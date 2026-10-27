# 🌾 AgriFlow - Smart Agricultural Management System

**AgriFlow** is an end-to-end IoT-powered smart farming platform designed to empower farmers and agricultural administrators. It combines real-time environmental telemetry, manual and automated pump controls, intelligent irrigation scheduling, push alerts, and an AI-powered Farm Assistant powered by Google Gemini.

---

## 🌟 Key Features

### 📊 Real-Time Telemetry Dashboard
- **Soil Moisture Monitoring:** Live soil moisture percentage ($0\% - 100\%$) with classification (Dry, Optimal, Wet).
- **Water Tank Level:** Non-contact liquid level sensing using an **HC-SR04 Ultrasonic Sensor**.
- **Climate Monitoring:** Real-time Temperature (°C) and Humidity (%) via **DHT22 Sensor**.
- **Sunlight & Lux Sensing:** Light intensity (%) and calculated Lux values using an **LDR Light Sensor**.

### 👥 Dual User Roles & Customized Themes
- **Farmer Dashboard:** Minimalist, clean light theme (`#FCFDF6`) optimized for daily field monitoring.
- **Admin Terminal:** High-tech dark IoT theme (`#0F172A`) with farmer user management, farm location oversight, and advanced control panels.
- **Role-Based Navigation:** Notifications, telemetry views, and profiles automatically adjust themes and navigation according to user role.

### 🚰 Irrigation & Fertilizer Controls
- **Manual Pump Control:** Instant ON/OFF relay control for **Water Pump** and **Fertilizer Pump** with visual state indicators (`RUNNING`, `SCHEDULED`, `OFF`).
- **Interactive Schedule Testing:** Built-in test buttons for water and fertilizer pumps running a 10-second test cycle with history logging and notification alerts.
- **Automatic Light Intensity Irrigation:** Threshold-based automatic watering cycle (e.g. at 70% light intensity) with a **1-minute safety run timer** and **5-minute anti-flooding cooldown**.

### ⏰ Smart Irrigation Scheduling
- **Flexible Scheduling:** Set custom start times (12-hour AM/PM), run durations (minutes/seconds), and repeat frequencies (*Every Day*, *Every 2 Days*, *Every 3 Days*, *Every 7 Days*, *Every 14 Days*).
- **24/7 Standalone Hardware Execution:** The ESP32 synchronizes with real-time internet clock servers (**NTP**) to execute schedules autonomously even if the phone is turned off or offline.

### 🔔 Sensor Alerts & Push Notifications
- **Centralized Alert Manager:** Real-time alert evaluations for critical low water levels, dry soil, extreme temperatures, and excessive sunlight.
- **Anti-Spam Protection:** Built-in cooldown timers and escalation logic to prevent alert spam.
- **In-App Notification History:** Filter by read/unread notifications with click-through navigation to the respective dashboard.

### 🤖 AgriFlow AI Assistant (Gemini API)
- **Context-Aware AI Chatbot:** Answers agricultural questions by analyzing your farm's live telemetry, active schedules, and recent irrigation history.
- **Express.js Backend:** Node.js server relaying prompts securely to Google Gemini AI API.

---

## 📐 Hardware Wiring & Pinout Guide (ESP32)

Connect your hardware sensors and relay modules to the ESP32 board according to the pinout table below:

| Hardware Module | Module Pin | ESP32 GPIO Pin | Function |
| :--- | :--- | :--- | :--- |
| **DHT22** (Temp & Humidity) | DATA / OUT | **GPIO 4** | Digital Sensor Data |
| **LDR** (Light Sensor) | AO (Analog Out) | **GPIO 32** | ADC Input |
| **Soil Moisture Sensor** | AO (Analog Out) | **GPIO 34** | ADC Input (Input-Only Pin) |
| **HC-SR04** (Ultrasonic) | TRIG | **GPIO 25** | Pulse Output |
| | ECHO | **GPIO 33** | Echo Input |
| **Water Pump Relay** (IN1) | CTRL / IN1 | **GPIO 27** | Digital Relay Control |
| | COM & NO | Screw Block | In-line with Water Pump Positive Wire |
| **Fertilizer Relay** (IN2) | CTRL / IN2 | **GPIO 26** | Digital Relay Control |
| | COM & NO | Screw Block | In-line with Fertilizer Pump Positive Wire |

