package com.example.agriflowapplication;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class FarmerAdapter extends RecyclerView.Adapter<FarmerAdapter.FarmerViewHolder> {

    private List<Map<String, Object>> farmerList;
    private List<String> farmerIds;
    private Context context;

    public FarmerAdapter(Context context, List<Map<String, Object>> farmerList, List<String> farmerIds) {
        this.context = context;
        this.farmerList = farmerList;
        this.farmerIds = farmerIds;
    }

    @NonNull
    @Override
    public FarmerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_farmer, parent, false);
        return new FarmerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FarmerViewHolder holder, int position) {
        Map<String, Object> farmer = farmerList.get(position);
        String farmerId = farmerIds.get(position);

        holder.tvFarmerName.setText((String) farmer.get("fullName"));
        holder.tvFarmName.setText((String) farmer.get("farmName"));

        holder.btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(context, EditFarmerActivity.class);
            intent.putExtra("farmerId", farmerId);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return farmerList.size();
    }

    public static class FarmerViewHolder extends RecyclerView.ViewHolder {
        TextView tvFarmerName, tvFarmName;
        Button btnEdit;

        public FarmerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFarmerName = itemView.findViewById(R.id.tvFarmerName);
            tvFarmName = itemView.findViewById(R.id.tvFarmName);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }
    }
}
