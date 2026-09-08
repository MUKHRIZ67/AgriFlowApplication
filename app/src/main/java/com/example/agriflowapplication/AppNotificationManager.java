package com.example.agriflowapplication;

import android.content.Context;

/**
 * @deprecated This class has been superseded by {@link SensorAlertManager} 
 * which provides expanded alert detection, centralized thresholds, and anti-spam protection
 * for all environmental sensors.
 */
@Deprecated
public class AppNotificationManager {
    public static AppNotificationManager getInstance(Context context) {
        return null; // Return null to identify any accidental remaining usages
    }
    public void evaluateWaterLevel(double percentage, String farmLocation) {}
    public void evaluateLightIntensity(double percentage, String farmLocation) {}
}
