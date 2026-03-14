package com.yamanobora.offlinerecorder.summarizer;

import android.content.Context;

public class AiSummarizer implements Summarizer {

    static {
        System.loadLibrary("ai-chat");
    }

    public native void loadModel(String modelPath);

    public native String summarizeWithLlama(String text);


    public native String nativeTest(String text);

    public AiSummarizer(Context context) {
    }

    @Override
    public String summarize(String text) {

        if (text == null || text.trim().isEmpty()) return text;

        // ★ 長すぎる入力をカット
        if (text.length() > 500) {
            text = text.substring(0, 500);
        }

        return summarizeWithLlama(text);
    }
}
