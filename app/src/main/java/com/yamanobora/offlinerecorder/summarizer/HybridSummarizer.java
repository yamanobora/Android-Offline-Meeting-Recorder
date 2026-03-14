package com.yamanobora.offlinerecorder.summarizer;

import android.content.Context;

public class HybridSummarizer implements Summarizer {

    private final Summarizer ruleBased;
    private final Summarizer aiBased; // nullになる場合あり
    private final boolean useAi;

    public HybridSummarizer(Context context, SummaryMode mode, boolean useAi) {
        this.ruleBased = new RuleBasedSummarizer();
        this.useAi = useAi;

        if (useAi) {
            this.aiBased = new AiSummarizer(context); // AIモードON時のみ
        } else {
            this.aiBased = null;
        }
    }

    @Override
    public String summarize(String text) {
        if (text == null || text.trim().isEmpty()) return "（内容なし）";

        String base = ruleBased.summarize(text);

        if (!useAi || aiBased == null) return base;

        try {
            String aiResult = aiBased.summarize(base);
            if (aiResult == null || aiResult.trim().isEmpty()) return base;
            return aiResult;
        } catch (Exception e) {
            e.printStackTrace();
            return base; // AI失敗時はルールベースでフォールバック
        }
    }
}
