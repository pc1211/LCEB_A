package com.example.pgyl.lceb_a;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

public class SolutionLinesListItemAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<String> solutionLines;

    public SolutionLinesListItemAdapter(Context context) {
        super();

        this.context = context;
        init();
    }

    private void init() {
        solutionLines = null;
    }

    public void close() {
        clearItems();
        solutionLines = null;
        context = null;
    }

    public void setItems(ArrayList<String> solutionLines) {
        this.solutionLines = solutionLines;
    }

    public void clearItems() {
        if (solutionLines != null) solutionLines.clear();
    }

    @Override
    public int getCount() {
        return (solutionLines != null) ? solutionLines.size() : 0;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(final int position, View rowView, ViewGroup parent) {   //  ViewHolder pattern
        SolutionLinesListItemViewHolder viewHolder;

        if (rowView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            rowView = inflater.inflate(R.layout.solutionlineslistitem, null);
            viewHolder = buildViewHolder(rowView);
            rowView.setTag(viewHolder);
        } else {
            viewHolder = (SolutionLinesListItemViewHolder) rowView.getTag();
        }
        //  setupViewHolder(viewHolder, position);
        paintView(viewHolder, position);
        return rowView;
    }

    public void paintView(SolutionLinesListItemViewHolder viewHolder, int position) {    //  Décoration proprement dite du getView
        viewHolder.tvSolutionLine.setText(solutionLines.get(position));
    }

    private SolutionLinesListItemViewHolder buildViewHolder(View rowView) {
        SolutionLinesListItemViewHolder viewHolder = new SolutionLinesListItemViewHolder();

        viewHolder.tvSolutionLine = rowView.findViewById(R.id.TV_SOLUTION_LINE);
        return viewHolder;
    }

    private void setupViewHolder(SolutionLinesListItemViewHolder viewHolder, int position) {
        //  RIEN
    }

}
