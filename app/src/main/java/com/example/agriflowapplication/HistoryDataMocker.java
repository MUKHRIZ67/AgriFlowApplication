package com.example.agriflowapplication;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.util.Calendar;
import java.util.Random;

public class HistoryDataMocker {

    public static void generateMockData() {
        FirebaseFirestore db = FirebaseManager.getInstance().getDb();
        WriteBatch batch = db.batch();
        Random random = new Random();

        String[] types = {"moisture", "temperature", "humidity", "light"};

        for (int i = 0; i < 40; i++) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.HOUR, -i * 2); // Spread over a few days
            
            String type = types[i % types.length];
            double value = 20 + (60) * random.nextDouble();
            String unit = "temperature".equals(type) ? "°C" : "%%";
            
            HistoryRecord record = new HistoryRecord(type, value, unit, "Prototype Zone", "Mock-ESP32", new Timestamp(calendar.getTime()));
            batch.set(db.collection("History").document(), record);
        }

        batch.commit();
    }
}
