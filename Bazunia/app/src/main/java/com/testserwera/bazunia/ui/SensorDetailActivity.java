package com.testserwera.bazunia.ui;

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
import android.view.inputmethod.InputMethodManager;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;


import com.testserwera.bazunia.R;

import com.testserwera.bazunia.data.DatabaseHelper;
import com.testserwera.bazunia.data.NotificationFrequencyManager;
import com.testserwera.bazunia.data.Sensor;
import com.testserwera.bazunia.data.SensorModel;
import com.testserwera.bazunia.data.ThresholdManager;
import com.testserwera.bazunia.data.VpsClientService;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.LocaleManager;

import org.json.JSONException;
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

    private static final String TAG = "SensorDetailActivity";
    private static final int HISTORY_LIMIT = 10;

    // UI Elements
    private TextView textSensorDetails;
    private TextView textThresholdMin;
    private TextView textThresholdMax;
    private SeekBar seekBarThresholdMin;
    private SeekBar seekBarThresholdMax;
    private LinearLayout thresholdContainer;
    private ListView listSensorHistory;
    private ImageButton btnFavorite;

    // UI Elements - Settings & Reporting
    private TextInputEditText editSensorInterval;
    private LinearLayout intervalContainer;
    private SwitchMaterial switchReporting;
    private LinearLayout reportingContainer;
    private TextInputEditText editSensorNotificationInterval;
    private LinearLayout notificationIntervalContainer;

    // Logic & Data
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationFrequencyManager notificationFrequencyManager;
    private AppearanceManager appearanceManager;
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;

    // State
    // ⭐️ USUNIĘTO: private long gatewayIdLong; (teraz zmienna lokalna w onCreate)
    private long sensorIdLong;
    private String gatewayIdString;
    private String sensorIdString;
    private String currentSensorType = "";
    private String currentTextScale;
    private String currentButtonScale;
    private boolean isFavorite = false;
    private long lastSyncToastTime = 0;

    private BroadcastReceiver syncStatusReceiver;

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
        appearanceManager = new AppearanceManager(this);
        currentTextScale = appearanceManager.getTextScale();
        currentButtonScale = appearanceManager.getButtonScale();
        appearanceManager.applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_detail);

        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);
        notificationFrequencyManager = new NotificationFrequencyManager(this);
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
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        btnFavorite = findViewById(R.id.btnFavorite);

        // Interwały i Raportowanie
        intervalContainer = findViewById(R.id.intervalContainer);
        editSensorInterval = findViewById(R.id.editSensorInterval);
        MaterialButton btnSaveInterval = findViewById(R.id.btnSaveInterval);

        reportingContainer = findViewById(R.id.reportingContainer);
        switchReporting = findViewById(R.id.switchReporting);

        notificationIntervalContainer = findViewById(R.id.notificationIntervalContainer);
        editSensorNotificationInterval = findViewById(R.id.editSensorNotificationInterval);
        MaterialButton btnSaveNotificationInterval = findViewById(R.id.btnSaveNotificationInterval);

        // --- Skalowanie ---
        appearanceManager.applyIconScale(btnSettings);
        appearanceManager.applyIconScale(btnBack);

        // --- Listenery ---
        btnRefresh.setOnClickListener(v -> forceReadingsSync());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnBack.setOnClickListener(v -> finish());
        btnFavorite.setOnClickListener(v -> toggleFavoriteStatus());

        btnSaveInterval.setOnClickListener(v -> saveSensorInterval());
        btnSaveNotificationInterval.setOnClickListener(v -> saveSensorNotificationInterval());

        // Listener dla Switcha - ręczna kontrola
        switchReporting.setOnClickListener(v -> {
            switchReporting.setEnabled(false); // Blokujemy do czasu odpowiedzi
            toggleReportingStatus();
        });

        // --- Pobieranie danych z Intent ---
        Intent intent = getIntent();

        // ⭐️ ZMIANA: gatewayIdLong jest teraz zmienną lokalną
        long gatewayIdLong = intent.getLongExtra("GATEWAY_ID_LONG", -1);
        sensorIdLong = intent.getLongExtra("SENSOR_ID_LONG", -1);

        gatewayIdString = String.valueOf(gatewayIdLong);
        sensorIdString = String.valueOf(sensorIdLong);

        if (gatewayIdLong == -1 || sensorIdLong == -1) {
            Log.e(TAG, getString(R.string.log_error_gateway_sensor_null));
            finish();
            return;
        }

        textSensorTitle.setText(String.format(getString(R.string.sensor_detail_title), sensorIdString, gatewayIdString));

        loadLatestDataAndHistory();
        loadSensorMetadata();
        loadSensorNotificationSettings();
        setupThresholdControls();
        checkFavoriteStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Obsługa zmiany języka
        if (LocaleManager.languageChanged) {
            LocaleManager.languageChanged = false;
            recreate();
            return;
        }
        // Obsługa zmiany wyglądu
        if (appearanceManager != null && (!currentTextScale.equals(appearanceManager.getTextScale()) ||
                !currentButtonScale.equals(appearanceManager.getButtonScale()))) {
            recreate();
            return;
        }

        // Rejestracja Receivera Statusu Synchronizacji
        if (syncStatusReceiver == null) {
            syncStatusReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean success = intent.getBooleanExtra("SYNC_SUCCESS", false);
                    long now = System.currentTimeMillis();
                    // Zapobieganie spamowaniu Toastami
                    if (now - lastSyncToastTime < 3000) return;

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
        loadSensorNotificationSettings();
        checkFavoriteStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        if (syncStatusReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStatusReceiver);
        }
    }

    // ==========================================
    // LOGIKA ULUBIONYCH (FAVORITES)
    // ==========================================

    private void checkFavoriteStatus() {
        isFavorite = dbHelper.isFavoriteSensor(sensorIdLong);
        updateFavoriteIcon();
    }

    private void toggleFavoriteStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean becomingFavorite = !isFavorite;
        isFavorite = becomingFavorite; // Optymistyczna aktualizacja UI
        updateFavoriteIcon();

        String url = Constants.FAVORITE_SENSORS_ENDPOINT;
        Request request;

        if (becomingFavorite) {
            JSONObject json = new JSONObject();
            try {
                json.put("id", sensorIdLong);
            } catch (JSONException e) {
                Log.e(TAG, "Błąd tworzenia JSON dla ulubionych", e);
            }
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
                    isFavorite = !becomingFavorite; // Cofnij zmianę
                    updateFavoriteIcon();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // ⭐️ ZMIANA: Try-with-resources dla Response
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(SensorDetailActivity.this,
                                    becomingFavorite ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites,
                                    Toast.LENGTH_SHORT).show();

                            // Wymuś odświeżenie listy czujników
                            Intent serviceIntent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                            serviceIntent.putExtra("FORCE_SYNC_NOW", true);
                            serviceIntent.putExtra("IS_SILENT", true);
                            startService(serviceIntent);
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(SensorDetailActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show();
                            isFavorite = !becomingFavorite;
                            updateFavoriteIcon();
                        });
                    }
                }
            }
        });
    }

    private void updateFavoriteIcon() {
        if (btnFavorite != null) {
            btnFavorite.setImageResource(isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        }
    }

    // ==========================================
    // LOGIKA PROGÓW (THRESHOLDS)
    // ==========================================

    private void setupThresholdControls() {
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
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (float) progress / 2.0f;
                TextView targetTextView = isMaxSlider ? textThresholdMax : textThresholdMin;
                String label = isMaxSlider ? getString(R.string.threshold_max_label) : getString(R.string.threshold_min_label);
                targetTextView.setText(String.format(Locale.getDefault(), getString(R.string.threshold_label_format), label, value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Interface method stub
            }

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
        seekBar.setProgress((int) (value * 2.0));
        textView.setText(String.format(Locale.getDefault(), getString(R.string.threshold_label_format), label, value));
    }

    // ==========================================
    // LOGIKA UI I DANYCH
    // ==========================================

    private void loadLatestDataAndHistory() {
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
        String displayData = getString(R.string.sensor_detail_type, model.type) + "\n"
                + getString(R.string.sensor_detail_value, model.value) + "\n"
                + getString(R.string.sensor_detail_timestamp, model.getFormattedTimestamp());

        textSensorDetails.setText(displayData);

        // Kolorowanie tekstu w przypadku alertu
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int defaultColor = typedValue.data;

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
        List<String> historyList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        // ⭐️ ZMIANA: Try-with-resources dla Cursor
        try (Cursor historyCursor = dbHelper.getSensorHistory(gatewayIdString, sensorIdString, HISTORY_LIMIT)) {
            if (historyCursor != null && historyCursor.moveToFirst()) {
                do {
                    try {
                        String value = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VALUE));
                        long timestamp = historyCursor.getLong(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
                        String type = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TYPE));

                        historyList.add(String.format(Locale.getDefault(), getString(R.string.sensor_history_item_format), sdf.format(new Date(timestamp)), type, value));
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing history row", e);
                    }
                } while (historyCursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading sensor history cursor", e);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        listSensorHistory.setAdapter(adapter);
    }

    private void forceReadingsSync() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        serviceIntent.putExtra("FORCE_READINGS_NOW", true);
        startService(serviceIntent);
    }

    // ==========================================
    // LOGIKA METADANYCH I USTAWIEŃ
    // ==========================================

    private void loadSensorMetadata() {
        Sensor sensorData = dbHelper.getSensorMetadata(sensorIdLong);

        // Ukryj ustawienia dla czujników pasywnych
        if (isPassiveSensor(currentSensorType)) {
            intervalContainer.setVisibility(View.GONE);
            reportingContainer.setVisibility(View.GONE);
        } else {
            intervalContainer.setVisibility(View.VISIBLE);
            reportingContainer.setVisibility(View.VISIBLE);

            if (sensorData != null) {
                // 1. Interwał
                Integer interval = sensorData.getIntervalSeconds();
                editSensorInterval.setText((interval != null && interval > 0) ? String.valueOf(interval) : "");

                // 2. Switch
                switchReporting.setChecked(sensorData.isReportingEnabled());
                switchReporting.setEnabled(true);
            } else {
                Log.w(TAG, "Nie można załadować metadanych dla sensora: " + sensorIdLong);
                intervalContainer.setVisibility(View.GONE);
                reportingContainer.setVisibility(View.GONE);
            }
        }
    }

    private void loadSensorNotificationSettings() {
        if (isPassiveSensor(currentSensorType)) {
            notificationIntervalContainer.setVisibility(View.GONE);
        } else {
            notificationIntervalContainer.setVisibility(View.VISIBLE);
            int savedInterval = notificationFrequencyManager.getFrequency(sensorIdLong, -1);
            editSensorNotificationInterval.setText(savedInterval > 0 ? String.valueOf(savedInterval) : "");
        }
    }

    private boolean isPassiveSensor(String type) {
        return type.equalsIgnoreCase("contact") ||
                type.equalsIgnoreCase("button") ||
                type.equalsIgnoreCase("motion");
    }

    private void saveSensorNotificationInterval() {
        String intervalStr = editSensorNotificationInterval.getText() != null ? editSensorNotificationInterval.getText().toString() : "";
        int intervalToSave = 0;

        if (!intervalStr.isEmpty()) {
            try {
                intervalToSave = Integer.parseInt(intervalStr);
                if (intervalToSave <= 0) intervalToSave = 0;
            } catch (NumberFormatException e) {
                editSensorNotificationInterval.setError(getString(R.string.error_invalid_number));
                return;
            }
        }

        notificationFrequencyManager.saveFrequency(sensorIdLong, intervalToSave);
        Toast.makeText(this, R.string.toast_notification_interval_saved, Toast.LENGTH_SHORT).show();

        hideKeyboard();
    }

    private void toggleReportingStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            switchReporting.setEnabled(true);
            return;
        }

        String url = Constants.SENSORS_ENDPOINT + "/" + sensorIdLong + "/toggle-reporting";
        RequestBody body = RequestBody.create(new byte[0]);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(SensorDetailActivity.this,
                            getString(R.string.toast_api_error_with_reason, e.getMessage()), Toast.LENGTH_SHORT).show();
                    switchReporting.setEnabled(true);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // ⭐️ ZMIANA: Try-with-resources dla Response (automatyczne zamknięcie)
                try (Response r = response) {
                    // Odczytujemy body od razu, zanim stream zostanie zamknięty
                    String responseBody = r.body() != null ? r.body().string() : "";

                    if (r.isSuccessful()) {
                        JSONObject sensorJson = new JSONObject(responseBody);
                        final boolean newStatus = sensorJson.getBoolean("reportingEnabled");

                        dbHelper.updateSensorReportingStatus(sensorIdLong, newStatus);

                        runOnUiThread(() -> {
                            switchReporting.setChecked(newStatus);
                            switchReporting.setEnabled(true);
                            Toast.makeText(SensorDetailActivity.this,
                                    newStatus ? R.string.toast_reporting_enabled : R.string.toast_reporting_disabled,
                                    Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(SensorDetailActivity.this,
                                    getString(R.string.toast_api_error_with_reason, responseBody), Toast.LENGTH_SHORT).show();
                            switchReporting.setEnabled(true);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Błąd w onResponse /toggle-reporting", e);
                    runOnUiThread(() -> {
                        Toast.makeText(SensorDetailActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show();
                        switchReporting.setEnabled(true);
                    });
                }
            }
        });
    }

    private void saveSensorInterval() {
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
                editSensorInterval.setError(getString(R.string.error_invalid_number));
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
                        getString(R.string.toast_api_error_with_reason, e.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // ⭐️ ZMIANA: Try-with-resources dla Response
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(SensorDetailActivity.this, R.string.toast_interval_update_success, Toast.LENGTH_SHORT).show();
                            Intent serviceIntent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                            serviceIntent.putExtra("FORCE_SYNC_NOW", true);
                            serviceIntent.putExtra("IS_SILENT", true);
                            startService(serviceIntent);
                        });
                    } else {
                        String error = r.body() != null ? r.body().string() : "Unknown error";
                        runOnUiThread(() -> Toast.makeText(SensorDetailActivity.this,
                                getString(R.string.toast_api_error_with_reason, error), Toast.LENGTH_SHORT).show());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Błąd odczytu odpowiedzi API", e);
                }
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Nie udało się schować klawiatury", e);
        }
    }
}