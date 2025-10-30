package com.example.bazunia.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences; // Potrzebny import
import android.os.IBinder;
import android.util.Log;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.bazunia.utils.Constants;
import com.example.bazunia.utils.NotificationHelper;
import com.example.bazunia.R;
import com.example.bazunia.ui.LoginActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VpsClientService extends Service {

    private static final String TAG = "VpsClientService";
    // ... (stałe bez zmian) ...
    private static final String CHANNEL_ID = "VpsClientServiceChannel";
    private static final int NOTIFICATION_ID = 101;
    private static final int POLLING_INTERVAL_SECONDS = 5;

    private OkHttpClient httpClient;
    private ScheduledExecutorService executorService;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationHelper notificationHelper;

    // [NOWA ZMIENNA] Do odczytu tokena
    private SharedPreferences authPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Serwis klienta VPS: onCreate");
        startForeground(NOTIFICATION_ID, createNotification());

        // Inicjalizacja komponentów
        httpClient = new OkHttpClient();
        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);
        notificationHelper = new NotificationHelper(this);

        // [NOWY KOD] Pobierz SharedPreferences, gdzie zapisany jest token
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);

        executorService = Executors.newSingleThreadScheduledExecutor();

        // 1. Rejestracja IP Androida (bez zmian, nadal używa hasła)
        registerAndroidIp();

        // 2. Cykliczne pobieranie danych (Polling)
        executorService.scheduleWithFixedDelay(this::fetchSensorData, 0, POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private Notification createNotification() {
        // ... (kod bez zmian) ...
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Serwis Klienta VPS", NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitorowanie czujników")
                .setContentText("Serwis aktywnie pobiera dane z VPS.")
                .setSmallIcon(R.drawable.ic_notification_alert)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    /**
     * [POPRAWIONA METODA]
     * Cyklicznie pobiera dane czujników z VPS, używając Tokena Google.
     */
    private void fetchSensorData() {
        // [NOWA LOGIKA] Pobierz zapisany token Google
        String idToken = authPrefs.getString(LoginActivity.KEY_ID_TOKEN, null);

        if (idToken == null) {
            Log.e(TAG, "BLAD POBIERANIA: Brak zapisanego ID Tokena. Serwis czeka.");
            // Serwis poczeka na następny cykl. W międzyczasie user może się zalogować.
            return;
        }

        // [POPRAWKA] Zamiast "Password", wysyłamy "Authorization"
        Request request = new Request.Builder()
                .url(Constants.SENSOR_DATA_ENDPOINT) // GET do /data/android
                .addHeader("Authorization", "Bearer " + idToken) // <-- POPRAWNY NAGŁÓWEK
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA danych z VPS: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // Używamy try-with-resources, to jest POPRAWNA implementacja (bez wycieków)
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        Log.d(TAG, "Odebrano dane: " + jsonResponse);
                        processSensorData(jsonResponse);
                    } else {
                        // [POPRAWKA] Jeśli kod to 401 lub 403, token mógł wygasnąć
                        if (resp.code() == 401 || resp.code() == 403) {
                            Log.e(TAG, "OSTRZEZENIE: Token odrzucony przez serwer (kod: " + resp.code() + "). Może wygasł.");
                            // W realnej apce tu byłaby logika odświeżenia tokena
                            // Na razie po prostu usuwamy stary token, żeby wymusić ponowne logowanie
                            authPrefs.edit().remove(LoginActivity.KEY_ID_TOKEN).apply();
                        } else {
                            Log.w(TAG, "OSTRZEZENIE: Pobieranie danych nieudane, kod: " + resp.code());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Parsuje JSON, zapisuje do bazy i sprawdza progi.
     * (Ta metoda jest poprawna, bez zmian)
     */
    private void processSensorData(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray jsonArray = root.getJSONArray("sensors");
            if (jsonArray.length() == 0) return;

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject sensorJson = jsonArray.getJSONObject(i);
                String gatewayId = sensorJson.getString("gateway_id");
                String sensorId = sensorJson.getString("sensor_id");
                String type = sensorJson.getString("type");
                String value = sensorJson.getString("value");
                long timestamp = sensorJson.getLong("timestamp");

                SensorModel sensor = new SensorModel(gatewayId, sensorId, type, value, timestamp);
                dbHelper.addSensorData(sensor);
                checkThresholds(sensor);
            }
            sendDataUpdateBroadcast();
        } catch (JSONException e) {
            Log.e(TAG, "Blad parsowania JSON z VPS: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Nieoczekiwany blad przetwarzania danych: " + e.getMessage());
        }
    }

    /**
     * Sprawdza progi.
     * (Ta metoda jest poprawna, bez zmian)
     */
    private void checkThresholds(SensorModel sensor) {
        // ... (cały kod checkThresholds bez zmian) ...
        if ("temperature".equalsIgnoreCase(sensor.type) || "humidity".equalsIgnoreCase(sensor.type)) {
            try {
                float currentValue = Float.parseFloat(sensor.value);
                boolean isHumidity = "humidity".equalsIgnoreCase(sensor.type);
                float defaultMin = isHumidity ? 5.0f : 18.0f;
                float defaultMax = isHumidity ? 30.0f : 22.0f;
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);
                String alertTitle = String.format(Locale.getDefault(), "Alert: %s %s", sensor.type, sensor.sensorId);
                String alertMessage = null;
                if (currentValue < min) {
                    alertMessage = String.format(Locale.getDefault(), "Wartość %s jest za niska: %.1f. Próg min: %.1f.", sensor.type, currentValue, min);
                } else if (currentValue > max) {
                    alertMessage = String.format(Locale.getDefault(), "Wartość %s jest za wysoka: %.1f. Próg max: %.1f.", sensor.type, currentValue, max);
                }
                if (alertMessage != null) {
                    int notificationId = (sensor.gatewayId + sensor.sensorId).hashCode();
                    notificationHelper.showNotification(alertTitle, alertMessage, notificationId);
                    Log.w(TAG, alertMessage);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Wartość czujnika nie jest numeryczna: " + sensor.value);
            }
        } else if ("door_contact".equalsIgnoreCase(sensor.type)) {
            if ("1".equals(sensor.value)) {
                String alertTitle = "Alert: Drzwi/Okna";
                String alertMessage = String.format("Czujnik %s (Bramka %s) ZGŁASZA OTWARTY STAN!", sensor.sensorId, sensor.gatewayId);
                int notificationId = (sensor.gatewayId + sensor.sensorId).hashCode();
                notificationHelper.showNotification(alertTitle, alertMessage, notificationId);
                Log.w(TAG, alertMessage);
            }
        }
    }

    private void sendDataUpdateBroadcast() {
        // ... (bez zmian) ...
        Intent intent = new Intent(Constants.ACTION_DATA_UPDATED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Rejestruje IP Androida.
     * (Ta metoda jest poprawna, bez zmian - używa hasła, co jest OK dla tego endpointu)
     */
    private void registerAndroidIp() {
        // ... (cały kod registerAndroidIp bez zmian) ...
        String json = String.format(Locale.getDefault(), "{\"password\":\"%s\",\"port\":%d}", Constants.SECRET_PASSWORD, Constants.ANDROID_LISTEN_PORT);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.REGISTRATION_ENDPOINT)
                .post(body)
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { Log.e(TAG, "BLAD POST rejestracji do VPS: " + e.getMessage()); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) { Log.i(TAG, "SUKCES: IP Androida zarejestrowane: " + Constants.ANDROID_LISTEN_PORT);
                } else { Log.w(TAG, "OSTRZEZENIE: Rejestracja nieudana, kod: " + response.code()); }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
        Log.d(TAG, "Serwis klienta VPS: onDestroy (zatrzymano Polling)...");
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}