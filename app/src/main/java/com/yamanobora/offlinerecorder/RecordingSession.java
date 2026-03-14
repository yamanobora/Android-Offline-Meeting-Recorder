package com.yamanobora.offlinerecorder;

import java.util.ArrayList;
import java.util.List;

public class RecordingSession {

    public final long startTime;
    public long endTime;

    private final List<SpeechBlock> blocks = new ArrayList<>();
    private final StringBuilder fullText = new StringBuilder();

    private String lastText = "";

    public RecordingSession() {
        startTime = System.currentTimeMillis();
    }

    public void updateLast(String text) {

        if (text.equals(lastText)) {
            return;
        }

        lastText = text;

        long now = System.currentTimeMillis();
        blocks.add(new SpeechBlock(now, text));

        fullText.append(text).append("\n");
    }

    public String getFullText() {
        return fullText.toString();
    }

    public void finish() {
        endTime = System.currentTimeMillis();
    }

    // 追加
    public void addLine(String text) {

        long now = System.currentTimeMillis();

        blocks.add(new SpeechBlock(now, text));

        fullText.append(text).append("\n");
    }

    // 追加
    public List<SpeechBlock> getBlocks() {
        return blocks;
    }
}