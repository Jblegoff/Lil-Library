package com.lillibrary.jblg.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class BookResponse implements Parcelable {
    public static final Creator<BookResponse> CREATOR=new Creator<BookResponse>() {
        @Override
        public BookResponse createFromParcel(Parcel parcel) {
            return new BookResponse(parcel);
        }

        @Override
        public BookResponse[] newArray(int i) {
            return new BookResponse[i];
        }
    };

    @SerializedName("totalItems")
    int totalItems;
    @SerializedName("item")
    List<Result> items;

    public int getTotalItems() {
        return totalItems;
    }

    public List<Result> getItems() {
        return items;
    }

    private BookResponse(Parcel parcel) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {

    }
}
