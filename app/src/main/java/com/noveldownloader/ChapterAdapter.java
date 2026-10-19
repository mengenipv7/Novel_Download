package com.noveldownloader;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ChapterAdapter extends BaseAdapter {

    private final List<ChapterInfo> items = new ArrayList<>();

    public void setItems(List<ChapterInfo> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void refresh() { notifyDataSetChanged(); }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chapter, parent, false);
        }
        ChapterInfo c = items.get(position);
        TextView tvIndex = v.findViewById(R.id.tvChapterIndex);
        TextView tvTitle = v.findViewById(R.id.tvChapterTitle);
        tvIndex.setText(String.valueOf(position + 1));
        tvTitle.setText(c.title);
        boolean downloaded = c.content != null && !c.content.isEmpty();
        tvTitle.setTextColor(downloaded ? Color.parseColor("#2E7D32") : Color.parseColor("#212121"));
        return v;
    }
}
