package com.yamanobora.offlinerecorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MeetingAdapter
        extends RecyclerView.Adapter<MeetingAdapter.ViewHolder> {

    private final List<MeetingRecord> records;
    private final OnItemClickListener listener;

    // 🔽 これを追加
    private final SimpleDateFormat format =
            new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN);

    public interface OnItemClickListener {
        void onItemClick(MeetingRecord record);
    }

    public MeetingAdapter(
            List<MeetingRecord> records,
            OnItemClickListener listener
    ) {
        this.records = records;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_meeting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {
        MeetingRecord record = records.get(position);

        holder.titleText.setText(record.title);
        holder.timeText.setText(
                format.format(record.startTime)
        );

        // ✅ 一覧は要約だけ表示
        holder.previewText.setText(
                record.summaryText != null
                        ? record.summaryText
                        : "（要約なし）"
        );

        holder.itemView.setOnClickListener(v ->
                listener.onItemClick(record)
        );
    }



    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView timeText;
        TextView previewText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.titleText);
            timeText = itemView.findViewById(R.id.timeText);
            previewText = itemView.findViewById(R.id.previewText);
        }
    }
}
