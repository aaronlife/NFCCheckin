package com.aaronlife.nfccheckin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;


public class AdapterListCheckin extends BaseAdapter
{
    private LayoutInflater ll;
    private DatabaseHelper databaseHelper;
    private ArrayList<DatabaseHelper.CheckinData> checkinDatas;
    private String filter;

    public AdapterListCheckin(Context context, String filter)
    {
        databaseHelper = DatabaseHelper.getInstance(context);
        checkinDatas = databaseHelper.queryAllCheckinData();
        ll = LayoutInflater.from(context);
        this.filter = filter;
    }

    @Override
    public int getCount()
    {
        return checkinDatas.size();
    }

    @Override
    public DatabaseHelper.CheckinData getItem(int position)
    {
        return checkinDatas.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        convertView = ll.inflate(R.layout.listview_checkin, parent, false);

        TextView txtRow1 = (TextView)convertView.findViewById(R.id.checkin_r1);
        TextView txtRow2 = (TextView)convertView.findViewById(R.id.checkin_r2);

        DatabaseHelper.CheckinData checkinData = getItem(position);

        if(filter != null &&
           !checkinData.col1.contains(filter) &&
           !checkinData.col2.contains(filter) &&
           !checkinData.datetime.contains(filter))
            return null;

        txtRow1.setText(checkinData.col1 + " - " + checkinData.col2);
        txtRow2.setText("打卡時間：" + checkinData.datetime);

        return convertView;
    }
}
