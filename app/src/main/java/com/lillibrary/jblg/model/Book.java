package com.lillibrary.jblg.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Book {

    @SerializedName("title")
    private String title;
    @SerializedName("authors")
    List<String> authors;

    public String getTitle() {
        return title;
    }

    public List<String> getAuthors() {
        return authors;
    }
}
