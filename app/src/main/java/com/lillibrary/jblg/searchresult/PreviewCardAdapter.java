package com.lillibrary.jblg.searchresult;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lillibrary.jblg.R;

import java.util.List;

public class PreviewCardAdapter extends RecyclerView.Adapter<PreviewCardAdapter.CardViewHolder> {

    /** Listens to user's interaction with the preview card item. */
    public interface CardItemListener {
        void onPreviewCardClicked(SearchedObject searchedObject);
    }

    private final List<SearchedObject> searchedObjectList;
    private final CardItemListener cardItemListener;

    public PreviewCardAdapter(
            List<SearchedObject> searchedObjectList, CardItemListener cardItemListener) {
        this.searchedObjectList = searchedObjectList;
        this.cardItemListener = cardItemListener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CardViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.products_preview_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        SearchedObject searchedObject = searchedObjectList.get(position);
        holder.bindProducts(searchedObject.getProductList());
        holder.itemView.setOnClickListener(v -> cardItemListener.onPreviewCardClicked(searchedObject));
    }

    @Override
    public int getItemCount() {
        return searchedObjectList.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final int imageSize;

        private CardViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.card_image);
            titleView = itemView.findViewById(R.id.card_title);
            subtitleView = itemView.findViewById(R.id.card_subtitle);
            imageSize = itemView.getResources().getDimensionPixelOffset(R.dimen.preview_card_image_size);
        }

        private void bindProducts(List<Product> products) {
            if (products.isEmpty()) {
                imageView.setVisibility(View.GONE);
                titleView.setText(R.string.static_image_card_no_result_title);
                subtitleView.setText(R.string.static_image_card_no_result_subtitle);
            } else {
                Product topProduct = products.get(0);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageDrawable(null);
                if (!TextUtils.isEmpty(topProduct.imageUrl)) {
                    new ImageDownloadTask(imageView, imageSize).execute(topProduct.imageUrl);
                } else {
                    imageView.setImageResource(R.drawable.logo_google_cloud);
                }
                titleView.setText(topProduct.title);
                subtitleView.setText(
                        itemView
                                .getResources()
                                .getString(R.string.static_image_preview_card_subtitle, products.size() - 1));
            }
        }
    }
}
