package com.integrals.photoclassification;
import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.material.button.MaterialButton;
import com.integrals.photoclassification.Helper.GridAdapter;
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
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import lal.adhish.gifprogressbar.GifView;

public class MainActivity extends AppCompatActivity {

    protected Interpreter tflite;
    private static final String TAG = "MAIN";
    private FaceDetector detector;
    private Bitmap editedBitmap;
    private int imageSizeX;
    private int imageSizeY;
    private static final float IMAGE_MEAN = 0.0f;
    private static final float IMAGE_STD = 1.0f;
    private int[] imageArray;
    private ArrayList<String> totalFaces = new ArrayList<>();
    private ArrayList<String> totalPhotos = new ArrayList<>();
    private ArrayList<Uri> uris = new ArrayList<>();

    private int faceCount = 0;
    private Handler handler;
    private TextView textView;
    private GifView pGif;
    private ImageView imageViewFace;
    private ImageView imageViewImage;
    private TextView result;
    private ArrayList<String> descs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        pGif = (GifView) findViewById(R.id.progressBar);
        pGif.setImageResource(R.drawable.loading_);
        pGif.setVisibility(View.INVISIBLE);
        try {
            tflite = new Interpreter(loadmodelfile(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
        imageViewFace=findViewById(R.id.facebt);
        imageViewImage=findViewById(R.id.imagebt);
        result=findViewById(R.id.result);
        detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        textView=findViewById(R.id.progressText);
        descs=new ArrayList<>();

        findViewById(R.id.btnProcessNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if (ActivityCompat.checkSelfPermission( MainActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        !=PackageManager.PERMISSION_GRANTED)  {

                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{
                                    Manifest.permission.READ_EXTERNAL_STORAGE},
                            100
                    );
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setType("image/*");
                startActivityForResult(intent, 1);

            }

        });

        findViewById(R.id.showfaces).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                imageViewFace.setVisibility(View.INVISIBLE);
                imageViewImage.setVisibility(View.INVISIBLE);
                result.setVisibility(View.INVISIBLE);
                textView.setVisibility(View.INVISIBLE);
                findViewById(R.id.btnProcessNext).setVisibility(View.INVISIBLE);
                GridView grid=findViewById(R.id.datagrid);
                grid.setVisibility(View.VISIBLE);
                grid.setNumColumns(1);
                grid.setAdapter(new GridAdapter(totalFaces,descs,getApplicationContext()));
            }
        });

        //toDo Junaid TK
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detector.release();
    }


    private void startWork() {

        Runnable runnable1 = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.btnProcessNext).setVisibility(View.INVISIBLE);
                        findViewById(R.id.showfaces).setVisibility(View.INVISIBLE);
                        findViewById(R.id.imagebt).setVisibility(View.VISIBLE);
                        pGif.setVisibility(View.VISIBLE);
                        textView.setText("Decoding bitmaps...");
                    }
                });
                for (int i = 0; i < uris.size(); i++) {
                    Uri imageUri = uris.get(i);
                    try {
                        InputStream is = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        totalPhotos.add(storeImage(bitmap));
                        addFacesBitmap(bitmap);
                        int finalI = i;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText("Decoded and added faces from " + finalI);
                            }
                        });
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("Filtering faces , please wait..." + totalFaces.size());
                    }
                });
                filterFaces();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("Filter completed." + totalFaces.size());
                    }
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                for (int i = 0; i < totalFaces.size(); i++) {
                    Bitmap face = getBitmap(totalFaces.get(i));
                    // For changing ui
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageViewFace.setImageBitmap(face);
                        }
                    });
                    int finalI = i;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText("Using face " + finalI);
                            findViewById(R.id.showfaces).setVisibility(View.VISIBLE);
                        }
                    });
                    storeImageResult(face, i + " FACE");
                    for (int j = 0; j < totalPhotos.size(); j++) {
                        Bitmap image = getBitmap(totalPhotos.get(j));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageViewImage.setImageBitmap(image);
                            }
                        });
                        int finalJ = j;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText("Checking with photo  " + finalJ);
                            }
                        });
                        if (isFacePresent(face, image)) {
                            storeImageResult(image, i + " FACE");
                        } else {

                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText("Task done");
                            findViewById(R.id.showfaces).setVisibility(View.VISIBLE);
                            pGif.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            }
        };
        new Thread(runnable1).start();
     }



    private MappedByteBuffer loadmodelfile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("Qfacenet.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startoffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset, declaredLength);
    }

    private TensorImage loadImage(final Bitmap bitmap, TensorImage inputImageBuffer) {
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

    private void addFacesBitmap(Bitmap imageBitmap) {
        SparseArray<Face> faces = getFacesInBitmap(imageBitmap);
        if (faces != null) {
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

                descs.add("Smiling = "+face.getIsSmilingProbability()+"\n"+"Left Eye Open Probablity ="+
                        face.getIsLeftEyeOpenProbability()+"\nRight eye open Probablity ="+
                        face.getIsRightEyeOpenProbability()+" Rotation : "+
                        (int)face.getEulerX()

                );

                totalFaces.add(storeImageResult(resizedBitmap, "PROCESS"));
            }
        }
        else { }
    }


    public float[][] get_embaddings(Bitmap bitmap) {
        TensorImage inputImageBuffer;
        float[][] embedding = new float[1][128];
        int imageTensorIndex = 0;
        int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape();
        imageSizeY = imageShape[1];
        imageSizeX = imageShape[2];
        DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();
        inputImageBuffer = new TensorImage(imageDataType);
        inputImageBuffer = loadImage(bitmap, inputImageBuffer);
        tflite.run(inputImageBuffer.getBuffer(), embedding);
        return embedding;
    }


    private boolean isFacesEqual(float[][] embedding1, float[][] embedding2) {
        boolean result = false;
        double distance = calculate_distance(embedding1, embedding2);
        if (distance < 4)
            result = true;
        else
            result = false;
        return result;
    }


    private double calculate_distance(float[][] ori_embedding, float[][] test_embedding) {
        double sum = 0.0;
        for (int i = 0; i < 128; i++) {
            sum = sum + Math.pow((ori_embedding[0][i] - test_embedding[0][i]), 2.0);
        }
        return Math.sqrt(sum);
    }

    private SparseArray<Face> getFacesInBitmap(Bitmap bitmap) {
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
            for (int index = 0; index < faces.size(); ++index) {
                Face face = faces.valueAt(index);
                canvas.drawRect(face.getPosition().x,
                        face.getPosition().y,
                        face.getPosition().x + face.getWidth(),
                        face.getPosition().y + face.getHeight(),
                        paint);
            }


            if (faces.size() == 0) {
                return null;
            }

            return faces;
        }

        return null;

    }

    private String storeImage(Bitmap bitmapImage) {
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File myPath = new File(directory, System.currentTimeMillis() + "__.jpeg");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(myPath);
            bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return myPath.getAbsolutePath();

    }

    private void filterFaces(){
        try {
            ArrayList<String> tempFaces;
            tempFaces=totalFaces;
            for(int i=0;i<tempFaces.size();i++){
                Bitmap index=getBitmap(tempFaces.get(i));
                float [][] embeddingIndex=get_embaddings(index);
                for(int j=0;j<tempFaces.size();j++){
                    Bitmap data=getBitmap(tempFaces.get(j));
                    float [][] embeddingFaces=get_embaddings(data);
                    if(isFacesEqual(embeddingIndex,embeddingFaces)){
                        totalFaces.remove(i);
                    }
                }
            }
        }catch (IndexOutOfBoundsException e){
            e.printStackTrace();
        }

    }
    private String storeImageResult(Bitmap bitmapImage, String direct) {

        File directory = getExternalFilesDir(direct);
        File myPath = new File(directory, System.currentTimeMillis() + "result__.jpeg");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(myPath);
            bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return myPath.getAbsolutePath();

    }

    private boolean isFacePresent(Bitmap face, Bitmap image) {
        boolean result = false;
        float[][] embedding1 = new float[1][128];
        float[][] embedding2 = new float[1][128];
        embedding1 = get_embaddings(face);
        SparseArray<Face> faces = getFacesInBitmap(image);
        if(faces!=null) {
            for (int index = 0; index < faces.size(); ++index) {
                Face faceData = faces.valueAt(index);
                int newWidth = (int) (faceData.getWidth());
                int newHeight = (int) faceData.getHeight();

                Bitmap resizedBitmap = Bitmap.createBitmap(
                        image,
                        (int) (faceData.getPosition().x),
                        (int) (faceData.getPosition().y),
                        newWidth,
                        newHeight);

                embedding2 = get_embaddings(resizedBitmap);

                if (isFacesEqual(embedding1, embedding2) == true) {
                    return true;
                }

            }
        }else{
            return  false;
        }
        return result;
    }


    public Bitmap getBitmap(String path) {
        Bitmap bitmap = null;
        try {
            File f = new File(path);
            bitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }



    //todo Shamir Faraz
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                uris.add(clipData.getItemAt(i).getUri());
            }
            startWork();
        }
    }
    private String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (Exception e) {
            Log.e(TAG, "getRealPathFromURI Exception : " + e.toString());
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    //todo Shamir Faraz
}
