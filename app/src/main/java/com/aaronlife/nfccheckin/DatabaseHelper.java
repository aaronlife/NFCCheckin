package com.aaronlife.nfccheckin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper
{
    public static class CheckinData
    {
        public String uid;
        public String col1;
        public String col2;
        public String datetime;
    }

    public static final String DATABASE_NAME = "NFCCheckinDB";
    public static final String TABLE_CHECKINS = "Checkins";

    public static final String COL_NAME_UID = "uid";
    public static final String COL_NAME_COL1 = "col1_data";
    public static final String COL_NAME_COL2 = "col2_data";
    public static final String COL_NAME_DATETIME = "datetime";

    private static final int DATABASE_VERSION = 1;

    private static DatabaseHelper mInstance = null;

    private DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version)
    {
        super(context, name, factory, version);
    }

    public synchronized static DatabaseHelper getInstance(Context context)
    {
        if (mInstance == null)
        {
            mInstance =
                    new DatabaseHelper(context.getApplicationContext(),
                            DATABASE_NAME, null,
                            DATABASE_VERSION);
        }

        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // 建立要存放資料的資料表格(相當於Excel的sheet)
        // 1. SQL語法不分大小寫
        // 2. 這裡大寫代表的是SQL標準語法, 小寫字是資料表/欄位的命名

        // 打卡記錄table
        db.execSQL("CREATE TABLE " +
                TABLE_CHECKINS +
                "(_id INTEGER PRIMARY KEY NOT NULL," +
                COL_NAME_UID + " TEXT NOT NULL," +
                COL_NAME_COL1 + " TEXT NOT NULL," +
                COL_NAME_COL2 + " TEXT NOT NULL," +
                COL_NAME_DATETIME + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {

    }

    public ArrayList<CheckinData> queryAllCheckinData()
    {
        ArrayList<CheckinData> checkinDatas = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        // 透過query來查詢資料
        Cursor c = db.query(TABLE_CHECKINS,           // 資料表名字
                new String[]{COL_NAME_UID, COL_NAME_COL1,
                             COL_NAME_COL2, COL_NAME_DATETIME}, //要取出的欄位資料
                null,                              // 查詢條件式
                null,                              // 查詢條件值字串陣列
                null,                              // Group By字串語法
                null,                              // Having字串法
                COL_NAME_DATETIME + " desc",       // Order By字串語法(排序)
                null);                             // Limit字串語法

        while(c.moveToNext())
        {
            CheckinData cd = new CheckinData();
            cd.uid = c.getString(c.getColumnIndex(COL_NAME_UID));
            cd.col1 = c.getString(c.getColumnIndex(COL_NAME_COL1));
            cd.col2 = c.getString(c.getColumnIndex(COL_NAME_COL2));
            cd.datetime = c.getString(c.getColumnIndex(COL_NAME_DATETIME));
            checkinDatas.add(cd);
        }

        // 釋放資源
        c.close();
        db.close();
        
        return checkinDatas;
    }

    public void insertCheckinData(CheckinData checkinData)
    {
        // 新增到資料庫
        SQLiteDatabase db = getWritableDatabase();

        // 定義要新增的資料
        ContentValues values = new ContentValues();
        values.put(COL_NAME_UID, checkinData.uid);
        values.put(COL_NAME_COL1, checkinData.col1);
        values.put(COL_NAME_COL2, checkinData.col2);
        values.put(COL_NAME_DATETIME, checkinData.datetime);

        // 新增一筆資料到資料表(Table)
        db.insert(TABLE_CHECKINS, null, values);

        // 釋放SQLiteDatabase資源
        db.close();
    }

    public void clearCheckinData()
    {
        SQLiteDatabase db = getReadableDatabase();
        db.delete(TABLE_CHECKINS, null, null);
        db.close();
    }
}
