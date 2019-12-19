package com.jblg.lillibrary.adapterChapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jblg.lillibrary.R;
import com.jblg.lillibrary._interface.iRecyclerOnClick;
import com.jblg.lillibrary.activity.Read_Pager;
import com.jblg.lillibrary.common.Common;
import com.jblg.lillibrary.detail_comic.Chapter;

import java.util.ArrayList;
import java.util.List;

public class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.MyViewHolder> implements Filterable {
   private ArrayList<Chapter>list;
   private  ArrayList<Chapter>source;
   private Context context;

    public ChapterAdapter(  ArrayList<Chapter> source,Context context) {
        this.list = source;
        this.source = source;
        this.context =context;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        LayoutInflater inflater= LayoutInflater.from(context);
        View view =inflater.inflate(R.layout.single_row_chap,viewGroup,false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
        Log.d("LINKURL", String.valueOf(list.size()));
        if(list.get(i)!=null)
        {
        Chapter chapter= list.get(i);
        Log.d("LINKURL",chapter.getNum());
        myViewHolder.txt_num.setText(String.format("Chapter %s", chapter.getNum()));
        myViewHolder.txt_chap.setText(chapter.getName());
        }
        else
        {
            myViewHolder.txt_num.setText(" ERROR   ");
            myViewHolder.txt_chap.setText(" ERROR    ");
        }
        myViewHolder.setiRecyclerOnClick(new iRecyclerOnClick() {
            @Override
            public void onClick(View view, int position) {
                Common.selectedChapter= new Chapter(list.get(position));
                Common.selectedIndex = position;

                Common.sourceChapter= list;
                Toast.makeText(context,list.get(position).getName(),Toast.LENGTH_SHORT).show();
                Intent intent =new Intent(context, Read_Pager.class);
                context.startActivity(intent);
            }
        });


    }


    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public Filter getFilter() {
       return new Filter() {
           @Override
           protected FilterResults performFiltering(CharSequence constraint) {
               String searchString= constraint.toString();
               if(searchString.isEmpty()) list=source;
               else
               {
                   List<Chapter> result= new ArrayList<>();
                   for(Chapter item: source)
                   {
                       if(item.getNum().toLowerCase().contains(searchString.toLowerCase()))
                       {
                           result.add(item);
                       }
                   }
               }
               FilterResults filterResults= new FilterResults();
               filterResults.values=list;
               return filterResults;
           }

           @Override
           protected void publishResults(CharSequence constraint, FilterResults results) {
             list= (ArrayList<Chapter>) results.values;
             notifyDataSetChanged();
           }
       };
    }

    public class MyViewHolder extends  RecyclerView.ViewHolder implements View.OnClickListener
    {
        TextView txt_num;
        TextView txt_chap;
         iRecyclerOnClick iRecyclerOnClick;
        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            txt_num= (TextView) itemView.findViewById(R.id.txt_num);
            txt_chap =(TextView) itemView.findViewById(R.id.namechap);
            itemView.setOnClickListener(this);
        }
        public void setiRecyclerOnClick(iRecyclerOnClick iRecyclerOnClick) {
            this.iRecyclerOnClick = iRecyclerOnClick;
        }
        @Override
        public void onClick(View v) {
            iRecyclerOnClick.onClick(v,getAdapterPosition());
        }
    }
}
