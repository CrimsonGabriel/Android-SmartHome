// 💾 VpsClientService.java (PEŁNA, POPRAWIONA WERSJA)
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.bazunia.utils.CleanupManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.utils.NotificationHelper;
import com.example.bazunia.R;
import com.example.bazunia.ui.LoginActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator; // <-- DODANY IMPORT
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
    private static final int POLLING_INTERVAL_SECONDS = 5;

    private OkHttpClient httpClient;
    private ScheduledExecutorService executorService;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationHelper notificationHelper;
    private CleanupManager cleanupManager;
    private SharedPreferences authPrefs;

    // ⭐️ POPRAWKA: Dodanie deklaracji pollingCounter ⭐️
    private int pollingCounter = 0;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Serwis klienta VPS: onCreate");
        startForeground(NOTIFICATION_ID, createNotification());

        httpClient = new OkHttpClient();
        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);
        notificationHelper = new NotificationHelper(this);
        cleanupManager = new CleanupManager(this);
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);

        executorService = Executors.newSingleThreadScheduledExecutor();

        registerAndroidIp();
        executorService.scheduleWithFixedDelay(this::fetchSensorData, 0, POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS);
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

    private void fetchSensorData() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Log.e(TAG, getString(R.string.log_error_no_token));
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
                Log.e(TAG, "BLAD POBIERANIA danych z VPS: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        Log.d(TAG, "Odebrano dane: " + jsonResponse);
                        processSensorData(jsonResponse);
                    } else {
                        if (resp.code() == 401 || resp.code() == 403) {
                            Log.e(TAG, String.format(Locale.getDefault(),
                                    getString(R.string.log_warn_token_rejected), resp.code()));
                            authPrefs.edit().remove(LoginActivity.KEY_JWT_TOKEN).apply();
                        } else {
                            Log.w(TAG, String.format(Locale.getDefault(),
                                    getString(R.string.log_warn_data_failed), resp.code()));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse: " + e.getMessage());
                }
            }
        });

        // ⭐️ POPRAWKA LOGIKI POLLINGU I AKTUALIZACJI W GŁÓWNYM WĄTKU EXECUTION ⭐️
        pollingCounter++;
        if (pollingCounter % 5 == 0) {
            fetchUpdateStatus();
            pollingCounter = 0;
        }
    }

    private void fetchUpdateStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Log.e(TAG, getString(R.string.log_error_no_token));
            return;
        }

        Request request = new Request.Builder()
                .url(Constants.UPDATE_STATUS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA statusu aktualizacji: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        processUpdateStatus(jsonResponse);
                    } else {
                        Log.w(TAG, String.format(Locale.getDefault(),
                                getString(R.string.log_warn_update_status_failed), resp.code()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Aktualizacje): " + e.getMessage());
                }
            }
        });
    }

    private void processUpdateStatus(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);

            // ⭐️ POPRAWKA: Użycie Iteratora do poprawnej iteracji po kluczach JSON ⭐️
            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                String status = jsonObject.getString(key);

                if ("REQUIRED".equals(status) || "OPTIONAL".equals(status)) {

                    String title = getString(R.string.notification_update_title);
                    String message = String.format(Locale.getDefault(),
                            getString(R.string.notification_update_available), key, status);

                    int notificationId = (key + "UPDATE").hashCode();

                    notificationHelper.showNotificationWithAction(title, message, notificationId, key);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Blad parsowania JSON statusu aktualizacji: " + e.getMessage());
        }
    }

    private void processSensorData(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray jsonArray = root.getJSONArray("sensors");
            if (jsonArray.length() == 0) return;

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject sensorJson = jsonArray.getJSONObject(i);

                String gatewayId = sensorJson.optString("gatewayId", "Brak GW");
                String sensorId = sensorJson.optString("sensorId", "Brak ID");

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
        } catch (JSONException e) {
            Log.e(TAG, "Blad parsowania JSON z VPS: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Nieoczekiwany blad przetwarzania danych: " + e.getMessage());
        }
    }

    private void checkThresholds(SensorModel sensor) {
        if ("temperature".equalsIgnoreCase(sensor.type) || "humidity".equalsIgnoreCase(sensor.type)) {
            try {
                float currentValue = Float.parseFloat(sensor.value);
                boolean isHumidity = "humidity".equalsIgnoreCase(sensor.type);
                float defaultMin = isHumidity ? 5.0f : 18.0f;
                float defaultMax = isHumidity ? 30.0f : 22.0f;
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);
                String alertTitle = String.format(Locale.getDefault(),
                        getString(R.string.alert_title_temp_humidity), sensor.type, sensor.sensorId);
                String alertMessage = null;
                if (currentValue < min) {
                    alertMessage = String.format(Locale.getDefault(),
                            getString(R.string.alert_msg_too_low), sensor.type, currentValue, min);
                } else if (currentValue > max) {
                    alertMessage = String.format(Locale.getDefault(),
                            getString(R.string.alert_msg_too_high), sensor.type, currentValue, max);
                }
                if (alertMessage != null) {
                    int notificationId = (sensor.gatewayId + sensor.sensorId).hashCode();
                    notificationHelper.showNotification(alertTitle, alertMessage, notificationId);
                    Log.w(TAG, alertMessage);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, String.format(Locale.getDefault(),
                        getString(R.string.log_error_not_numeric), sensor.value));
            }
        } else if ("door_contact".equalsIgnoreCase(sensor.type)) {
            if ("1".equals(sensor.value)) {
                String alertTitle = getString(R.string.alert_title_door_window);
                String alertMessage = String.format(Locale.getDefault(),
                        getString(R.string.alert_msg_door_open), sensor.sensorId, sensor.gatewayId);
                int notificationId = (sensor.gatewayId + sensor.sensorId).hashCode();
                notificationHelper.showNotification(alertTitle, alertMessage, notificationId);
                Log.w(TAG, alertMessage);
            }
        }
    }

    private void sendDataUpdateBroadcast() {
        Intent intent = new Intent(Constants.ACTION_DATA_UPDATED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void registerAndroidIp() {
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

    public static void requestGatewayHistoryDeletion(Context context) {
        SharedPreferences authPrefs = context.getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Toast.makeText(context, context.getString(R.string.toast_error_not_logged_in), Toast.LENGTH_LONG).show();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Constants.DELETE_GATEWAY_HISTORY_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(RequestBody.create(new byte[0]))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD ŻĄDANIA usunięcia historii z VPS: " + e.getMessage());
                new android.os.Handler(context.getMainLooper()).post(() ->
                        Toast.makeText(context, context.getString(R.string.toast_error_deletion_failed), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Log.i(TAG, "SUKCES: Historia danych na VPS usunięta. Body: " + responseBody);
                    new android.os.Handler(context.getMainLooper()).post(() ->
                            Toast.makeText(context, context.getString(R.string.toast_success_gateway_history_deleted), Toast.LENGTH_LONG).show());
                } else {
                    Log.w(TAG, String.format(Locale.getDefault(),
                            context.getString(R.string.log_warn_deletion_failed), response.code(), responseBody));
                    new android.os.Handler(context.getMainLooper()).post(() ->
                            Toast.makeText(context, context.getString(R.string.toast_error_deletion_failed_server), Toast.LENGTH_LONG).show());
                }
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