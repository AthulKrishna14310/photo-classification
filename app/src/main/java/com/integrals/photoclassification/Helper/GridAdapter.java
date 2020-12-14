package com.integrals.photoclassification.Helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.integrals.photoclassification.R;

import java.io.File;
import java.util.ArrayList;

//toDO Junaid TK
public class GridAdapter extends BaseAdapter {

    private  ArrayList<String> arrayList;
    private ArrayList<String>  descs;
    Context context;

    public GridAdapter(ArrayList<String> arrayList,ArrayList<String> desc, Context context) {
        this.arrayList = arrayList;
        this.context = context;
        this.descs=desc;
    }

    @Override
    public int getCount() {
        return arrayList.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        LayoutInflater layoutInflater=(LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view=layoutInflater.inflate(R.layout.singleframe,null);
        ImageView img=view.findViewById(R.id.iconimage);
        TextView tv=view.findViewById(R.id.textdata);
        tv.setText(descs.get(position));
        Bitmap b=getBitmap(arrayList.get(position));
        img.setImageBitmap(b);
        return view;

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
}
//toDo JUNAID TK