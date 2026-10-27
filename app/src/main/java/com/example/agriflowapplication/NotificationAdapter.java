package com.example.agriflowapplication;

import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<NotificationRecord> notifications;
    private final OnNotificationClickListener listener;
    private final boolean isAdmin;

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationRecord notification);
    }

    public NotificationAdapter(List<NotificationRecord> notifications, OnNotificationClickListener listener) {
        this(notifications, listener, false);
    }

    public NotificationAdapter(List<NotificationRecord> notifications, OnNotificationClickListener listener, boolean isAdmin) {
        this.notifications = notifications;
        this.listener = listener;
        this.isAdmin = isAdmin;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationRecord notification = notifications.get(position);
        holder.tvTitle.setText(notification.getTitle());
        holder.tvMessage.setText(notification.getMessage());
        
        if (notification.getTimestamp() != null) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    notification.getTimestamp().toDate().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            holder.tvTimestamp.setText(timeAgo);
        }

        // Status indicator color
        int color = "critical".equalsIgnoreCase(notification.getStatus()) ? 
                Color.parseColor("#E53935") : Color.parseColor("#FB8C00");
        holder.statusIndicator.setBackgroundColor(color);

        // Apply theme styling
        if (isAdmin) {
            holder.cardNotification.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_surface));
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_on_surface));
            holder.tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_on_surface_variant));
            holder.tvTimestamp.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_on_surface_variant));
        } else {
            holder.cardNotification.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.surface_container));
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface));
            holder.tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface_variant));
            holder.tvTimestamp.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.on_surface_variant));
        }

        // Unread dot
        holder.ivUnreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> listener.onNotificationClick(notification));
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardNotification;
        TextView tvTitle, tvMessage, tvTimestamp;
        View statusIndicator, ivUnreadDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardNotification = itemView.findViewById(R.id.cardNotification);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            ivUnreadDot = itemView.findViewById(R.id.ivUnreadDot);
        }
    }
}
