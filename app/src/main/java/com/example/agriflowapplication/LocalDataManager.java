package com.example.agriflowapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class LocalDataManager {
    private static final String PREF_NAME = "AgriFlowPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_FARM_NAME = "farmName";
    private static final String KEY_FARM_LOCATION = "farmLocation";

    private static LocalDataManager instance;
    private final SharedPreferences sharedPreferences;

    private LocalDataManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Initialize with default credentials if empty
        if (!sharedPreferences.contains(KEY_EMAIL)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_EMAIL, "denice67@gmail.com");
            editor.putString(KEY_PASSWORD, "password123");
            editor.putString(KEY_FULL_NAME, "Denice Kang");
            editor.putString(KEY_PHONE, "012-3456789");
            editor.putString(KEY_FARM_NAME, "Green Valley Farm");
            editor.putString(KEY_FARM_LOCATION, "Shah Alam, Selangor");
            editor.apply();
        }
    }

    public static synchronized LocalDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocalDataManager(context.getApplicationContext());
        }
        return instance;
    }

    // Login Function
    public boolean login(String email, String password) {
        String storedEmail = sharedPreferences.getString(KEY_EMAIL, "");
        String storedPassword = sharedPreferences.getString(KEY_PASSWORD, "");

        if (email.equals(storedEmail) && password.equals(storedPassword)) {
            sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply();
            return true;
        }
        return false;
    }

    // Logout Function
    public void logout() {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // Profile Functions
    public String getFullName() { return sharedPreferences.getString(KEY_FULL_NAME, "Denice Kang"); }
    public String getEmail() { return sharedPreferences.getString(KEY_EMAIL, "denice67@gmail.com"); }
    public String getPhone() { return sharedPreferences.getString(KEY_PHONE, "012-3456789"); }
    public String getFarmName() { return sharedPreferences.getString(KEY_FARM_NAME, "Green Valley Farm"); }
    public String getFarmLocation() { return sharedPreferences.getString(KEY_FARM_LOCATION, "Shah Alam, Selangor"); }

    public void updateProfile(String name, String phone, String farmName, String farmLocation) {
        sharedPreferences.edit()
                .putString(KEY_FULL_NAME, name)
                .putString(KEY_PHONE, phone)
                .putString(KEY_FARM_NAME, farmName)
                .putString(KEY_FARM_LOCATION, farmLocation)
                .apply();
    }

    public boolean changePassword(String currentPassword, String newPassword) {
        String storedPassword = sharedPreferences.getString(KEY_PASSWORD, "");
        if (currentPassword.equals(storedPassword)) {
            sharedPreferences.edit().putString(KEY_PASSWORD, newPassword).apply();
            return true;
        }
        return false;
    }
}