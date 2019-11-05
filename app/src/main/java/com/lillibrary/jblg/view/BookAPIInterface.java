package com.lillibrary.jblg.view;

import com.lillibrary.jblg.model.BookResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface BookAPIInterface {
    @GET("/books/v1/volumes?q=isbn:{ISBN}&key=AIzaSyAmoFXGEouYNo3UySwDPrqxEH1GWUwzmAE")
    Call<BookResponse> getBookByISBN(@Path("ISBN") int ISBN);

}
