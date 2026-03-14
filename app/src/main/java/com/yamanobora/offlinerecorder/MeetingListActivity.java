package com.yamanobora.offlinerecorder;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MeetingListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meeting_list);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<MeetingRecord> records = MeetingStorage.loadAll(this);

        MeetingAdapter adapter = new MeetingAdapter(records, record -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("recordId", record.id);
            startActivity(intent);
        });

        recyclerView.setAdapter(adapter);
    }
}
