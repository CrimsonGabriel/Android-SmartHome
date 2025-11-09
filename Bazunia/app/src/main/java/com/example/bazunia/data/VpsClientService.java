package com.example.bazunia.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.bazunia.utils.CleanupManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.utils.NotificationHelper;
import com.example.bazunia.R;
import com.example.bazunia.ui.LoginActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
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
    private static final String CHANNEL_ID = "VpsClientServiceChannel";
    private static final int NOTIFICATION_ID = 101;

    private static final int POLLING_INTERVAL_READINGS_SECONDS = 5;
    private static final int POLLING_INTERVAL_GATEWAYS_SECONDS = 60;

    private OkHttpClient httpClient;
    private ScheduledExecutorService executorService;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationHelper notificationHelper;
    private CleanupManager cleanupManager;
    private SharedPreferences authPrefs;
    private Gson gson;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Serwis klienta VPS: onCreate");
        startForeground(NOTIFICATION_ID, createNotification());

        httpClient = new OkHttpClient();
        gson = new Gson();
        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);
        notificationHelper = new NotificationHelper(this);
        cleanupManager = new CleanupManager(this);
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);

        executorService = Executors.newSingleThreadScheduledExecutor();

        registerAndroidIp();

        executorService.scheduleWithFixedDelay(this::fetchSensorData, 0, POLLING_INTERVAL_READINGS_SECONDS, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::syncGatewayDefinitions, 1, POLLING_INTERVAL_GATEWAYS_SECONDS, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::fetchUpdateStatus, 5, POLLING_INTERVAL_GATEWAYS_SECONDS, TimeUnit.SECONDS);
    }

    private Notification createNotification() {
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
     * <<< NOWA METODA POMOCNICZA >>>
     * Bezpiecznie pobiera aktualny token JWT z SharedPreferences.
     * @return Token JWT lub null, jeśli nie istnieje.
     */
    private String getJwtToken() {
        // authPrefs jest już zainicjowane w onCreate
        String token = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (token == null) {
            Log.e(TAG, getString(R.string.log_error_no_token));
        }
        return token;
    }

    /**
     * Zadanie 1: Pobiera surowe ODCZYTY (stara logika, /data/android)
     */
    private void fetchSensorData() {
        // <<< POPRAWKA: Pobieraj token za każdym razem >>>
        String jwtToken = getJwtToken();
        if (jwtToken == null) {
            return;
        }

        Request request = new Request.Builder()
                .url(Constants.SENSOR_DATA_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA (Odczyty): " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        processSensorData(jsonResponse); // Zapisuje do TABLE_READINGS
                    } else {
                        Log.w(TAG, String.format(Locale.getDefault(),
                                getString(R.string.log_warn_data_failed), resp.code()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Odczyty): " + e.getMessage());
                }
            }
        });
    }

    /**
     * Zadanie 2: Synchronizuje BRAMKI I CZUJNIKI (NOWA LOGIKA, /api/gateways)
     */
    private void syncGatewayDefinitions() {
        // <<< POPRAWKA: Pobieraj token za każdym razem >>>
        String jwtToken = getJwtToken();
        if (jwtToken == null) {
            return;
        }

        Request request = new Request.Builder()
                .url(Constants.GATEWAYS_ENDPOINT) // Używa nowego endpointu
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA (Bramki): " + e.getMessage());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        Type listType = new TypeToken<List<Gateway>>() {}.getType();
                        List<Gateway> gateways = gson.fromJson(jsonResponse, listType);
                        dbHelper.syncGatewaysAndSensors(gateways);
                        sendDataUpdateBroadcast();
                    } else {
                        Log.w(TAG, String.format(Locale.getDefault(),
                                "Pobieranie bramek nieudane, kod: %d", resp.code()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Bramki): " + e.getMessage());
                }
            }
        });
    }

    /**
     * Zadanie 3: Pobiera status aktualizacji (stara logika)
     */
    private void fetchUpdateStatus() {
        // <<< POPRAWKA: Pobieraj token za każdym razem >>>
        String jwtToken = getJwtToken();
        if (jwtToken == null) return;

        Request request = new Request.Builder()
                .url(Constants.UPDATE_STATUS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA (Update Status): " + e.getMessage());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        processUpdateStatus(resp.body().string());
                    } else {
                        Log.w(TAG, String.format(Locale.getDefault(),
                                getString(R.string.log_warn_update_status_failed), resp.code()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Update Status): " + e.getMessage());
                }
            }
        });
    }

    // ... (metody processUpdateStatus, processSensorData, checkThresholds, sendDataUpdateBroadcast, registerAndroidIp... bez zmian) ...
    private void processUpdateStatus(String json) {
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String status = root.getString(key);
                if ("available".equalsIgnoreCase(status)) {
                    notificationHelper.showUpdateNotification(key, status);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Blad parsowania JSON z /api/update/status: " + e.getMessage());
        }
    }

    private void processSensorData(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray jsonArray = root.getJSONArray("sensors");
            if (jsonArray.length() == 0) return;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject sensorJson = jsonArray.getJSONObject(i);
                // <<< POPRAWKA: Użyj kluczy z nowego DTO >>>
                String gatewayId = sensorJson.getString("gatewayId");
                String sensorId = sensorJson.getString("sensorId");
                String type = sensorJson.getString("type");
                String value = sensorJson.getString("value");
                long timestamp = sensorJson.getLong("timestamp");
                SensorModel sensor = new SensorModel(gatewayId, sensorId, type, value, timestamp);
                dbHelper.addSensorData(sensor);
                checkThresholds(sensor);
            }
            int cleanupDays = cleanupManager.getCleanupDays();
            if (cleanupDays > 0) {
                dbHelper.cleanOldSensorData(cleanupDays);
            }
            sendDataUpdateBroadcast();
        } catch (Exception e) {
            Log.e(TAG, "Nieoczekiwany blad przetwarzania odczytów: " + e.getMessage(), e); // <<< Lepsze logowanie
        }
    }

    private void checkThresholds(SensorModel sensor) {
        if (sensor == null || sensor.type == null) return;

        String type = sensor.type.toLowerCase();
        String doorContactType = getString(R.string.sensor_type_door_contact); // "door_contact"
        String humidityType = getString(R.string.sensor_type_humidity); // "humidity"

        // --- ŚCIEŻKA 1: Sprawdzanie czujników typu 'contact' ---
        if (type.equals(doorContactType) || type.equals("contact")) {
            if (getString(R.string.door_contact_open_value).equals(sensor.value)) { // "1"
                // Przekazujemy typ DOOR, który nie używa liczb
                notificationHelper.showThresholdAlert(sensor, 0, 0, NotificationHelper.ThresholdType.DOOR);
            }
        }
        // --- ŚCIEŻKA 2: Sprawdzanie czujników TYLKO numerycznych ---
        else if (type.equals("temperature") || type.equals(humidityType)) {


            try {
                // 1. Jawna konwersja String -> float (zgodnie z Twoją sugestią)
                float currentValue = Float.parseFloat(sensor.value);
                boolean isHumidity = type.equals(humidityType);

                float defaultMin = isHumidity ? 5.0f : 18.0f;
                float defaultMax = isHumidity ? 30.0f : 22.0f;

                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);

                // 2. Przekazanie bezpiecznych float-ów, a nie String-ów
                if (currentValue < min) {
                    notificationHelper.showThresholdAlert(sensor, currentValue, min, NotificationHelper.ThresholdType.LOW);
                } else if (currentValue > max) {
                    notificationHelper.showThresholdAlert(sensor, currentValue, max, NotificationHelper.ThresholdType.HIGH);
                }
            } catch (NumberFormatException e) {
                // 3. Jeśli konwersja się nie uda (np. "Błąd"), jest łapana i nie ma crasha
                Log.w(TAG, String.format(Locale.getDefault(),
                        getString(R.string.log_error_not_numeric), sensor.value));
            }
        }
    }

    private void sendDataUpdateBroadcast() {
        Intent intent = new Intent(Constants.ACTION_DATA_UPDATED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void registerAndroidIp() {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("password", Constants.SECRET_PASSWORD);
            jsonBody.put("port", Constants.ANDROID_LISTEN_PORT);
        } catch (JSONException e) {
            Log.e(TAG, "Blad tworzenia JSON dla /register/android", e);
            return;
        }
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.REGISTRATION_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Blad rejestracji Android IP: " + e.getMessage());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if(response.isSuccessful()) {
                    Log.d(TAG, "Pomyslnie zarejestrowano IP Androida na serwerze.");
                } else {
                    Log.w(TAG, "Rejestracja IP Androida nieudana, kod: " + response.code());
                }
                response.close();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Serwis klienta VPS: onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Serwis klienta VPS: onDestroy");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}