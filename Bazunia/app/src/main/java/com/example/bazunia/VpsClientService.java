package com.example.bazunia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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

/**
 * Serwis laczacy sie z VPS (REST) w trybie Polling (Pobierania):
 * 1. Regularnie pobiera dane czujników z VPS (GET /data/android).
 * 2. Zapisuje dane do bazy i sprawdza progi (bez potrzeby otwierania portu na routerze).
 * 3. Uruchamia się jako Foreground Service.
 */
public class VpsClientService extends Service {

    private static final String TAG = "VpsClientService";
    private static final String CHANNEL_ID = "VpsClientServiceChannel";
    private static final int NOTIFICATION_ID = 101;
    private static final int POLLING_INTERVAL_SECONDS = 5; // Czas odpytywania VPS

    private OkHttpClient httpClient;
    private ScheduledExecutorService executorService;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationHelper notificationHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Serwis klienta VPS: onCreate");

        // Uruchomienie jako Foreground Service, wymagane dla Androida 8.0+ (Oreo)
        startForeground(NOTIFICATION_ID, createNotification());

        // Inicjalizacja komponentów
        httpClient = new OkHttpClient();
        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);
        notificationHelper = new NotificationHelper(this);

        // Zaplanowanie cyklicznych zadań
        executorService = Executors.newSingleThreadScheduledExecutor();

        // 1. Rejestracja IP Androida (wykonana jednorazowo przy starcie)
        registerAndroidIp();

        // 2. Cykliczne pobieranie danych (Polling)
        executorService.scheduleAtFixedRate(this::fetchSensorData, 0, POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Tworzy powiadomienie dla Foreground Service.
     */
    private Notification createNotification() {
        // Wymagane utworzenie kanału powiadomień dla Androida 8.0+ (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Serwis Klienta VPS",
                    NotificationManager.IMPORTANCE_LOW // Niska ważność, ponieważ to tło
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitorowanie czujników")
                .setContentText("Serwis aktywnie pobiera dane z VPS.")
                .setSmallIcon(R.drawable.ic_notification_alert)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    /**
     * Cyklicznie pobiera dane czujników z VPS w trybie Polling.
     */
    private void fetchSensorData() {
        // Długie operacje I/O (sieciowe) muszą być poza wątkiem głównym (tutaj jest ScheduledExecutorService)
        Request request = new Request.Builder()
                .url(Constants.SENSOR_DATA_ENDPOINT) // GET do /data/android
                .addHeader("Password", Constants.SECRET_PASSWORD) // Hasło w nagłówku
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "BLAD POBIERANIA danych z VPS: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        Log.d(TAG, "Odebrano dane: " + jsonResponse);
                        processSensorData(jsonResponse);
                    } else {
                        Log.w(TAG, "OSTRZEZENIE: Pobieranie danych nieudane, kod: " + resp.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Parsuje odebrany JSON, zapisuje do bazy danych i sprawdza progi.
     * @param json String JSON z danymi czujników.
     */
    private void processSensorData(String json) {
        try {
            JSONObject root = new JSONObject(json);
            // Oczekujemy, że dane są zawarte w tablicy pod kluczem "sensors"
            JSONArray jsonArray = root.getJSONArray("sensors");

            if (jsonArray.length() == 0) {
                Log.d(TAG, "Odebrano pusta tablice danych.");
                return;
            }

            List<SensorModel> latestSensorData = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject sensorJson = jsonArray.getJSONObject(i);

                // ZMIANA KLUCZY Z POWROTEM NA 'snake_case', aby pasowały do loga
                String gatewayId = sensorJson.getString("gateway_id"); // Zmieniono z 'gatewayId'
                String sensorId = sensorJson.getString("sensor_id");     // Zmieniono z 'sensorId'
                String type = sensorJson.getString("type");
                String value = sensorJson.getString("value");
                long timestamp = sensorJson.getLong("timestamp");

                // UWAGA: Konstruktor SensorModel używa camelCase, co jest poprawne.
                SensorModel sensor = new SensorModel(gatewayId, sensorId, type, value, timestamp);
                latestSensorData.add(sensor);

                // 1. Zapis do bazy danych (poprawiony w poprzednim kroku, akceptuje SensorModel)
                dbHelper.addSensorData(sensor);

                // 2. Sprawdzenie progów i alerty
                checkThresholds(sensor);
            }

            // Wysłanie Broadcastu do Aktywności, aby odświeżyć UI
            sendDataUpdateBroadcast();

        } catch (JSONException e) {
            // W tym miejscu będzie błąd, jeśli klucze JSON nie pasują
            Log.e(TAG, "Blad parsowania JSON z VPS: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Nieoczekiwany blad przetwarzania danych: " + e.getMessage());
        }
    }

    /**
     * Sprawdza, czy wartość czujnika przekracza zdefiniowane progi.
     */
    private void checkThresholds(SensorModel sensor) {
        // Tylko dla czujników numerycznych (temperatura, wilgotność)
        if ("temperature".equalsIgnoreCase(sensor.type) || "humidity".equalsIgnoreCase(sensor.type)) {
            try {
                float currentValue = Float.parseFloat(sensor.value);

                // Ustalenie wartości domyślnych na podstawie typu
                boolean isHumidity = "humidity".equalsIgnoreCase(sensor.type);
                float defaultMin = isHumidity ? 5.0f : 18.0f;
                float defaultMax = isHumidity ? 30.0f : 22.0f;

                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);

                String alertTitle = String.format(Locale.getDefault(), "Alert: %s %s", sensor.type, sensor.sensorId);
                String alertMessage = null;

                if (currentValue < min) {
                    alertMessage = String.format(Locale.getDefault(),
                            "Wartość %s jest za niska: %.1f. Próg min: %.1f.",
                            sensor.type, currentValue, min);
                } else if (currentValue > max) {
                    alertMessage = String.format(Locale.getDefault(),
                            "Wartość %s jest za wysoka: %.1f. Próg max: %.1f.",
                            sensor.type, currentValue, max);
                }

                if (alertMessage != null) {
                    // Używamy hasha (identyfikatora) czujnika jako unikalnego ID dla powiadomienia.
                    int notificationId = (sensor.gatewayId + sensor.sensorId).hashCode();
                    notificationHelper.showNotification(alertTitle, alertMessage, notificationId);
                    Log.w(TAG, alertMessage);
                }

            } catch (NumberFormatException e) {
                Log.w(TAG, "Wartość czujnika nie jest numeryczna: " + sensor.value);
            }
        } else if ("door_contact".equalsIgnoreCase(sensor.type)) {
            // Logika dla czujnika otwarcia drzwi
            if ("1".equals(sensor.value)) {
                String alertTitle = "Alert: Drzwi/Okna";
                String alertMessage = String.format("Czujnik %s (Bramka %s) ZGŁASZA OTWARTY STAN!",
                        sensor.sensorId, sensor.gatewayId);
                int notificationId = (sensor.gatewayId + sensor.sensorId).hashCode();
                notificationHelper.showNotification(alertTitle, alertMessage, notificationId);
                Log.w(TAG, alertMessage);
            }
        }
    }


    /**
     * Wysyła lokalny sygnał do Aktywności, aby odświeżyć dane na ekranie.
     */
    private void sendDataUpdateBroadcast() {
        Intent intent = new Intent(Constants.ACTION_DATA_UPDATED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Jednorazowo rejestruje IP Androida na VPS.
     */
    private void registerAndroidIp() {
        // Utworzenie JSONa z hasłem i portem nasłuchiwania (choć nasłuchiwanie jest teraz nieaktywne, dane są potrzebne do rejestracji)
        String json = String.format(Locale.getDefault(),
                "{\"password\":\"%s\",\"port\":%d}",
                Constants.SECRET_PASSWORD,
                Constants.ANDROID_LISTEN_PORT);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        // Używamy HTTPS z Constants.REGISTRATION_ENDPOINT
        Request request = new Request.Builder()
                .url(Constants.REGISTRATION_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "BLAD POST rejestracji do VPS: " + e.getMessage());
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.i(TAG, "SUKCES: IP Androida zarejestrowane: " + Constants.ANDROID_LISTEN_PORT);
                } else {
                    Log.w(TAG, "OSTRZEZENIE: Rejestracja nieudana, kod: " + response.code());
                }
            }
        });
    }

    // --- ZARZĄDZANIE SERWISEM ---

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Kontynuuj działanie, nawet jeśli aplikacja zostanie zabita.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            // Zatrzymanie wątku cyklicznego pobierania danych
            executorService.shutdownNow();
        }
        Log.d(TAG, "Serwis klienta VPS: onDestroy (zatrzymano Polling)...");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}