---

## 🤖 ESP32 Master Firmware Sketch

Upload the complete Arduino C++ sketch to your ESP32 board. Ensure you set your WiFi credentials, timezone offset (`GMT_OFFSET_SEC`), and Firebase Auth credentials.

<details>
<summary><b>Click to Expand ESP32 Master C++ Code</b></summary>

```cpp
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <DHT.h>
#include "time.h"

// 1. WIFI CONFIG
#define WIFI_SSID "YOUR_WIFI_NAME"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

// 2. FIREBASE CONFIG
#define API_KEY "AIzaSyDY845YkT1G5Ivc3IpdaoGPT_FzeJdTIcg"
#define FIREBASE_PROJECT_ID "agriflow-1628e"
#define USER_EMAIL "esp32@agriflow.com"
#define USER_PASSWORD "password123"

// 3. PUMP RELAY PINS
#define WATER_PUMP 27
#define FERTILIZER_PUMP 26

#define RELAY_ON  HIGH   // Set to LOW if active-low relay module
#define RELAY_OFF LOW    

// 4. SENSOR PINS
#define DHTPIN 4 
#define DHTTYPE DHT22
DHT dht(DHTPIN, DHTTYPE);

#define LDR_PIN 32
#define SOIL_PIN 34
#define TRIG_PIN 25
#define ECHO_PIN 33

const float TANK_DEPTH_CM = 7.2;     
const float SENSOR_OFFSET_CM = 2.0;  

// 5. NTP TIME CONFIG (UTC+8 for Malaysia/Singapore/Philippines: 8 * 3600 = 28800)
#define GMT_OFFSET_SEC (8 * 3600)  
#define DAYLIGHT_OFFSET_SEC 0
const char* ntpServer = "pool.ntp.org";

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastControlCheckTime = 0;
unsigned long lastTelemetryPublishTime = 0;
const unsigned long CONTROL_INTERVAL_MS = 3000;   
const unsigned long TELEMETRY_INTERVAL_MS = 10000; 

unsigned long waterRunUntilMillis = 0;
unsigned long fertRunUntilMillis = 0;
String lastWaterTriggeredMinute = "";
String lastFertTriggeredMinute = "";

void getCurrentTimeFormats(String &fmt1, String &fmt2) {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    fmt1 = ""; fmt2 = ""; return;
  }
  int hour12 = timeinfo.tm_hour % 12;
  if (hour12 == 0) hour12 = 12;
  int minute = timeinfo.tm_min;
  const char* amPm = (timeinfo.tm_hour >= 12) ? "PM" : "AM";

  char buf1[20], buf2[20];
  snprintf(buf1, sizeof(buf1), "%02d:%02d %s", hour12, minute, amPm);
  snprintf(buf2, sizeof(buf2), "%d:%02d %s", hour12, minute, amPm);
  fmt1 = String(buf1); fmt2 = String(buf2);
}

bool isScheduleEnabled(String upperPayload) {
  int idx = upperPayload.indexOf("ENABLED");
  if (idx == -1) return false;
  String seg = upperPayload.substring(idx, min((int)upperPayload.length(), idx + 60));
  return (seg.indexOf("TRUE") != -1);
}

bool checkWaterSchedule(String tFmt1, String tFmt2) {
  if (tFmt1.length() == 0) return false;
  if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, "(default)", "schedules/water")) {
    String upperPayload = fbdo.payload();
    upperPayload.toUpperCase();
    bool enabled = isScheduleEnabled(upperPayload);
    int timeIdx = upperPayload.indexOf("STARTTIME");

    if (enabled && timeIdx != -1) {
      String timeSeg = upperPayload.substring(timeIdx, min((int)upperPayload.length(), timeIdx + 120));
      String upperFmt1 = tFmt1; upperFmt1.toUpperCase();
      String upperFmt2 = tFmt2; upperFmt2.toUpperCase();

      if (timeSeg.indexOf(upperFmt1) != -1 || timeSeg.indexOf(upperFmt2) != -1) {
        if (lastWaterTriggeredMinute != tFmt1) {
          lastWaterTriggeredMinute = tFmt1;
          waterRunUntilMillis = millis() + (5 * 60 * 1000L); // 5 min run
        }
        return true;
      }
    }
  }
  return false;
}

void processPumpControls() {
  String tFmt1, tFmt2;
  getCurrentTimeFormats(tFmt1, tFmt2);

  bool waterExplicitOff = false, fertExplicitOff = false;
  bool waterManualOn = false, fertManualOn = false;

  if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, "(default)", "system_data/device_control")) {
    String upperPayload = fbdo.payload();
    upperPayload.toUpperCase();

    int wIdx = upperPayload.indexOf("WATERPUMPSTATE");
    if (wIdx != -1) {
      String wSeg = upperPayload.substring(wIdx, min((int)upperPayload.length(), wIdx + 120));
      if (wSeg.indexOf("\"RUNNING\"") != -1 || wSeg.indexOf("\"ON\"") != -1) waterManualOn = true;
      else if (wSeg.indexOf("\"OFF\"") != -1) waterExplicitOff = true;
    }

    int fIdx = upperPayload.indexOf("FERTILIZERPUMPSTATE");
    if (fIdx != -1) {
      String fSeg = upperPayload.substring(fIdx, min((int)upperPayload.length(), fIdx + 120));
      if (fSeg.indexOf("\"RUNNING\"") != -1 || fSeg.indexOf("\"ON\"") != -1) fertManualOn = true;
      else if (fSeg.indexOf("\"OFF\"") != -1) fertExplicitOff = true;
    }
  }

  checkWaterSchedule(tFmt1, tFmt2);

  bool waterActive = waterManualOn || (!waterExplicitOff && millis() < waterRunUntilMillis);
  bool fertActive  = fertManualOn  || (!fertExplicitOff  && millis() < fertRunUntilMillis);

  digitalWrite(WATER_PUMP, waterActive ? RELAY_ON : RELAY_OFF);
  digitalWrite(FERTILIZER_PUMP, fertActive ? RELAY_ON : RELAY_OFF);
}

void setup() {
  Serial.begin(115200);
  dht.begin();
  pinMode(TRIG_PIN, OUTPUT); pinMode(ECHO_PIN, INPUT);
  pinMode(WATER_PUMP, OUTPUT); pinMode(FERTILIZER_PUMP, OUTPUT);
  digitalWrite(WATER_PUMP, RELAY_OFF); digitalWrite(FERTILIZER_PUMP, RELAY_OFF);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) { delay(500); }

  configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, ntpServer);
  config.api_key = API_KEY;
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

void loop() {
  if (Firebase.ready()) {
    unsigned long currentMillis = millis();
    if (currentMillis - lastControlCheckTime >= CONTROL_INTERVAL_MS) {
      lastControlCheckTime = currentMillis;
      processPumpControls();
    }
  }
}
```
</details>

