package com.aaronlife.nfccheckin;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

//
// 主畫面：顯示標題畫面，卡片嗶下去的後，顯示該卡片基本資料，以及過去１０筆記錄。
// 選單：編輯使用者、匯出資料庫、匯入資料庫、檢視資料庫
// 匯出CSV：到Download目錄
// 匯入CSV：從Download目錄
//
// 備註：
//   1. NEDF標籤存放使用者基本資料（自訂５個欄位）
//   2. 檢視資料庫時可以用文字來過濾
//   3. 使用SQLite存打卡記錄
//   4. 使用SQLite存卡片資料（UID）

// 指定該Activity為應用程式中的最上層Activity
//
public class MainActivity extends AppCompatActivity
{
    private ListView listCheckin;
    private EditText searchText;

    private DatabaseHelper databaseHelper;
    private NFCTagManager nfcTagManager;

    private NfcAdapter nfcAdapter;
    private PendingIntent nfcPendingIntent;
    private IntentFilter[] intentFilters;

    private boolean isProcessed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setTitle("NFC打卡紀錄");

        listCheckin = (ListView)findViewById(R.id.checkin_list);
        listCheckin.setAdapter(new AdapterListCheckin(this, null));

        searchText = (EditText)findViewById(R.id.filter_text);
        searchText.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after)
            {}

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count)
            {
                String filter = null;

                if(searchText.length() > 0)
                    filter = searchText.getText().toString();

                listCheckin.setAdapter(
                        new AdapterListCheckin(MainActivity.this, filter));
            }

            @Override
            public void afterTextChanged(Editable s)
            {
            }
        });

        databaseHelper = DatabaseHelper.getInstance(this);
        nfcTagManager = NFCTagManager.getInstance();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        nfcPendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, getClass())
                                 .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        IntentFilter ndefFilter =
                        new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter techFilter =
                        new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);

        try
        {
            ndefFilter.addDataType("text/plain");
        }
        catch (IntentFilter.MalformedMimeTypeException e) { }

        intentFilters = new IntentFilter[] { ndefFilter, techFilter };
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        // 處理NFC標籤
        processIntent(intent);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        nfcAdapter.enableForegroundDispatch(this,
                                            nfcPendingIntent,
                                            intentFilters,
                                            null);

        if(!isProcessed) processIntent(getIntent());
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        NfcAdapter.getDefaultAdapter(this).disableForegroundDispatch(this);

        // android.view.windowLeaded預防
        if(funcDlg != null) funcDlg.dismiss();
    }

    protected  void processIntent(Intent intent)
    {
        // 偵測NFC功能有沒有打開
        if(!nfcAdapter.isEnabled())
        {
            if(!nfcTagManager.isNFCEnabled(nfcAdapter));
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("NFC未開啟");
                builder.setMessage("需開啟NFC功能才能打卡");
                builder.setNegativeButton("關閉", null);
                builder.setPositiveButton("設定",
                                        new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        Intent it = new Intent(Settings.ACTION_NFC_SETTINGS);
                        startActivity(it);
                    }
                });

                funcDlg = builder.show();
            }

            return;
        }

        isProcessed = true;

        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        Parcelable[] rawMessages =
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

        if(intent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED))
        {
            showInputDialog(tag, null);
        }
        else if(intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED))
        {
            if(nfcTagManager.isNewTag(rawMessages))
            {
                Log.d("aarontest", "New Tag");

                showInputDialog(tag, null);
            }
            else
            {
                Log.d("aarontest", "Data existing.");

                showActionDialog(tag, rawMessages);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if(id == R.id.clear_checkin_data)
        {
            AlertDialog.Builder dlg = new AlertDialog.Builder(this);

            dlg.setTitle("清空打卡記錄");
            dlg.setMessage("確定要清空打卡記錄嗎？");
            dlg.setNegativeButton("取消", null);
            dlg.setPositiveButton("確定", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // 再問一次
                    AlertDialog.Builder dlg2 =
                            new AlertDialog.Builder(MainActivity.this);

                    dlg2.setTitle("清空打卡記錄再確認");
                    dlg2.setMessage("打卡記錄清空後無法復原，確定要清空嗎？");
                    dlg2.setNegativeButton("取消", null);
                    dlg2.setPositiveButton("確定",
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dlg, int which)
                            {
                                // 再問一次
                                databaseHelper.clearCheckinData();
                                Toast.makeText(MainActivity.this, "打卡記錄已清空",
                                        Toast.LENGTH_LONG).show();

                                listCheckin.setAdapter(null);
                            }
                        }).show();
                }
            }).show();
        }
        else if(id == R.id.export_checkins)
        {
            exportCheckinData();
        }

        return super.onOptionsItemSelected(item);
    }

    AlertDialog funcDlg;

    protected void showInputDialog(final Tag tag, NFCTagManager.NFCData nfcData)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        final View dlgView = inflater.inflate(R.layout.dialog_add_tag, null);

        final EditText eCol1 = (EditText)dlgView.findViewById(R.id.edit_col1);
        final EditText eCol2 = (EditText)dlgView.findViewById(R.id.edit_col2);
        final EditText eCol3 = (EditText)dlgView.findViewById(R.id.edit_col3);

        if(nfcData != null)
        {
            eCol1.setText(nfcData.col1);
            eCol2.setText(nfcData.col2);
            eCol3.setText(nfcData.col3);
        }

        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("確定", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                final String col1 = eCol1.getText().toString();
                if(col1.length() == 0)
                {
                    Toast.makeText(MainActivity.this,
                                   "更新卡片資料失敗，資料欄位１不得為空",
                                   Toast.LENGTH_LONG).show();

                    return;
                }

                AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity.this);

                dlg.setTitle("更新卡片資料");
                dlg.setMessage("確定要更新卡片資料嗎（卡片上原有資料將會被覆蓋）？");
                dlg.setNegativeButton("取消", null);
                dlg.setPositiveButton("確定", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        String col2 = eCol2.getText().toString();
                        String col3 = eCol3.getText().toString();

                        if(nfcTagManager.writeData(tag, col1, col2, col3))
                            Toast.makeText(MainActivity.this, "更新卡片資料完成",
                                    Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(MainActivity.this, "更新卡片資料失敗",
                                    Toast.LENGTH_LONG).show();
                    }
                }).show();
            }
        });

        builder.setView(dlgView);
        funcDlg = builder.show();
    }

    protected void showActionDialog(final Tag tag, Parcelable[] rawMsg)
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dlgView = inflater.inflate(R.layout.dialog_action, null);

        final NFCTagManager.NFCData nfcData = nfcTagManager.readTagData(rawMsg);

        TextView text1 = (TextView)dlgView.findViewById(R.id.action_text1);
        TextView text2 = (TextView)dlgView.findViewById(R.id.action_text2);

        if(nfcData != null)
        {
            text1.setText(nfcData.col1 + " - " + nfcData.col2);
            text2.setText(nfcData.col3);
        }
        else
        {
            text1.setText("卡片無資料");
            text2.setText("請除新建立資料");
        }

        builder.setNegativeButton("關閉", null);
        builder.setView(dlgView);
        funcDlg = builder.show();

        // 打卡
        dlgView.findViewById(R.id.action_checkin)
            .setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    //
                    DatabaseHelper.CheckinData checkinData =
                                            new DatabaseHelper.CheckinData();
                    checkinData.uid = Tools.bytesToHexString(tag.getId());
                    checkinData.col1 = nfcData.col1;
                    checkinData.col2 = nfcData.col2;
                    checkinData.datetime =
                            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
                                    .format(new Date());
                    databaseHelper.insertCheckinData(checkinData);
                    funcDlg.dismiss();

                    Toast.makeText(MainActivity.this, "打卡完成",
                            Toast.LENGTH_LONG).show();

                    String filter = null;
                    if(searchText.getText().toString().length() > 0)
                    {
                        filter = searchText.getText().toString();
                    }

                    listCheckin.setAdapter(
                            new AdapterListCheckin(MainActivity.this, filter));
                }
            });

        // 編輯卡片資料
        dlgView.findViewById(R.id.action_edit)
            .setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    funcDlg.dismiss(); // 1111
                    showInputDialog(tag, nfcData);
                }
            });

        // 清除卡片資料
        dlgView.findViewById(R.id.action_clear)
            .setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if(nfcTagManager.clearTagData(tag))
                        Toast.makeText(MainActivity.this, "清除卡片資料完成",
                                Toast.LENGTH_LONG).show();
                    else
                        Toast.makeText(MainActivity.this, "清除卡片資料失敗",
                                Toast.LENGTH_LONG).show();

                    funcDlg.dismiss();
                }
            });
    }

    public static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSIONS = 1;

    // 匯出為CSV
    private boolean exportCheckinData()
    {
        // 要求權限
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            Log.d("aarontest", "未取得裝置寫入權限");

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSIONS);
        }

        // 確認目錄存在
        String path = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
        new File(path).mkdirs();

        // 檔名
        SimpleDateFormat sdf2 =
                new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.ENGLISH);
        String datetime = sdf2.format(Calendar.getInstance().getTime());

        File file = new File(path + "/NFCCheckin_" + datetime + ".csv");

        Log.d("aarontest", file.getAbsolutePath());
        try
        {
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);

            // 寫入標題
            bw.write("資料欄位１,");
            bw.write("資料欄位２,");
            bw.write("打卡時間");

            bw.newLine(); // 換行
            bw.flush(); // 將緩衝區資料寫入檔案


            // 寫入內容
            ArrayList<DatabaseHelper.CheckinData> checkinDatas =
                    databaseHelper.queryAllCheckinData();

            for(DatabaseHelper.CheckinData cd : checkinDatas)
            {
                bw.write(cd.col1 + ",");
                bw.write(cd.col2 + ",");
                bw.write(cd.datetime);

                bw.newLine(); // 換行
                bw.flush(); // 將緩衝區資料寫入檔案
            }

            bw.close();
            fw.close();

            AlertDialog.Builder dlg = new AlertDialog.Builder(this);
            dlg.setTitle("匯出完成");
            dlg.setMessage("匯出打卡資料成功（CSV格式），" +
                           "資料存放在「文件」（Documents）目錄。\n\n" +
                           "檔名：\nNFCCheckin_" + datetime + ".csv");
            dlg.setNegativeButton("關閉", null);
            dlg.show();
        }
        catch(IOException e)
        {
            AlertDialog.Builder dlg = new AlertDialog.Builder(this);
            dlg.setTitle("匯出失敗");
            dlg.setMessage("錯誤訊息：" + e.getMessage());
            dlg.setNegativeButton("關閉", null);
            dlg.show();
        }

        return true;
    }
}
