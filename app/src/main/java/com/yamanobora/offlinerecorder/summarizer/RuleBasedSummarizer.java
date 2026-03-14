package com.yamanobora.offlinerecorder.summarizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政・議事録特化のルールベース要約エンジン
 * 話題ごとの見出し付きで、地域猫や住民トラブル対応もカバー
 */
public class RuleBasedSummarizer implements Summarizer {

    @Override
    public String summarize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "（内容なし）";
        }

        // 改行ごとに分割
        String[] lines = text.split("\n");

        // 見出しごとに文章をまとめるマップ
        Map<String, List<String>> topics = new LinkedHashMap<>();

        for (String line : lines) {
            String topic = detectTopic(line);

            topics
                    .computeIfAbsent(topic, k -> new ArrayList<>())
                    .add(line);
        }

        // 見出し＋本文形式で要約作成
        StringBuilder summary = new StringBuilder();

        for (Map.Entry<String, List<String>> entry : topics.entrySet()) {
            summary.append("【").append(entry.getKey()).append("】\n");
            summary.append("・")
                    .append(entry.getValue().get(0)) // まず1文だけ抽出
                    .append("\n\n");
        }

        return summary.toString().trim();
    }

    /**
     * 文章から話題を判定
     * @param line 1行の文章
     * @return 話題ラベル
     */
    private String detectTopic(String line) {
        line = line.trim();

        // 立ち入り検査
        if (line.contains("検査") || line.contains("確認")) return "立ち入り検査";

        // 書類関係
        if (line.contains("書類") || line.contains("提出") || line.contains("不備")) return "書類関係";

        // 指導・対応
        if (line.contains("対応") || line.contains("指導") || line.contains("改善")) return "指導・対応";

        // 今後の予定
        if (line.contains("次回") || line.contains("期限")) return "今後の予定";

        // 結論
        if (line.contains("決定") || line.contains("結論")) return "結論";

        // 課題
        if (line.contains("課題") || line.contains("問題")) return "課題";

        // 合意事項
        if (line.contains("合意") || line.contains("承認")) return "合意事項";

        // 苦情・トラブル（住民・犬猫関連）
        if (line.contains("苦情") || line.contains("クレーム") || line.contains("糞") || line.contains("臭い")) {
            return "苦情・トラブル";
        }

        // 餌やり問題（地域猫）
        if (line.contains("餌やり") || line.contains("放置") || line.contains("無責任")) return "餌やり問題";

        // その他
        return "その他";
    }
}
