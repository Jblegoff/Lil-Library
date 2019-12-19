package com.jblg.lillibrary.fragment;

import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.jblg.lillibrary.R;
import com.jblg.lillibrary.adapterChapter.ChapterAdapter;
import com.jblg.lillibrary.common.Common;
import com.jblg.lillibrary.detail_comic.Chapter;
import com.jblg.lillibrary.swipeRecycler.SwipeController;
import com.jblg.lillibrary.swipeRecycler.SwipeControllerActions;

import java.util.ArrayList;
import java.util.List;

public class Chapter_Fragment extends Fragment {
    private ChapterAdapter adapter;
    private FloatingActionButton increase;
    private FloatingActionButton decrease;

    private SwipeController swipeController = new SwipeController(new SwipeControllerActions() {
        @Override
        public void onRightClicked(int position) {
            Toast.makeText(getContext(), list.get(position).getName(), Toast.LENGTH_SHORT).show();
            super.onRightClicked(position);
        }
    });
    private ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeController);
    private List<Chapter> list = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.detail_chapter, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_chapter);
        adapter = new ChapterAdapter((ArrayList<Chapter>) list, getContext());
        final LinearLayoutManager layoutManager=  new LinearLayoutManager(getActivity());
        layoutManager.setReverseLayout(false);
        layoutManager.setStackFromEnd(true);
        FloatingActionMenu actionMenu = view.findViewById(R.id.fab_menu);
        increase= view.findViewById(R.id.fab_increase);
        decrease = view.findViewById(R.id.fab_decrease);
        actionMenu.setClosedOnTouchOutside(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                swipeController.onDraw(c, getResources());
                increase.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        layoutManager.setReverseLayout(false);
                        layoutManager.scrollToPositionWithOffset(0, 0);
                    }
                });
                decrease.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        layoutManager.setReverseLayout(true);
                        layoutManager.scrollToPositionWithOffset(list.size()-1, 0);
                    }
                });
            }
        });
        setHasOptionsMenu(true);
        return view;
    }






    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_main, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                adapter.getFilter().filter(s);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adapter.getFilter().filter(s);
                return false;
            }
        });

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<List<String>> data = Common.selectedComic.getListchap();
        for (int i = data.size() - 1; i >= 0; i--) {
            list.add(new Chapter(data.get(i)));
            Log.d("MARKURL", data.get(i).get(0));
        }
//

    }
}
