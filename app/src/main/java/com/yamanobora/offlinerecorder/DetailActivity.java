package com.yamanobora.offlinerecorder;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DetailActivity extends AppCompatActivity {

    private TextView summaryTextView;
    private TextView fullTextView;
    private TextView titleTextView;
    private TextView timeTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        titleTextView = findViewById(R.id.titleText);
        timeTextView = findViewById(R.id.timeText);
        summaryTextView = findViewById(R.id.summaryText);
        fullTextView = findViewById(R.id.fullText);

        // 一覧から渡された recordId を取得
        String recordId = getIntent().getStringExtra("recordId");
        if (recordId == null) return;

        // JSON から該当データを読み込み
        MeetingRecord record = MeetingStorage.loadById(this, recordId);
        if (record == null) return;

        titleTextView.setText(record.title);
        timeTextView.setText(
                android.text.format.DateFormat.format(
                        "yyyy/MM/dd HH:mm",
                        record.startTime
                )
        );

        summaryTextView.setText(
                record.summaryText != null && !record.summaryText.isEmpty()
                        ? record.summaryText
                        : "（要約なし）"
        );

        fullTextView.setText(
                record.fullText != null && !record.fullText.isEmpty()
                        ? record.fullText
                        : "（全文なし）"
        );
    }
}
