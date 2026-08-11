package com.example.agriflowapplication;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FarmerListActivity extends AppCompatActivity {

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

        RecyclerView rvFarmers = findViewById(R.id.rvFarmers);
        progressBar = findViewById(R.id.progressBar);

        rvFarmers.setLayoutManager(new LinearLayoutManager(this));
        farmerList = new ArrayList<>();
        farmerIds = new ArrayList<>();
        adapter = new FarmerAdapter(this, farmerList, farmerIds);
        rvFarmers.setAdapter(adapter);

        setupSwipeToDelete(rvFarmers);

        findViewById(R.id.fabAddFarmer).setOnClickListener(v -> startActivity(new Intent(FarmerListActivity.this, AddFarmerActivity.class)));

        setupBottomNavigation();
        loadFarmers();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_manage);
        
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    FirebaseFirestore.getInstance().collection("users")
                            .document(user.getUid())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                Intent intent;
                                if (documentSnapshot.exists() && "admin".equalsIgnoreCase(documentSnapshot.getString("role"))) {
                                    intent = new Intent(FarmerListActivity.this, AdminDashboardActivity.class);
                                } else {
                                    intent = new Intent(FarmerListActivity.this, DashboardActivity.class);
                                }
                                startActivity(intent);
                                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                finish();
                            });
                }
                return true;
            } else if (itemId == R.id.nav_manage) {
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(FarmerListActivity.this, AdminProfileActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupSwipeToDelete(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private final ColorDrawable background = new ColorDrawable(Color.RED);
            private final Drawable icon = ContextCompat.getDrawable(FarmerListActivity.this, R.drawable.ic_delete);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                new AlertDialog.Builder(FarmerListActivity.this)
                        .setTitle(R.string.delete_farmer)
                        .setMessage(R.string.delete_farmer_confirmation)
                        .setPositiveButton(R.string.delete, (dialog, which) -> adapter.deleteFarmer(position))
                        .setNegativeButton(R.string.cancel, (dialog, which) -> adapter.notifyItemChanged(position))
                        .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                        .show();
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View itemView = viewHolder.itemView;

                if (dX < 0) { // Swiping to the left
                    background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    background.draw(c);

                    if (icon != null) {
                        int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconTop = itemView.getTop() + iconMargin;
                        int iconBottom = iconTop + icon.getIntrinsicHeight();
                        int iconLeft = itemView.getRight() - iconMargin - icon.getIntrinsicWidth();
                        int iconRight = itemView.getRight() - iconMargin;
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        icon.draw(c);
                    }
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
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
