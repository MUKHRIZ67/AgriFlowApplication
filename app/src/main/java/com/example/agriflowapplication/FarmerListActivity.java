package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FarmerListActivity extends AppCompatActivity {

    private RecyclerView rvFarmers;
    private FarmerAdapter adapter;
    private List<Map<String, Object>> farmerList;
    private List<String> farmerIds;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_farmer_list);

        db = FirebaseManager.getInstance().getDb();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvFarmers = findViewById(R.id.rvFarmers);
        progressBar = findViewById(R.id.progressBar);

        rvFarmers.setLayoutManager(new LinearLayoutManager(this));
        farmerList = new ArrayList<>();
        farmerIds = new ArrayList<>();
        adapter = new FarmerAdapter(this, farmerList, farmerIds);
        rvFarmers.setAdapter(adapter);

        setupBottomNavigation();
        loadFarmers();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_manage);
        
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(FarmerListActivity.this, AdminDashboardActivity.class));
                return true;
            } else if (itemId == R.id.nav_manage) {
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(FarmerListActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    private void loadFarmers() {
        progressBar.setVisibility(View.VISIBLE);
        // Fetch all users. You might want to filter by role if you have one.
        // For now, let's fetch all documents in "users" collection.
        db.collection("users").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    farmerList.clear();
                    farmerIds.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        // Optionally skip admin users
                        String role = document.getString("role");
                        if (!"admin".equalsIgnoreCase(role)) {
                            farmerList.add(document.getData());
                            farmerIds.add(document.getId());
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFarmers(); // Refresh list when returning from Edit activity
    }
}
