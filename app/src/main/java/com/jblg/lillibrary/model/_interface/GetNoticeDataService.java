package com.jblg.lillibrary.model._interface;


import com.jblg.lillibrary.model.detail_comic.Detail_Comic;
import com.jblg.lillibrary.model.detail_comic.detail_chap;
import com.jblg.lillibrary.model.manga.MangaList;

import io.reactivex.Observable;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface GetNoticeDataService {
    @GET("list/0/")
    Observable<MangaList>getNoticeData();
    //Call<MangaList>
    @GET("manga/{comicid}/")
    Observable<Detail_Comic> getDetailComic(@Path("comicid")String comicid);
    @GET("manga/{comicid}/")
    Call<Detail_Comic> getComic(@Path("comicid")String comicid);
    @GET("chapter/{chapterid}/")
    Call<detail_chap> getChap(@Path("chapterid")String chapterid);

}
