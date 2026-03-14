package com.yamanobora.offlinerecorder;

public class SpeechBlock {

    public long startTime;
    public long endTime;
    public String text;

    public SpeechBlock(long startTime, String text) {
        this.startTime = startTime;
        this.text = text;
        this.endTime = startTime;
    }
}
