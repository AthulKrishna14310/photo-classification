package com.integrals.photoclassification;



import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity  {

    protected Interpreter tflite;
    private static final String TAG = "MAIN";
    private FaceDetector detector;
    private Bitmap editedBitmap;
    private  int imageSizeX;
    private  int imageSizeY;
    private static final float IMAGE_MEAN = 0.0f;
    private static final float IMAGE_STD = 1.0f;
    private int[] imageArray;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try{
            tflite=new Interpreter(loadmodelfile(this));
        }catch (Exception e) {
            e.printStackTrace();
        }
        imageArray = new int[]{R.drawable.face1, R.drawable.sample_8};

        detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();
        findViewById(R.id.btnProcessNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap image=decodeBitmapImage(imageArray[1]);
                Bitmap face=decodeBitmapImage(imageArray[0]);
                Toast.makeText(getApplicationContext(),"Face is contained in image = "+
                        isFacePresent(face,image)+"",Toast.LENGTH_SHORT).show();
            }
        });
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        detector.release();
    }


    private boolean isFacePresent(Bitmap faceBitmap , Bitmap imageBitmap){
        boolean result=false;

        float[][] embedding1=new float[1][128];
        float[][] embedding2=new float[1][128];

        embedding1=get_embaddings(faceBitmap);
        SparseArray<Face> faces=processImage(imageBitmap);
        if(faces!=null) {
            for (int index = 0; index < faces.size(); ++index) {

                Face face = faces.valueAt(index);
                int newWidth = (int) (face.getWidth());
                int newHeight = (int) (face.getHeight());

                Bitmap resizedBitmap = Bitmap.createBitmap(
                        imageBitmap,
                        (int) (face.getPosition().x),
                        (int) (face.getPosition().y),
                        newWidth,
                        newHeight);

                embedding2 = get_embaddings(resizedBitmap);
                if (isFacesEqual(embedding1, embedding2) == true) {
                    result = true;
                }

            }
        }else{
            Toast.makeText(getApplicationContext(),"Faces is null ",Toast.LENGTH_SHORT).show();
        }

        return result;
    }

    private MappedByteBuffer loadmodelfile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor=activity.getAssets().openFd("Qfacenet.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startoffset = fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startoffset,declaredLength);
    }

    public float[][] get_embaddings(Bitmap bitmap){
        TensorImage inputImageBuffer;
        float[][] embedding = new float[1][128];
        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape();
        imageSizeY = imageShape[1];
        imageSizeX = imageShape[2];
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        inputImageBuffer = new TensorImage(imageDataType);
        inputImageBuffer = loadImage(bitmap,inputImageBuffer);
        tflite.run(inputImageBuffer.getBuffer(),embedding);
        return embedding;
    }

    private TensorImage loadImage(final Bitmap bitmap, TensorImage inputImageBuffer ) {
        inputImageBuffer.load(bitmap);
        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(getPreprocessNormalizeOp())
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }

    private TensorOperator getPreprocessNormalizeOp() {
        return new NormalizeOp(IMAGE_MEAN, IMAGE_STD);
    }

    private boolean isFacesEqual(float[][] embedding1,float[][] embedding2){
        boolean result=false;
        double distance=calculate_distance(embedding1,embedding2);
        if(distance<6)
            result=true;
        else
            result=false;
        return result;
    }

    private double calculate_distance(float[][] ori_embedding, float[][] test_embedding) {
        double sum =0.0;
        for(int i=0;i<128;i++){
            sum=sum+Math.pow((ori_embedding[0][i]-test_embedding[0][i]),2.0);
        }
        return Math.sqrt(sum);
    }

    private SparseArray<Face> processImage(Bitmap bitmap) {
        if (detector.isOperational() && bitmap != null) {
            editedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap
                    .getHeight(), bitmap.getConfig());
            float scale = getResources().getDisplayMetrics().density;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GREEN);
            paint.setTextSize((int) (16 * scale));
            paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            Canvas canvas = new Canvas(editedBitmap);
            canvas.drawBitmap(bitmap, 0, 0, paint);
            Frame frame = new Frame.Builder().setBitmap(editedBitmap).build();
            SparseArray<Face> faces = detector.detect(frame);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    for (int index = 0; index < faces.size(); ++index) {
                        Face face = faces.valueAt(index);
                        canvas.drawRect(face.getPosition().x,
                                face.getPosition().y,
                                face.getPosition().x + face.getWidth(),
                                face.getPosition().y + face.getHeight(),
                                paint);
                    }
                }
            });

            if (faces.size() == 0) {
                return null;
            }

            return faces;
        }

        return null;

    }
    private Bitmap decodeBitmapImage(int image) {
        int targetW = 300;
        int targetH = 300;
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeResource(getResources(), image,
                bmOptions);

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return BitmapFactory.decodeResource(getResources(), image,
                bmOptions);
    }

}
