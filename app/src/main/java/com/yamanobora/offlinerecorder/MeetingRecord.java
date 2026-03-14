package com.yamanobora.offlinerecorder;

import java.util.List;

public class MeetingRecord {

    public String id;
    public String title;
    public long startTime;
    public long endTime;

    public List<SpeechBlock> speechBlocks;
    public String fullText;
    public String summaryText; // ← 追加

    public MeetingRecord(
            String id,
            String title,
            long startTime,
            long endTime,
            List<SpeechBlock> speechBlocks,
            String fullText,
            String summaryText
    ) {
        this.id = id;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.speechBlocks = speechBlocks;
        this.fullText = fullText;
        this.summaryText = summaryText;
    }
}

