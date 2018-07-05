package com.tzutalin.dlibtest;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.epam.goldeneye.R;

import java.util.ArrayList;
import java.util.List;

public class DetectedAdapter extends RecyclerView.Adapter<DetectedAdapter.DetectedViewHolder> {

    private List<DetectedItem> items = new ArrayList<>();

    @NonNull
    @Override
    public DetectedViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new DetectedViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.detected_list_item,parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final DetectedViewHolder holder, final int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setItems(List<DetectedItem> pItems) {
        items.clear();
        items.addAll(pItems);
        notifyDataSetChanged();
    }

    class DetectedViewHolder extends RecyclerView.ViewHolder {

        private final ImageView image;
        private final TextView title;

        DetectedViewHolder(final View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.img);
            title = itemView.findViewById(R.id.title);
        }

        void bind(DetectedItem item) {
            image.setImageDrawable(item.image);
            title.setText(item.title);
        }
    }
}
