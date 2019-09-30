package com.lillibrary.jblg.model;

import com.google.gson.annotations.SerializedName;

public class Result {
    @SerializedName("volumeInfo")
    private Book volumeInfo;

    public Book getBook(){
        return volumeInfo;
    }
}
