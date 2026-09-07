package com.example.agriflowapplication;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryRecord> records;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy\nhh:mm a", Locale.getDefault());

    public HistoryAdapter(List<HistoryRecord> records) {
        this.records = records;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryRecord record = records.get(position);
        Context context = holder.itemView.getContext();
        
        String unit = record.getUnit() != null ? record.getUnit() : "%";
        if ("%%".equals(unit)) unit = "%";
        holder.tvReadingValue.setText(String.format(Locale.getDefault(), "%.1f%s", record.getValue(), unit));
        
        String sensorInfo = (record.getDeviceId() != null ? record.getDeviceId() : "Unknown Sensor");
        if (record.getZone() != null) {
            sensorInfo += " · " + record.getZone();
        }
        holder.tvSensorInfo.setText(sensorInfo);

        if (record.getTimestamp() != null) {
            holder.tvDateTime.setText(dateFormat.format(record.getTimestamp().toDate()));
        } else {
            holder.tvDateTime.setText("---");
        }

        // Apply Icon and Color based on sensor type
        applySensorStyling(holder, record.getSensorType(), context);
    }

    private void applySensorStyling(ViewHolder holder, String type, Context context) {
        if (type == null) type = "moisture";
        
        switch (type.toLowerCase()) {
            case "temperature":
                holder.ivSensorIcon.setImageResource(android.R.drawable.ic_menu_info_details);
                holder.ivSensorIcon.setColorFilter(Color.parseColor("#F06292"));
                break;
            case "humidity":
                holder.ivSensorIcon.setImageResource(android.R.drawable.ic_menu_view);
                holder.ivSensorIcon.setColorFilter(Color.parseColor("#9575CD"));
                break;
            case "light":
                holder.ivSensorIcon.setImageResource(android.R.drawable.ic_menu_day);
                holder.ivSensorIcon.setColorFilter(Color.parseColor("#FBC02D"));
                break;
            case "waterlevel":
                holder.ivSensorIcon.setImageResource(android.R.drawable.ic_menu_save);
                holder.ivSensorIcon.setColorFilter(Color.parseColor("#1E88E5"));
                break;
            default: // moisture
                holder.ivSensorIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                holder.ivSensorIcon.setColorFilter(Color.parseColor("#64B5F6"));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvReadingValue, tvSensorInfo, tvDateTime;
        ImageView ivSensorIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvReadingValue = itemView.findViewById(R.id.tvReadingValue);
            tvSensorInfo = itemView.findViewById(R.id.tvSensorInfo);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            ivSensorIcon = itemView.findViewById(R.id.ivSensorIcon);
        }
    }
}
