package com.example.agriflowapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class AiMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<AiMessage> messages;
    private boolean isAdmin = false;

    public AiMessageAdapter(List<AiMessage> messages) {
        this.messages = messages;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == AiMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user_message, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai_message, parent, false);
            return new AiMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AiMessage message = messages.get(position);
        if (holder instanceof UserMessageViewHolder) {
            UserMessageViewHolder userHolder = (UserMessageViewHolder) holder;
            userHolder.tvUserMessage.setText(message.getText());

            if (isAdmin) {
                if (userHolder.cardUserMessage != null) {
                    userHolder.cardUserMessage.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_surface_high));
                }
                userHolder.tvUserMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_on_surface));
            } else {
                if (userHolder.cardUserMessage != null) {
                    userHolder.cardUserMessage.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ai_bubble_user));
                }
                userHolder.tvUserMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ai_text_user));
            }

        } else if (holder instanceof AiMessageViewHolder) {
            AiMessageViewHolder aiHolder = (AiMessageViewHolder) holder;
            aiHolder.tvAiMessage.setText(message.getText());
            
            if (message.isLoading()) {
                aiHolder.pbLoading.setVisibility(View.VISIBLE);
            } else {
                aiHolder.pbLoading.setVisibility(View.GONE);
            }

            if (isAdmin) {
                if (aiHolder.cardAiAvatar != null) {
                    aiHolder.cardAiAvatar.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_primary));
                }
                if (aiHolder.cardAiMessage != null) {
                    aiHolder.cardAiMessage.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_surface));
                }
                aiHolder.tvAiMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.iot_on_surface));
            } else {
                if (aiHolder.cardAiAvatar != null) {
                    aiHolder.cardAiAvatar.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_dark_green));
                }
                if (aiHolder.cardAiMessage != null) {
                    aiHolder.cardAiMessage.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ai_bubble_ai));
                }
                aiHolder.tvAiMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ai_text_ai));
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardUserMessage;
        TextView tvUserMessage;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardUserMessage = itemView.findViewById(R.id.cardUserMessage);
            tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
        }
    }

    static class AiMessageViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardAiAvatar;
        MaterialCardView cardAiMessage;
        TextView tvAiMessage;
        ProgressBar pbLoading;

        AiMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            cardAiAvatar = itemView.findViewById(R.id.cardAiAvatar);
            cardAiMessage = itemView.findViewById(R.id.cardAiMessage);
            tvAiMessage = itemView.findViewById(R.id.tvAiMessage);
            pbLoading = itemView.findViewById(R.id.pbLoading);
        }
    }
}
