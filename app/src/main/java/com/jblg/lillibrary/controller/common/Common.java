package com.jblg.lillibrary.controller.common;


import com.jblg.lillibrary.RetrofitInstance;
import com.jblg.lillibrary.model._interface.GetNoticeDataService;
import com.jblg.lillibrary.model.detail_comic.Chapter;
import com.jblg.lillibrary.model.detail_comic.Detail_Comic;
import com.jblg.lillibrary.model.manga.Manga;

import java.util.ArrayList;
import java.util.List;

public class Common {
    public static Manga selectedManga;
    public  static Detail_Comic selectedComic;
    public static Chapter selectedChapter;
    public static int selectedIndex;
    public static List<Chapter> sourceChapter =new ArrayList<>();
    public static GetNoticeDataService getAPI()
    {

      return  RetrofitInstance.getRetrofitInstance().create(GetNoticeDataService.class);
    }

}
