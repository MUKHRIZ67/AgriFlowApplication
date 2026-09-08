package com.example.agriflowapplication;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FirebaseManager {
    private static FirebaseManager instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private FirebaseManager() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public void updateFcmToken() {
        if (auth.getCurrentUser() == null) {
            android.util.Log.d("AgriFlow_FCM", "No user logged in, skipping token update.");
            return;
        }
        
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                android.util.Log.w("AgriFlow_FCM", "Fetching FCM registration token failed", task.getException());
                return;
            }

            // Get new FCM registration token
            String token = task.getResult();
            String uid = auth.getUid();
            
            if (uid != null && token != null) {
                android.util.Log.d("AgriFlow_FCM", "FCM Token: " + token);
                
                Map<String, Object> tokenData = new HashMap<>();
                tokenData.put("fcmToken", token);
                tokenData.put("lastUpdated", com.google.firebase.Timestamp.now());
                tokenData.put("platform", "android");

                db.collection("users").document(uid)
                        .collection("device_tokens").document(token)
                        .set(tokenData)
                        .addOnSuccessListener(aVoid -> android.util.Log.d("AgriFlow_FCM", "Token saved successfully to Firestore"))
                        .addOnFailureListener(e -> android.util.Log.e("AgriFlow_FCM", "Error saving token to Firestore", e));
            }
        });
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getDb() {
        return db;
    }
}
