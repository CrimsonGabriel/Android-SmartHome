package com.example.bazunia.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.example.bazunia.utils.LocaleManager;
import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.data.DatabaseHelper;
import com.example.bazunia.R;
// ⭐️ ZMIANA 1: Import modelu Sensor (będzie potrzebny)
import com.example.bazunia.data.Sensor;
import com.example.bazunia.data.SensorModel;
import com.example.bazunia.data.ThresholdManager;
import com.example.bazunia.data.VpsClientService;
import com.google.android.material.button.MaterialButton;
// ⭐️ ZMIANA 2: Import SwitchMaterial
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SensorDetailActivity extends AppCompatActivity {

    private static final int HISTORY_LIMIT = 10;
    private static final String TAG = "SensorDetailActivity"; // Dodano TAG do logowania

    private TextView textSensorDetails, textThresholdMin, textThresholdMax;
    private SeekBar seekBarThresholdMin, seekBarThresholdMax;
    private LinearLayout thresholdContainer;
    private ListView listSensorHistory;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;

    private long gatewayIdLong;
    private long sensorIdLong;
    private String gatewayIdString, sensorIdString;
    private String currentSensorType = "";

    private AppearanceManager appearanceManager;
    private String currentTextScale;
    private String currentButtonScale;

    private ImageButton btnFavorite;
    private boolean isFavorite = false;
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;
    private BroadcastReceiver syncStatusReceiver;
    private long lastSyncToastTime = 0;
    private com.google.android.material.textfield.TextInputEditText editSensorInterval;
    private MaterialButton btnSaveInterval;
    private LinearLayout intervalContainer;

    // ⭐️ ZMIANA 3: Dodanie nowych pól dla przełącznika
    private SwitchMaterial switchReporting;
    private LinearLayout reportingContainer;

    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constants.ACTION_DATA_UPDATED.equals(intent.getAction())) {
                loadLatestDataAndHistory();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ... (inicjalizacja appearanceManager, super.onCreate, setContentView)
        appearanceManager = new AppearanceManager(this);
        currentTextScale = appearanceManager.getTextScale();
        currentButtonScale = appearanceManager.getButtonScale();
        appearanceManager.applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_detail);

        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);
        httpClient = new OkHttpClient();
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);

        // --- Wyszukiwanie widoków ---
        TextView textSensorTitle = findViewById(R.id.textSensorTitle);
        textSensorDetails = findViewById(R.id.textSensorDetails);
        seekBarThresholdMin = findViewById(R.id.seekBarThresholdMin);
        textThresholdMin = findViewById(R.id.textThresholdMin);
        seekBarThresholdMax = findViewById(R.id.seekBarThresholdMax);
        textThresholdMax = findViewById(R.id.textThresholdMax);
        thresholdContainer = findViewById(R.id.thresholdContainer);
        listSensorHistory = findViewById(R.id.listSensorHistory);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnBack = findViewById(R.id.btnBackSensorDetail);
        intervalContainer = findViewById(R.id.intervalContainer);
        editSensorInterval = findViewById(R.id.editSensorInterval);
        btnSaveInterval = findViewById(R.id.btnSaveInterval);
        btnFavorite = findViewById(R.id.btnFavorite);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);

        // ⭐️ ZMIANA 4: Wyszukiwanie nowych widoków
        switchReporting = findViewById(R.id.switchReporting);
        reportingContainer = findViewById(R.id.reportingContainer);

        // --- Skalowanie ---
        appearanceManager.applyIconScale(btnSettings);
        appearanceManager.applyIconScale(btnBack);

        // --- Listenery ---
        btnRefresh.setOnClickListener(v -> forceReadingsSync());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnBack.setOnClickListener(v -> finish());
        btnFavorite.setOnClickListener(v -> toggleFavoriteStatus());
        btnSaveInterval.setOnClickListener(v -> saveSensorInterval());

        // ⭐️ ZMIANA 5: Dodanie listenera dla przełącznika
        // Używamy setOnClickListener, aby ręcznie kontrolować stan (zapobiega "mruganiu")
        switchReporting.setOnClickListener(v -> {
            // Natychmiast wyłącz przełącznik, aby pokazać, że trwa operacja
            switchReporting.setEnabled(false);
            // Wywołaj metodę API
            toggleReportingStatus();
        });

        // --- Pobieranie Intent ---
        // ... (reszta onCreate bez zmian)
        Intent intent = getIntent();
        gatewayIdLong = intent.getLongExtra("GATEWAY_ID_LONG", -1);
        sensorIdLong = intent.getLongExtra("SENSOR_ID_LONG", -1);
        gatewayIdString = String.valueOf(gatewayIdLong);
        sensorIdString = String.valueOf(sensorIdLong);

        if (gatewayIdLong == -1 || sensorIdLong == -1) {
            Log.e("SensorDetailActivity", getString(R.string.log_error_gateway_sensor_null));
            finish();
            return;
        }

        textSensorTitle.setText(String.format(getString(R.string.sensor_detail_title), sensorIdString, gatewayIdString));

        loadLatestDataAndHistory(); // To ustawi currentSensorType
        loadSensorMetadata(); // To ustawi stan przełącznika
        setupThresholdControls();
        checkFavoriteStatus();
    }

    @Override
    protected void onResume() {
        // ... (cała metoda onResume bez zmian, ładuje dane)
        super.onResume();
        if (LocaleManager.languageChanged) {
            LocaleManager.languageChanged = false;
            recreate();
            return;
        }
        if (appearanceManager != null && (!currentTextScale.equals(appearanceManager.getTextScale()) ||
                !currentButtonScale.equals(appearanceManager.getButtonScale()))) {
            recreate();
            return;
        }
        if (syncStatusReceiver == null) {
            syncStatusReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean success = intent.getBooleanExtra("SYNC_SUCCESS", false);
                    long now = System.currentTimeMillis();
                    if (now - lastSyncToastTime < 3000) {
                        Log.d("SensorDetailActivity", "SyncStatusReceiver: Zignorowano zduplikowany broadcast o sukcesie.");
                        return;
                    }
                    if (success) {
                        Toast.makeText(context, R.string.sync_success, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, R.string.sync_error, Toast.LENGTH_SHORT).show();
                    }
                    lastSyncToastTime = now;
                }
            };
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(syncStatusReceiver, new IntentFilter(Constants.ACTION_SYNC_STATUS));
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        loadLatestDataAndHistory();
        loadSensorMetadata();
        checkFavoriteStatus();
    }

    @Override
    protected void onPause() {
        // ... (bez zmian)
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        if (syncStatusReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStatusReceiver);
        }
    }

    // ... (metody checkFavoriteStatus, toggleFavoriteStatus, setupThresholdControls, createSeekBarListener, updateSeekBarUI, loadLatestDataAndHistory, updateSensorDetailsUI, loadSensorHistory, forceReadingsSync, updateFavoriteIcon... są OK)
    // Poniżej wklejone dla kompletności:
    private void checkFavoriteStatus() {
        isFavorite = dbHelper.isFavoriteSensor(sensorIdLong);
        updateFavoriteIcon();
    }

    private void toggleFavoriteStatus() {
        // ... (bez zmian)
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean becomingFavorite = !isFavorite;
        isFavorite = becomingFavorite;
        updateFavoriteIcon();
        String url = Constants.FAVORITE_SENSORS_ENDPOINT;
        Request request;
        if (becomingFavorite) {
            JSONObject json = new JSONObject();
            try { json.put("id", sensorIdLong); } catch (Exception e) {}
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();
        } else {
            url += "/" + sensorIdLong;
            request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .delete()
                    .build();
        }
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(SensorDetailActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show();
                    isFavorite = !becomingFavorite;
                    updateFavoriteIcon();
                });
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SensorDetailActivity.this,
                                becomingFavorite ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites,
                                Toast.LENGTH_SHORT).show();
                        Intent serviceIntent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                        serviceIntent.putExtra("FORCE_SYNC_NOW", true);
                        serviceIntent.putExtra("IS_SILENT", true);
                        startService(serviceIntent);
                    } else {
                        Toast.makeText(SensorDetailActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show();
                        isFavorite = !becomingFavorite;
                        updateFavoriteIcon();
                    }
                });
                response.close();
            }
        });
    }

    private void setupThresholdControls() {
        // ... (bez zmian)
        boolean isHumidity = getString(R.string.sensor_type_humidity).equalsIgnoreCase(currentSensorType);
        float defaultMin = isHumidity ? 5.0f : 18.0f;
        float defaultMax = isHumidity ? 30.0f : 22.0f;
        float savedMin = thresholdManager.getMinThreshold(gatewayIdString, sensorIdString, defaultMin);
        float savedMax = thresholdManager.getMaxThreshold(gatewayIdString, sensorIdString, defaultMax);
        updateSeekBarUI(seekBarThresholdMin, textThresholdMin, getString(R.string.threshold_min_label), savedMin);
        updateSeekBarUI(seekBarThresholdMax, textThresholdMax, getString(R.string.threshold_max_label), savedMax);
        seekBarThresholdMin.setOnSeekBarChangeListener(createSeekBarListener(false));
        seekBarThresholdMax.setOnSeekBarChangeListener(createSeekBarListener(true));
    }

    private SeekBar.OnSeekBarChangeListener createSeekBarListener(boolean isMaxSlider) {
        // ... (bez zmian)
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (float) progress / 2.0f;
                TextView targetTextView = isMaxSlider ? textThresholdMax : textThresholdMin;
                String label = isMaxSlider ? getString(R.string.threshold_max_label) : getString(R.string.threshold_min_label);
                targetTextView.setText(String.format(Locale.getDefault(), getString(R.string.threshold_label_format), label, value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float newMin = (float) seekBarThresholdMin.getProgress() / 2.0f;
                float newMax = (float) seekBarThresholdMax.getProgress() / 2.0f;
                thresholdManager.saveThresholds(gatewayIdString, sensorIdString, newMin, newMax);
                loadLatestDataAndHistory();
            }
        };
    }

    private void updateSeekBarUI(SeekBar seekBar, TextView textView, String label, float value) {
        // ... (bez zmian)
        seekBar.setProgress((int) (value * 2.0));
        textView.setText(String.format(Locale.getDefault(), getString(R.string.threshold_label_format), label, value));
    }

    private void loadLatestDataAndHistory() {
        // ... (bez zmian)
        SensorModel latestModel = dbHelper.getLatestSensorData(gatewayIdString, sensorIdString);
        if (latestModel != null) {
            currentSensorType = latestModel.type;
            updateSensorDetailsUI(latestModel);
        } else {
            textSensorDetails.setText(getString(R.string.sensor_detail_no_data));
            loadSensorHistory();
        }
    }

    private void updateSensorDetailsUI(SensorModel model) {
        // ... (bez zmian)
        String displayData = getString(R.string.sensor_detail_type, model.type) + "\n"
                + getString(R.string.sensor_detail_value, model.value) + "\n"
                + getString(R.string.sensor_detail_timestamp, model.getFormattedTimestamp());
        textSensorDetails.setText(displayData);
        int defaultColor;
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        defaultColor = typedValue.data;
        if (getString(R.string.sensor_type_door_contact).equalsIgnoreCase(model.type)) {
            thresholdContainer.setVisibility(View.GONE);
            textSensorDetails.setTextColor(getString(R.string.door_contact_open_value).equals(model.value) ? Color.RED : defaultColor);
        } else {
            thresholdContainer.setVisibility(View.VISIBLE);
            boolean isHumidity = getString(R.string.sensor_type_humidity).equalsIgnoreCase(model.type);
            float defaultMin = isHumidity ? 5.0f : 18.0f;
            float defaultMax = isHumidity ? 30.0f : 22.0f;
            float min = thresholdManager.getMinThreshold(gatewayIdString, sensorIdString, defaultMin);
            float max = thresholdManager.getMaxThreshold(gatewayIdString, sensorIdString, defaultMax);
            try {
                float currentValue = Float.parseFloat(model.value);
                textSensorDetails.setTextColor(currentValue < min || currentValue > max ? Color.RED : defaultColor);
            } catch (NumberFormatException e) {
                textSensorDetails.setTextColor(defaultColor);
            }
        }
        loadSensorHistory();
    }

    private void loadSensorHistory() {
        // ... (bez zmian)
        List<String> historyList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        try (Cursor historyCursor = dbHelper.getSensorHistory(gatewayIdString, sensorIdString, HISTORY_LIMIT)) {
            if (historyCursor != null && historyCursor.moveToFirst()) {
                do {
                    try {
                        String value = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VALUE));
                        long timestamp = historyCursor.getLong(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
                        String type = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TYPE));
                        historyList.add(String.format(Locale.getDefault(), getString(R.string.sensor_history_item_format), sdf.format(new Date(timestamp)), type, value));
                    } catch (Exception e) {
                        Log.e("SensorDetailActivity", "Error processing one history row.", e);
                    }
                } while (historyCursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("SensorDetailActivity", "Error loading sensor history cursor.", e);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        listSensorHistory.setAdapter(adapter);
    }

    private void forceReadingsSync() {
        // ... (bez zmian)
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        serviceIntent.putExtra("FORCE_READINGS_NOW", true);
        startService(serviceIntent);
    }

    private void updateFavoriteIcon() {
        // ... (bez zmian)
        if (btnFavorite != null) {
            btnFavorite.setImageResource(isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        }
    }

    // ⭐️ ZMIANA 6: Przebudowa metody `loadSensorMetadata`
    /**
     * Ładuje metadane czujnika (interwał, stan raportowania), które nie są w odczytach.
     */
    private void loadSensorMetadata() {
        // Użyj nowej metody z DatabaseHelper, która zwraca obiekt Sensor
        Sensor sensorData = dbHelper.getSensorMetadata(sensorIdLong);

        // Pokaż tylko dla czujników "aktywnych" (nie przycisków/kontaktronów)
        if (currentSensorType.equalsIgnoreCase("contact") || currentSensorType.equalsIgnoreCase("button") || currentSensorType.equalsIgnoreCase("motion")) {
            intervalContainer.setVisibility(View.GONE);
            reportingContainer.setVisibility(View.GONE); // Ukryj też nowy kontener
        } else {
            intervalContainer.setVisibility(View.VISIBLE);
            reportingContainer.setVisibility(View.VISIBLE); // Pokaż nowy kontener

            if (sensorData != null) {
                // 1. Ustaw interwał
                Integer interval = sensorData.getIntervalSeconds();
                if (interval != null && interval > 0) {
                    editSensorInterval.setText(String.valueOf(interval));
                } else {
                    editSensorInterval.setText(""); // Puste, jeśli null lub 0
                    editSensorInterval.setHint(getString(R.string.sensor_detail_interval_hint));
                }

                // 2. Ustaw stan przełącznika raportowania
                boolean isEnabled = sensorData.isReportingEnabled();
                // Ustaw stan wizualny bez wywoływania listenera
                switchReporting.setChecked(isEnabled);
                // Upewnij się, że jest włączony (na wypadek, gdyby API failowało)
                switchReporting.setEnabled(true);

            } else {
                // Nie udało się pobrać metadanych, ukryj ustawienia
                Log.w(TAG, "Nie można załadować metadanych dla sensora: " + sensorIdLong);
                intervalContainer.setVisibility(View.GONE);
                reportingContainer.setVisibility(View.GONE);
            }
        }
    }

    // ⭐️ ZMIANA 7: Dodanie nowej metody do przełączania stanu raportowania
    /**
     * Wywołuje endpoint API /toggle-reporting.
     * Aktualizuje UI i bazę danych na podstawie odpowiedzi serwera.
     */
    private void toggleReportingStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            switchReporting.setEnabled(true); // Włącz z powrotem
            return;
        }

        // Endpoint, który zdefiniowaliśmy na backendzie
        String url = Constants.SENSORS_ENDPOINT + "/" + sensorIdLong + "/toggle-reporting";

        // Tworzymy puste ciało dla żądania POST
        RequestBody body = RequestBody.create(new byte[0]); // Puste ciało

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body) // Używamy POST
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(SensorDetailActivity.this,
                            getString(R.string.toast_api_error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Włącz przełącznik po błędzie
                    switchReporting.setEnabled(true);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    try {
                        // Serwer zwraca zaktualizowany obiekt Sensor
                        JSONObject sensorJson = new JSONObject(responseBody);
                        final boolean newStatus = sensorJson.getBoolean("reportingEnabled");

                        // Zaktualizuj lokalną bazę danych
                        dbHelper.updateSensorReportingStatus(sensorIdLong, newStatus);

                        // Zaktualizuj UI na podstawie odpowiedzi serwera
                        runOnUiThread(() -> {
                            switchReporting.setChecked(newStatus);
                            switchReporting.setEnabled(true); // Włącz przełącznik
                            Toast.makeText(SensorDetailActivity.this,
                                    newStatus ? R.string.toast_reporting_enabled : R.string.toast_reporting_disabled,
                                    Toast.LENGTH_SHORT).show();
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Błąd parsowania odpowiedzi z /toggle-reporting", e);
                        runOnUiThread(() -> {
                            Toast.makeText(SensorDetailActivity.this, R.string.toast_api_error_parsing, Toast.LENGTH_SHORT).show();
                            switchReporting.setEnabled(true);
                        });
                    }
                } else {
                    // Błąd serwera (np. 403, 500)
                    runOnUiThread(() -> {
                        Toast.makeText(SensorDetailActivity.this,
                                getString(R.string.toast_api_error) + ": " + responseBody, Toast.LENGTH_SHORT).show();
                        switchReporting.setEnabled(true); // Włącz przełącznik
                    });
                }
                response.close();
            }
        });
    }

    /**
     * Zapisuje nowy interwał dla tego konkretnego czujnika.
     */
    private void saveSensorInterval() {
        // ... (bez zmian)
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        String intervalStr = editSensorInterval.getText() != null ? editSensorInterval.getText().toString() : "";
        Integer intervalToSend = null;

        if (!intervalStr.isEmpty()) {
            try {
                intervalToSend = Integer.parseInt(intervalStr);
                if (intervalToSend <= 0) intervalToSend = null;
            } catch (NumberFormatException e) {
                editSensorInterval.setError("Nieprawidłowa liczba");
                return;
            }
        }

        String jsonBody = "{\"intervalSeconds\": " + intervalToSend + "}";
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        String url = Constants.SENSORS_ENDPOINT + "/" + sensorIdLong;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .put(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SensorDetailActivity.this,
                        getString(R.string.toast_api_error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(SensorDetailActivity.this, R.string.toast_interval_update_success, Toast.LENGTH_SHORT).show();
                        Intent serviceIntent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                        serviceIntent.putExtra("FORCE_SYNC_NOW", true);
                        serviceIntent.putExtra("IS_SILENT", true);
                        startService(serviceIntent);
                    });
                } else {
                    String error = response.body() != null ? response.body().string() : "Unknown error";
                    runOnUiThread(() -> Toast.makeText(SensorDetailActivity.this,
                            getString(R.string.toast_api_error) + ": " + error, Toast.LENGTH_SHORT).show());
                }
                response.close();
            }
        });
    }
}