package com.aaronlife.nfccheckin;

import android.content.Context;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class NFCTagManager
{
    public static class NFCData
    {
        String col1 = "";
        String col2 = "";
        String col3 = "";
    }

    private static NFCTagManager instance;

    private NFCTagManager()
    {

    }

    public synchronized static NFCTagManager getInstance()
    {
        if(instance == null)
        {
            instance = new NFCTagManager();
        }

        return instance;
    }

    public boolean isNFCSupported(Context context)
    {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);

        if(nfcAdapter == null)
        {
            Log.d("aarontest", "這個裝置沒有支援NFC。");

            return false;
        }

        return true;
    }

    public boolean isNFCEnabled(NfcAdapter nfcAdapter)
    {
        if(nfcAdapter == null)
        {
            Log.d("aarontest", "這個裝置沒有支援NFC。");

            return false;
        }

        if(nfcAdapter.isEnabled())
        {
            Log.d("aarontest", "NFC功能為開啟。");
            return true;
        }
        else
            Log.d("aarontest", "NFC功能為關閉。");

        return true;
    }

    public boolean isNewTag(Parcelable[] rawMessages)
    {
        if(rawMessages != null)
        {
            NdefMessage msg = (NdefMessage)rawMessages[0];

            NdefRecord[] records = msg.getRecords();

            if(records.length == 2)
            {
                if(readTagData(rawMessages) == null) return true;

                for(NdefRecord r : records)
                {
                    if(r.getTnf() != NdefRecord.TNF_WELL_KNOWN &&
                       r.getTnf() != NdefRecord.TNF_EXTERNAL_TYPE)
                        return true;

                    if(r.getPayload() == null) return true;

                    if(r.getTnf() == NdefRecord.TNF_WELL_KNOWN)
                    {
                        byte[] payload = r.getPayload();

                        if((payload[0] & 0x80) != 0) return true;

                        String text = null;
                        try
                        {
                            text = new String(payload, 1,
                                    payload.length - 1,
                                    "UTF-8");
                        }
                        catch(UnsupportedEncodingException e)
                        {
                            e.printStackTrace();
                        }

                        String[] colData = text.split("::");

                        if(colData.length != 3) return true;
                    }
                }

                return false;
            }
        }

        return true;
    }

    public boolean writeData(Tag tag, String col1, String col2, String col3)
    {
        // 使用者資料Record
        String text = col1 + "::" + col2 + "::" + col3;

        String lang = "cht";
        byte[] textBytes = text.getBytes();
        byte[] langBytes = new byte[0];
        try
        {
            langBytes = lang.getBytes("UTF-8");
        }
        catch(UnsupportedEncodingException e)
        {
            Log.d("aarontest", "ERROR: " + e.getMessage());
        }

        // 乘載資料
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[1 + langLength + textLength];

        // 設定語言碼的長度
        payload[0] = (byte)langLength;

        // 將語言碼長度和語言碼字串放入payload中
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        NdefRecord dataRecord =
                new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                        NdefRecord.RTD_TEXT,
                        new byte[0],
                        payload);

        // AAR
        NdefRecord aarRecord = new NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE,
                "android.com:pkg".getBytes(),
                new byte[0],
                "com.aaronlife.nfccheckin".getBytes());

        // 設定所有的record, AAR放在後面，否則會收不到NDEF訊息
        NdefRecord[] records = {dataRecord, aarRecord};
        NdefMessage message = new NdefMessage(records);

        Ndef ndef = Ndef.get(tag);

        // 尚未格式化
        if(ndef == null)
        {
            NdefFormatable formatable = NdefFormatable.get(tag);

            if(formatable != null)
            {
                Log.d("aarontest", "可格式化成NDEF的標籤");

                try
                {
                    formatable.connect();
                    formatable.format(message);

                    return true;
                }
                catch(IOException e)
                {
                    Log.d("aarontest", "connect fialed: " + e.getMessage());
                }
                catch(FormatException e)
                {
                    Log.d("aarontest", "format fialed: " + e.getMessage());
                }
            }

            return false;
        }

        Log.d("aarontest", "NFC空間: " + ndef.getMaxSize() +
                        ", Message size: " + message.toByteArray().length);

        try
        {
            ndef.connect();
            ndef.writeNdefMessage(message);

            Log.d("aarontest", "資料寫入NFC標籤成功");
            return true;
        }
        catch(IOException | FormatException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                ndef.close();
            }
            catch(IOException e)
            {
                e.printStackTrace();
            }
        }

        return false;
    }

    public NFCData readTagData(Parcelable[] rawMessages)
    {
        if(rawMessages == null || rawMessages.length == 0) return null;

        NdefMessage message = (NdefMessage)rawMessages[0];
        NdefRecord[] records = message.getRecords();

        for(NdefRecord r : records)
        {
            // 當NDEF record被寫為TNF_EMPTY後，NdefMessage物件會有Record在，
            // 但是讀取payload會是null
            if(r.getTnf() == NdefRecord.TNF_WELL_KNOWN &&
               r.getPayload() != null)
            {
                String text = "";
                byte[] payload = r.getPayload();

                // 位元8為Status byte
                String textEncoding =
                            ((payload[0] & 0x80) == 0) ? "UTF-8" : "UTF-16";

                // 位元0~5為Language Code長度
                int languageCodeLength = payload[0] & 0x3F;

                // 取得ISO/IANA定義的Language Code，編碼為US-ASCII，但這裡沒用到，
                // 所以註解起來
                // String languageCode =
                //     new String(payload, 1, languageCodeLength, "US-ASCII");

                try
                {
                    text = new String(payload, languageCodeLength + 1,
                                      payload.length - languageCodeLength - 1,
                                      textEncoding);
                }
                catch(UnsupportedEncodingException e)
                {
                    Log.e("UnsupportedEncoding", e.toString());
                }

                Log.d("aarontest", "NFC Content: " + text);

                String[] colData = text.split("::");

                NFCData nfcData = new NFCData();

                if(colData.length > 0) nfcData.col1 = colData[0];
                if(colData.length > 1) nfcData.col2 = colData[1];
                if(colData.length > 2) nfcData.col3 = colData[2];

                return nfcData;
            }
        }

        return null;
    }

    public boolean clearTagData(Tag tag)
    {
        Ndef ndefTag = Ndef.get(tag);

        try
        {
            NdefRecord record = new NdefRecord(
                                    NdefRecord.TNF_EMPTY, null, null, null);

            // 因為這行，所以需要API 16，否則API 14就夠了
            NdefMessage message = new NdefMessage(record);
            ndefTag.connect();
            ndefTag.writeNdefMessage(message);
            ndefTag.close();

            Log.d("aarontest", "Delete tag data successful.");

            return true;
        }
        catch(IOException | FormatException e)
        {
            Log.d("aarontest", "clearTagData ERROR: " + e.getMessage());
        }

        return false;
    }
}
