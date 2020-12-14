package com.integrals.photoclassification.Helper;


import java.util.ArrayList;
//
public class Person {
    public String Name="Name";
    public String faceUrl;
    public ArrayList<String> photoList=new ArrayList<>();

    public Person(String name, String faceUrl) {
        Name = name;
        this.faceUrl = faceUrl;
    }
    public void addPhoto(String url){
        photoList.add(url);
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getFaceUrl() {
        return faceUrl;
    }

    public void setFaceUrl(String faceUrl) {
        this.faceUrl = faceUrl;
    }

    public ArrayList<String> getPhotoList() {
        return photoList;
    }

    public void setPhotoList(ArrayList<String> photoList) {
        this.photoList = photoList;
    }
}