---

## 🛠️ Software Stack & Dependencies

- **Android Client:** Java / Kotlin, AndroidX, Material Design 3, Firebase Firestore, Firebase Auth, Firebase Messaging (FCM), MPAndroidChart.
- **Backend API:** Node.js, Express.js, Google Gemini AI API (`@google/generative-ai`), dotenv.
- **Database:** Firebase Cloud Firestore & Realtime Database.
- **Embedded Hardware:** ESP32, Firebase_ESP_Client, DHT Sensor Library.

---

## 🚀 Setup & Installation Instructions

### 1. Firebase Setup
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/).
2. Enable **Firebase Authentication** (Email/Password).
3. Create a hardware user:
   - **Email:** `esp32@agriflow.com`
   - **Password:** `password123`
4. Enable **Cloud Firestore** in test or production mode.
5. Download `google-services.json` and place it in the `app/` directory of the Android project.

### 2. Gemini AI Server Setup (`server/`)
1. Obtain an API key from [Google AI Studio](https://aistudio.google.com/).
2. Navigate to the `server/` directory:
   ```bash
   cd server
   ```
3. Create a `.env` file:
   ```env
   GEMINI_API_KEY=your_google_ai_studio_key_here
   PORT=3000
   ```
4. Install dependencies and start the server:
   ```bash
   npm install
   npm start
   ```

### 3. Android App Execution
1. Open `AgriFlowApplication` in Android Studio.
2. Build and run the app on an Android Device or Emulator.
3. The app automatically connects to local or remote server endpoints for AI assistance and Firebase Firestore for real-time telemetry.

---

## 📄 License
This project is developed for educational and smart agriculture applications.
