package com.yamanobora.offlinerecorder;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class MeetingStorage {

    private static final Gson gson =
            new GsonBuilder().setPrettyPrinting().create();

    // 保存
    public static void save(Context context, MeetingRecord record) {
        try {
            String filename = "meeting_" + record.id + ".json";
            File file = new File(context.getFilesDir(), filename);

            FileWriter writer = new FileWriter(file);
            gson.toJson(record, writer);
            writer.flush();
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 全件読み込み
    public static List<MeetingRecord> loadAll(Context context) {
        List<MeetingRecord> list = new ArrayList<>();

        File dir = context.getFilesDir();
        File[] files = dir.listFiles();

        if (files == null) return list;

        for (File file : files) {
            if (!file.getName().startsWith("meeting_")) continue;

            try {
                FileReader reader = new FileReader(file);
                MeetingRecord record =
                        gson.fromJson(reader, MeetingRecord.class);
                reader.close();

                list.add(record);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    // IDで1件読み込み（詳細画面用）
    public static MeetingRecord loadById(Context context, String id) {
        List<MeetingRecord> records = loadAll(context);

        for (MeetingRecord record : records) {
            if (record.id != null && record.id.equals(id)) {
                return record;
            }
        }
        return null;
    }

}
