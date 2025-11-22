package com.testserwera.bazunia.data;

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

import com.testserwera.bazunia.utils.CleanupManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.NotificationHelper;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.ui.LoginActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
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
    private static final int POLLING_INTERVAL_FOLDERS_SECONDS = 60;

    // <<< 1. POPRAWKA: Dodanie brakującej stałej
    private static final String BATTERY_PREFS = "BatteryNotificationPrefs";

    private OkHttpClient httpClient;
    private ScheduledExecutorService executorService;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationHelper notificationHelper;
    private CleanupManager cleanupManager;
    private SharedPreferences authPrefs;
    // <<< 2. POPRAWKA: Dodanie brakującej deklaracji pola
    private SharedPreferences batteryPrefs;
    private Gson gson;
    private SharedPreferences mutePrefs;
    private static final String UPDATE_PREFS = "UpdatePrefs";
    private static final int POLLING_INTERVAL_RETENTION_SECONDS = 300;
    private SharedPreferences retentionPrefs;
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
        // Ta linia jest już poprawna, bo 'batteryPrefs' i 'BATTERY_PREFS' są zadeklarowane
        batteryPrefs = getSharedPreferences(BATTERY_PREFS, Context.MODE_PRIVATE);
        mutePrefs = getSharedPreferences("NotificationMutePrefs", Context.MODE_PRIVATE);
        executorService = Executors.newSingleThreadScheduledExecutor();
        retentionPrefs = getSharedPreferences("RetentionPrefs", Context.MODE_PRIVATE);
        registerAndroidIp();

        executorService.scheduleWithFixedDelay(() -> fetchSensorData(false), 0, POLLING_INTERVAL_READINGS_SECONDS, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(() -> syncGatewayDefinitions(false), 1, POLLING_INTERVAL_GATEWAYS_SECONDS, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(() -> syncFoldersAndFavorites(false), 2, POLLING_INTERVAL_FOLDERS_SECONDS, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::fetchUpdateStatus, 5, POLLING_INTERVAL_GATEWAYS_SECONDS, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::fetchSensorErrorStatus, 10, 60, TimeUnit.SECONDS);
        executorService.scheduleWithFixedDelay(this::checkRetentionStatus, 15, POLLING_INTERVAL_RETENTION_SECONDS, TimeUnit.SECONDS);




    }

    private Notification createNotification() {
        // ... (bez zmian)
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

    private String getJwtToken() {
        // ... (bez zmian)
        String token = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (token == null) {
            Log.e(TAG, getString(R.string.log_error_no_token));
        }
        return token;
    }

    /**
     * Zadanie 1: Pobiera surowe ODCZYTY (stara logika, /data/android)
     */
    private void fetchSensorData(boolean isManual) {
        String jwtToken = getJwtToken();
        if (jwtToken == null) {
            if (isManual) sendSyncStatusBroadcast(false);
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
                if (isManual) sendSyncStatusBroadcast(false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        processSensorData(jsonResponse); // To wysyła ACTION_DATA_UPDATED
                        if (isManual) sendSyncStatusBroadcast(true);
                    } else {
                        Log.w(TAG, String.format(Locale.getDefault(),
                                getString(R.string.log_warn_data_failed), resp.code()));
                        if (isManual) sendSyncStatusBroadcast(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Odczyty): " + e.getMessage());
                    if (isManual) sendSyncStatusBroadcast(false);
                }
            }
        });
    }


    /**
     * Zadanie 2: Synchronizuje BRAMKI I CZUJNIKI (NOWA LOGIKA, /api/gateways)
     */
    private void syncGatewayDefinitions(boolean isManual) {
        String jwtToken = getJwtToken();
        if (jwtToken == null) {
            if (isManual) sendSyncStatusBroadcast(false);
            return;
        }
        Request request = new Request.Builder()
                .url(Constants.GATEWAYS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA (Bramki): " + e.getMessage());
                if (isManual) sendSyncStatusBroadcast(false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();
                        Type listType = new TypeToken<List<Gateway>>() {
                        }.getType();
                        List<Gateway> gateways = gson.fromJson(jsonResponse, listType);

                        // Ta sekcja jest już poprawna
                        if (gateways != null && !gateways.isEmpty()) {
                            checkAllBatteryThresholds(gateways);
                        }

                        dbHelper.syncGatewaysAndSensors(gateways);
                        sendDataUpdateBroadcast();
                        if (isManual) sendSyncStatusBroadcast(true);
                    } else {
                        Log.w(TAG, String.format(Locale.getDefault(),
                                "Pobieranie bramek nieudane, kod: %d", resp.code()));
                        if (isManual) sendSyncStatusBroadcast(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Bramki): " + e.getMessage());
                    if (isManual) sendSyncStatusBroadcast(false);
                }
            }
        });
    }


    /**
     * Zadanie 3: Synchronizuje Foldery i Ulubione (NOWA LOGIKA)
     */
    private void syncFoldersAndFavorites(boolean isManual) {
        String jwtToken = getJwtToken();
        if (jwtToken == null) {
            if (isManual) sendSyncStatusBroadcast(false);
            return;
        }

        try {
            // --- 1. Pobierz Foldery ---
            List<Folder> folders;
            Request foldersRequest = new Request.Builder()
                    .url(Constants.FOLDERS_ENDPOINT)
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .get().build();

            // Użycie try-with-resources zamyka Response automatycznie
            try (Response foldersResponse = httpClient.newCall(foldersRequest).execute()) {
                if (!foldersResponse.isSuccessful()) {
                    throw new IOException("Błąd pobierania folderów: " + foldersResponse.code());
                }
                if (foldersResponse.body() == null) {
                    throw new IOException("Pusta odpowiedź serwera (brak body) przy pobieraniu folderów");
                }

                String foldersJson = foldersResponse.body().string();
                Type folderListType = new TypeToken<List<Folder>>() {}.getType();
                folders = gson.fromJson(foldersJson, folderListType);
            }

            // --- 2. Pobierz Ulubione Bramki ---
            List<Gateway> favoriteGateways;
            Request favGatewaysRequest = new Request.Builder()
                    .url(Constants.FAVORITE_GATEWAYS_ENDPOINT)
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .get().build();

            try (Response favGatewaysResponse = httpClient.newCall(favGatewaysRequest).execute()) {
                if (!favGatewaysResponse.isSuccessful()) {
                    throw new IOException("Błąd pobierania ulubionych bramek: " + favGatewaysResponse.code());
                }
                if (favGatewaysResponse.body() == null) {
                    throw new IOException("Pusta odpowiedź serwera przy pobieraniu ulubionych bramek");
                }

                String favGatewaysJson = favGatewaysResponse.body().string();
                Type gatewayListType = new TypeToken<List<Gateway>>() {}.getType();
                favoriteGateways = gson.fromJson(favGatewaysJson, gatewayListType);
            }

            // --- 3. Pobierz Ulubione Czujniki ---
            List<Sensor> favoriteSensors;
            Request favSensorsRequest = new Request.Builder()
                    .url(Constants.FAVORITE_SENSORS_ENDPOINT)
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .get().build();

            try (Response favSensorsResponse = httpClient.newCall(favSensorsRequest).execute()) {
                if (!favSensorsResponse.isSuccessful()) {
                    throw new IOException("Błąd pobierania ulubionych czujników: " + favSensorsResponse.code());
                }
                if (favSensorsResponse.body() == null) {
                    throw new IOException("Pusta odpowiedź serwera przy pobieraniu ulubionych czujników");
                }

                String favSensorsJson = favSensorsResponse.body().string();
                Type sensorListType = new TypeToken<List<Sensor>>() {}.getType();
                favoriteSensors = gson.fromJson(favSensorsJson, sensorListType);
            }

            // --- 4. Zapisz wszystko do bazy w jednej transakcji ---
            // Zmienne folders, favoriteGateways, favoriteSensors są wypełnione powyżej
            dbHelper.syncFoldersAndFavorites(folders, favoriteGateways, favoriteSensors);

            // 5. Powiadom UI (DataActivity), że dane się zmieniły
            sendDataUpdateBroadcast();
            if (isManual) sendSyncStatusBroadcast(true);

        } catch (Exception e) {
            Log.e(TAG, "KRYTYCZNY BŁĄD w syncFoldersAndFavorites: " + e.getMessage());
            if (isManual) sendSyncStatusBroadcast(false);
        }
    }


    /**
     * Zadanie 4: Pobiera status aktualizacji (NOWA LOGIKA)
     * Odpytuje /api/my-updates i sprawdza lokalne wyciszenie.
     */
    private void fetchUpdateStatus() {
        String jwtToken = getJwtToken();
        if (jwtToken == null) return;

        Request request = new Request.Builder()
                .url(Constants.MY_UPDATES_ENDPOINT) //
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA (Update List): " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String json = resp.body().string();
                        processUpdateList(json);
                    } else {
                        Log.w(TAG, "Update check failed: " + resp.code());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing updates: " + e.getMessage());
                }
            }
        });
    }

    private void processUpdateList(String json) {
        try {
            // Oczekujemy tablicy JSON obiektów ClientUpdateResponse
            JSONArray updatesArray = new JSONArray(json);
            SharedPreferences updatePrefs = getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();

            for (int i = 0; i < updatesArray.length(); i++) {
                JSONObject update = updatesArray.getJSONObject(i);

                long assignmentId = update.getLong("assignmentId");
                String title = update.getString("title");
                String urgency = update.getString("urgency"); // REQUIRED, OPTIONAL, CUSTOM
                String version = update.getString("version");

                // Sprawdź, czy ta aktualizacja nie jest wyciszona lokalnie (Snooze 5 min)
                long snoozeUntil = updatePrefs.getLong("snooze_" + assignmentId, 0);
                if (now < snoozeUntil) {
                    Log.d(TAG, "Aktualizacja " + assignmentId + " jest wyciszona do " + new java.util.Date(snoozeUntil));
                    continue;
                }

                // Pobierz deferCount (ilość odroczeń z backendu, jeśli jest w JSON,
                // jeśli nie ma - przyjmij 0. Backend w AssignmentDto to wysyła, w ClientResponse może nie być,
                // ale załóżmy że dodaliśmy to do DTO backendu lub klient musi śledzić.
                // Wg backendu wysłanego wcześniej: ClientUpdateResponse nie ma pola deferCount,
                // ale logika "tylko raz" jest walidowana przez backend.
                // Tutaj po prostu wyświetlamy powiadomienie).

                // Wyświetl powiadomienie
                notificationHelper.showUpdateNotification(assignmentId, title, version, urgency);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON Error updates: " + e.getMessage());
        }
    }

    // --- Reszta metod ---

    private void processSensorData(String json) {
        // ... (bez zmian)
        try {
            JSONObject root = new JSONObject(json);
            JSONArray jsonArray = root.getJSONArray("sensors");
            if (jsonArray.length() == 0) return;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject sensorJson = jsonArray.getJSONObject(i);
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
            int cleanupSize = cleanupManager.getCleanupSize();
            if (cleanupSize > 0) {
                // Zakładam, że dodałeś metodę cleanSensorDataBySize do DatabaseHelper w poprzednim kroku
                dbHelper.cleanSensorDataBySize(cleanupSize);
            }
            sendDataUpdateBroadcast();
        } catch (Exception e) {
            Log.e(TAG, "Nieoczekiwany blad przetwarzania odczytów: " + e.getMessage(), e);
        }
    }

    private void checkThresholds(SensorModel sensor) {
        // ... (bez zmian)
        if (sensor == null || sensor.type == null) return;
        // Sprawdź, czy alerty wartości (progi) są wyciszone dla tego sensora
        String muteKey = "thresh_sensor_" + sensor.sensorId;
        if (mutePrefs.getBoolean(muteKey, false)) {
            // Log.d(TAG, "Alerty wartości dla " + sensor.sensorId + " są wyciszone.");
            return; // Zakończ, nie wysyłaj powiadomienia
        }
        String type = sensor.type.toLowerCase();
        String doorContactType = getString(R.string.sensor_type_door_contact);
        String humidityType = getString(R.string.sensor_type_humidity);
        if (type.equals(doorContactType) || type.equals("contact")) {
            if (getString(R.string.door_contact_open_value).equals(sensor.value)) {
                notificationHelper.showThresholdAlert(sensor, 0, 0, NotificationHelper.ThresholdType.DOOR);
            }
        } else if (type.equals("temperature") || type.equals(humidityType)) {
            try {
                float currentValue = Float.parseFloat(sensor.value);
                boolean isHumidity = type.equals(humidityType);
                float defaultMin = isHumidity ? 5.0f : 18.0f;
                float defaultMax = isHumidity ? 30.0f : 22.0f;
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);
                if (currentValue < min) {
                    notificationHelper.showThresholdAlert(sensor, currentValue, min, NotificationHelper.ThresholdType.LOW);
                } else if (currentValue > max) {
                    notificationHelper.showThresholdAlert(sensor, currentValue, max, NotificationHelper.ThresholdType.HIGH);
                }
            } catch (NumberFormatException e) {
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
        // ... (bez zmian)
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
                if (response.isSuccessful()) {
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

        if (intent != null) {
            // Blok dla pełnej synchronizacji (bez zmian)
            if (intent.getBooleanExtra("FORCE_SYNC_NOW", false)) {
                boolean isSilent = intent.getBooleanExtra("IS_SILENT", false);
                boolean isManual = !isSilent;

                if (isManual) {
                    Log.d(TAG, "Wymuszono natychmiastową synchronizację DEFINICJI (ręcznie)!");
                } else {
                    Log.d(TAG, "Wymuszono natychmiastową synchronizację DEFINICJI (cicho)!");
                }

                if (executorService != null && !executorService.isShutdown()) {
                    executorService.submit(() -> syncGatewayDefinitions(isManual));
                    executorService.submit(() -> syncFoldersAndFavorites(isManual));
                }
            }

            // Blok dla odczytów (bez zmian)
            if (intent.getBooleanExtra("FORCE_READINGS_NOW", false)) {
                Log.d(TAG, "Wymuszono natychmiastową synchronizację ODCZYTÓW (ręcznie)!");
                if (executorService != null && !executorService.isShutdown()) {
                    executorService.submit(() -> fetchSensorData(true));
                }
            }

            // 🔽🔽🔽 NOWY BLOK TYLKO DLA BATERII 🔽🔽🔽
            if (intent.getBooleanExtra("FORCE_BATTERY_CHECK_NOW", false)) {
                Log.d(TAG, "Wymuszono natychmiastową synchronizację BATERII (ręcznie)!");
                if (executorService != null && !executorService.isShutdown()) {
                    // Uruchamiamy tylko `syncGatewayDefinitions`, bo tam są dane o baterii.
                    // Oznaczamy jako "manual" (true), aby serwis wysłał Toasta (przez ACTION_SYNC_STATUS)
                    executorService.submit(() -> syncGatewayDefinitions(true));
                }
            }
            // 🔼🔼🔼 KONIEC NOWEGO BLOKU 🔼🔼🔼
        }
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

    private void sendSyncStatusBroadcast(boolean success) {
        Intent intent = new Intent(Constants.ACTION_SYNC_STATUS);
        intent.putExtra("SYNC_SUCCESS", success);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Iteruje po wszystkich bramkach i czujnikach, aby sprawdzić stan baterii.
     */
    private void checkAllBatteryThresholds(List<Gateway> gateways) {
        for (Gateway gateway : gateways) {
            if (gateway.getSensors() != null) {
                for (Sensor sensor : gateway.getSensors()) {
                    // Sprawdź tylko sensory, które raportują poziom baterii
                    if (sensor.getBatteryLevel() != null && sensor.getBatteryLevel() > 0) {
                        checkSensorBattery(sensor, gateway.getName());
                    }
                }
            }
        }
    }

    /**
     * Sprawdza poziom baterii dla pojedynczego czujnika i wysyła powiadomienie,
     * jeśli przekroczono nowy próg (i nie powiadomiono o tym wcześniej).
     */
    private void checkSensorBattery(Sensor sensor, String gatewayName) {
        int newLevel = sensor.getBatteryLevel();
        // Sprawdź, czy alerty baterii są wyciszone dla tego sensora
        String muteKey = "batt_sensor_" + sensor.getId();
        if (mutePrefs.getBoolean(muteKey, false)) {
            // Log.d(TAG, "Alerty baterii dla " + sensor.getId() + " są wyciszone.");
            return; // Zakończ, nie wysyłaj powiadomienia
        }
        String prefKey = "battery_notified_" + sensor.getId();

        // Ta linia jest już poprawna, bo 'batteryPrefs' jest zadeklarowane
        int lastNotifiedLevel = batteryPrefs.getInt(prefKey, 100);

        SharedPreferences.Editor editor = batteryPrefs.edit();

        // Logika progów: powiadamiaj tylko przy *przejściu* przez próg w dół.

        // PRÓG 1: Krytyczny (1%)
        if (newLevel <= 1 && lastNotifiedLevel > 1) {
            notificationHelper.showBatteryAlert(sensor.getName(), gatewayName, newLevel);
            editor.putInt(prefKey, 1).apply();
        }
        // PRÓG 2: Bardzo niski (10%)
        else if (newLevel <= 10 && lastNotifiedLevel > 10) {
            notificationHelper.showBatteryAlert(sensor.getName(), gatewayName, newLevel);
            editor.putInt(prefKey, 10).apply();
        }
        // PRÓG 3: Niski (20%)
        else if (newLevel <= 20 && lastNotifiedLevel > 20) {
            notificationHelper.showBatteryAlert(sensor.getName(), gatewayName, newLevel);
            editor.putInt(prefKey, 20).apply();
        }
        // RESET: Jeśli bateria została naładowana/wymieniona (np. > 20%)
        else if (newLevel > 20 && lastNotifiedLevel <= 20) {
            // Resetuj stan, aby przyszłe powiadomienia mogły być wysłane
            editor.putInt(prefKey, 100).apply();
        }
    }
    /**
     * NOWE ZADANIE: Pobiera statusy błędów (np. offline) dla czujników/bramek
     */
    private void fetchSensorErrorStatus() {
        String jwtToken = getJwtToken();
        if (jwtToken == null) {
            Log.w(TAG, "Brak tokena, pomijam sprawdzanie statusu błędów.");
            return;
        }

        // Upewnij się, że dodałeś ten endpoint do pliku Constants.java
        Request request = new Request.Builder()
                .url(Constants.SENSOR_STATUS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD POBIERANIA (Status Błędów): " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String jsonResponse = resp.body().string();

                        // Używamy GSON, tak jak w syncGatewayDefinitions
                        // Będziemy potrzebować nowej klasy SensorStatusErrorDto
                        Type listType = new TypeToken<List<SensorStatusErrorDto>>() {}.getType();
                        List<SensorStatusErrorDto> errors = gson.fromJson(jsonResponse, listType);

                        if (errors != null && !errors.isEmpty()) {
                            Log.w(TAG, "Wykryto " + errors.size() + " błędów statusu czujników.");
                            for (SensorStatusErrorDto error : errors) {

                                // Użyj nazwy encji (np. "Bramka Kuchnia") jeśli jest dostępna,
                                // w przeciwnym razie użyj ID (np. "sensor_123")
                                String entityIdentifier;
                                if (error.entityName != null && !error.entityName.isEmpty()) {
                                    entityIdentifier = error.entityName;
                                } else {
                                    entityIdentifier = error.entityType + "_" + error.entityId;
                                }

                                // Wywołaj nową metodę z NotificationHelpera
                                notificationHelper.showSensorCommsError(entityIdentifier, error.readableMessage);
                            }
                        } else {
                            // To jest normalne, oznacza że nie ma błędów
                            Log.d(TAG, "Brak błędów statusu czujników.");
                        }
                    } else {
                        Log.w(TAG, "Pobieranie statusu błędów nieudane, kod: " + resp.code());
                    }
                } catch (Exception e) {
                    // Np. błąd parsowania JSON
                    Log.e(TAG, "KRYTYCZNY BLAD w onResponse (Status Błędów): " + e.getMessage());
                }
            }
        });
    }
    // ⭐️⭐️⭐️ ZADANIE 6: RETENCJA (To czego brakowało) ⭐️⭐️⭐️
    private void checkRetentionStatus() {
        String jwtToken = getJwtToken();
        if (jwtToken == null) return;

        // UWAGA: Upewnij się, że w Constants.java masz VPS_SERVER_IP
        // Jeśli nie, użyj swojego adresu IP na sztywno do testów
        String url = Constants.VPS_SERVER_IP + "/api/retention/status";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { /* ignoruj błędy sieci */ }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        JSONObject json = new JSONObject(resp.body().string());
                        String serverStatus = json.optString("status", "NONE");

                        // Odczytaj ostatnio znany status
                        String localStatus = retentionPrefs.getString("last_status", "NONE");

                        // Jeśli status się zmienił i jest decyzją końcową
                        if (!serverStatus.equals(localStatus)) {
                            if ("ACCEPTED".equals(serverStatus)) {
                                notificationHelper.showRetentionStatusNotification(true);
                            } else if ("REJECTED".equals(serverStatus)) {
                                notificationHelper.showRetentionStatusNotification(false);
                            }
                            // Zapisz, żeby nie spamować powiadomieniami
                            retentionPrefs.edit().putString("last_status", serverStatus).apply();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking retention: " + e.getMessage());
                }
            }
        });
    }
}