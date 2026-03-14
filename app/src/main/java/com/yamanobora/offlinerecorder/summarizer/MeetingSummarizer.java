package com.yamanobora.offlinerecorder.summarizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class MeetingSummarizer {

    private final Summarizer summarizer;

    public MeetingSummarizer(Context context, AiSummarizer sharedAi) {
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean useAi = prefs.getBoolean("ai_mode", false);


        Log.d("AI_MODE", "MeetingSummarizer useAi = " + useAi);

        if (useAi) {
            // ★ ここが重要：毎回 new しない
            this.summarizer = sharedAi;
        } else {
            this.summarizer = new RuleBasedSummarizer();
        }
    }

    public String summarize(String fullText) {
        return summarizer.summarize(fullText);
    }
}