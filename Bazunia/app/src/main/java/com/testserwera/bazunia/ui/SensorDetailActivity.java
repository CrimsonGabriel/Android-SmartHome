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
import android.util.Pair;
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

import androidx.annotation.NonNull; // ✅ Dodano wymagany import
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private static final int HISTORY_LIMIT = 50;

    // --- UI GŁÓWNE ---
    private TextView textSensorDetails;
    private ListView listSensorHistory;
    private ImageButton btnFavorite;

    // --- UI BOTTOM SHEET ---
    private TextView textThresholdMin, textThresholdMax;
    private SeekBar seekBarThresholdMin, seekBarThresholdMax;
    private CardView cardThresholds; // ✅ Używamy tego zamiast thresholdContainer

    private TextInputEditText editSensorInterval;
    private SwitchMaterial switchReporting;

    private TextInputEditText editSensorNotificationInterval;
    private LinearLayout notificationIntervalContainer;

    // --- LOGIKA ---
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;
    private NotificationFrequencyManager notificationFrequencyManager;
    private AppearanceManager appearanceManager;
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;
    private BroadcastReceiver syncStatusReceiver;

    private long sensorIdLong;
    private String gatewayIdString;
    private String sensorIdString;
    private String currentSensorType = "";
    private String currentTextScale, currentButtonScale;
    private boolean isFavorite = false;
    private long lastSyncToastTime = 0;

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

        Intent intent = getIntent();
        long gatewayIdLong = intent.getLongExtra("GATEWAY_ID_LONG", -1);
        sensorIdLong = intent.getLongExtra("SENSOR_ID_LONG", -1);

        gatewayIdString = String.valueOf(gatewayIdLong);
        sensorIdString = String.valueOf(sensorIdLong);

        if (gatewayIdLong == -1 || sensorIdLong == -1) {
            finish();
            return;
        }

        TextView textSensorTitle = findViewById(R.id.textSensorTitle);
        textSensorDetails = findViewById(R.id.textSensorDetails);
        listSensorHistory = findViewById(R.id.listSensorHistory);
        MaterialButton btnBack = findViewById(R.id.btnBackSensorDetail);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        btnFavorite = findViewById(R.id.btnFavorite);
        FloatingActionButton fabSettings = findViewById(R.id.fabSettings);

        // Ukrycie starego przycisku jeśli istnieje
        View oldBtnSettings = findViewById(R.id.btnSettings);
        if (oldBtnSettings != null) oldBtnSettings.setVisibility(View.GONE);

        textSensorTitle.setText(String.format(getString(R.string.sensor_detail_title), sensorIdString, gatewayIdString));
        appearanceManager.applyIconScale(btnBack);

        btnRefresh.setOnClickListener(v -> forceReadingsSync());
        btnBack.setOnClickListener(v -> finish());
        btnFavorite.setOnClickListener(v -> toggleFavoriteStatus());
        fabSettings.setOnClickListener(v -> openSettingsSheet());

        loadLatestDataAndHistory();
        checkFavoriteStatus();
    }

    @Override
    protected void onResume() {
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
                    if (now - lastSyncToastTime < 3000) return;
                    if (success) Toast.makeText(SensorDetailActivity.this, R.string.sync_success, Toast.LENGTH_SHORT).show();
                    else Toast.makeText(SensorDetailActivity.this, R.string.sync_error, Toast.LENGTH_SHORT).show();
                    lastSyncToastTime = now;
                }
            };
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(syncStatusReceiver, new IntentFilter(Constants.ACTION_SYNC_STATUS));
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        loadLatestDataAndHistory();
        checkFavoriteStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        if (syncStatusReceiver != null) LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStatusReceiver);
    }

    // --- BOTTOM SHEET ---
    private void openSettingsSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_sensor_settings_sheet, findViewById(android.R.id.content), false);
        bottomSheetDialog.setContentView(sheetView);

        // ✅ Teraz pobieramy CardView (card_thresholds) z XML-a, który właśnie utworzyliśmy
        cardThresholds = sheetView.findViewById(R.id.card_thresholds);

        seekBarThresholdMin = sheetView.findViewById(R.id.seekBarThresholdMin);
        textThresholdMin = sheetView.findViewById(R.id.textThresholdMin);
        seekBarThresholdMax = sheetView.findViewById(R.id.seekBarThresholdMax);
        textThresholdMax = sheetView.findViewById(R.id.textThresholdMax);

        editSensorInterval = sheetView.findViewById(R.id.editSensorInterval);
        MaterialButton btnSaveInterval = sheetView.findViewById(R.id.btnSaveInterval);

        switchReporting = sheetView.findViewById(R.id.switchReporting);

        notificationIntervalContainer = sheetView.findViewById(R.id.notificationIntervalContainer);
        editSensorNotificationInterval = sheetView.findViewById(R.id.editSensorNotificationInterval);
        MaterialButton btnSaveNotificationInterval = sheetView.findViewById(R.id.btnSaveNotificationInterval);

        if (btnSaveInterval != null) {
            btnSaveInterval.setOnClickListener(v -> {
                saveSensorInterval();
                bottomSheetDialog.dismiss();
            });
        }
        if (btnSaveNotificationInterval != null) {
            btnSaveNotificationInterval.setOnClickListener(v -> {
                saveSensorNotificationInterval();
                bottomSheetDialog.dismiss();
            });
        }
        if (switchReporting != null) {
            switchReporting.setOnClickListener(v -> {
                switchReporting.setEnabled(false);
                toggleReportingStatus();
            });
        }

        // Wczytaj dane i ustaw widoczność
        loadSensorMetadata();
        loadSensorNotificationSettings();
        setupThresholdControls();

        bottomSheetDialog.setOnDismissListener(dialog -> hideKeyboard());
        bottomSheetDialog.show();
    }

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

        // Pobranie domyślnego koloru tekstu z motywu (żeby wracał do normalnego koloru)
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int defaultColor = typedValue.data;

        // ⭐️ NOWA LOGIKA KOLOROWANIA (Spójna z listą) ⭐️
        boolean isAlarmState = false;

        if (!thresholdManager.isThresholdSupported(model.type)) {
            // --- LOGIKA DLA BINARNYCH (Światło, Drzwi, Ruch itp.) ---
            // Używamy Float.parseFloat, żeby "1" i "1.0" działały tak samo
            try {
                float val = Float.parseFloat(model.value);
                // Jeśli > 0.5 (czyli 1) uznajemy za stan aktywny/alarmowy -> CZERWONY
                if (val > 0.5f) {
                    isAlarmState = true;
                }
            } catch (NumberFormatException e) {
                // Jak przyjdą śmieci zamiast liczby, to nie robimy alarmu
            }
        } else {
            // --- LOGIKA DLA ANALOGOWYCH (Suwaki: Temp, Wilgotność itp.) ---
            // Czerwony tylko jeśli wyjdzie poza suwaki (Thresholds)
            Pair<Float, Float> defaultRange = thresholdManager.getDefaultRangeForType(model.type);
            float min = thresholdManager.getMinThreshold(gatewayIdString, sensorIdString, defaultRange.first);
            float max = thresholdManager.getMaxThreshold(gatewayIdString, sensorIdString, defaultRange.second);

            try {
                float val = Float.parseFloat(model.value);
                if (val < min || val > max) {
                    isAlarmState = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Aplikujemy kolor: Czerwony (Alarm) lub Domyślny (Norma)
        if (isAlarmState) {
            textSensorDetails.setTextColor(Color.RED);
        } else {
            textSensorDetails.setTextColor(defaultColor);
        }

        loadSensorHistory();
    }

    private void loadSensorHistory() {
        List<String> historyList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        try (Cursor c = dbHelper.getSensorHistory(gatewayIdString, sensorIdString, HISTORY_LIMIT)) {
            if (c != null && c.moveToFirst()) {
                do {
                    String val = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VALUE));
                    long ts = c.getLong(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP));
                    String type = c.getString(c.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TYPE));
                    historyList.add(String.format(Locale.getDefault(), getString(R.string.sensor_history_item_format), sdf.format(new Date(ts)), type, val));
                } while (c.moveToNext());
            }
        } catch (Exception e) { Log.e(TAG, "History error", e); }
        listSensorHistory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList));
    }

    // --- SUWAKI I PROGI ---
    private void setupThresholdControls() {
        if (seekBarThresholdMin == null || seekBarThresholdMax == null) return;

        // ⭐️ POPRAWKA LOGIKI ⭐️
        // thresholdManager.isThresholdSupported zwraca TRUE dla Analogowych (Temp, Power)
        // i FALSE dla Binarnych (Light, Flow, Button).

        if (thresholdManager.isThresholdSupported(currentSensorType)) {
            // JEST Analogowy -> POKAZUJEMY suwaki
            if (cardThresholds != null) cardThresholds.setVisibility(View.VISIBLE);
        } else {
            // JEST Binarny -> UKRYWAMY suwaki
            if (cardThresholds != null) cardThresholds.setVisibility(View.GONE);
            return; // Wychodzimy, nie ma sensu ustawiać paska
        }

        // Dalsza część kodu wykonuje się tylko dla Analogowych (tych widocznych)
        Pair<Float, Float> range = thresholdManager.getDefaultRangeForType(currentSensorType);
        float absMin = range.first;
        float absMax = range.second;

        int maxProgress = (int) ((absMax - absMin) * 2);
        seekBarThresholdMin.setMax(maxProgress);
        seekBarThresholdMax.setMax(maxProgress);

        float currentMin = thresholdManager.getMinThreshold(gatewayIdString, sensorIdString, absMin);
        float currentMax = thresholdManager.getMaxThreshold(gatewayIdString, sensorIdString, absMax);

        seekBarThresholdMin.setProgress((int)((currentMin - absMin) * 2));
        seekBarThresholdMax.setProgress((int)((currentMax - absMin) * 2));

        updateThresholdLabel(textThresholdMin, getString(R.string.threshold_min_label), currentMin);
        updateThresholdLabel(textThresholdMax, getString(R.string.threshold_max_label), currentMax);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = absMin + (progress / 2.0f);
                boolean isMax = (seekBar == seekBarThresholdMax);
                updateThresholdLabel(isMax ? textThresholdMax : textThresholdMin,
                        isMax ? getString(R.string.threshold_max_label) : getString(R.string.threshold_min_label),
                        val);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float newMin = absMin + (seekBarThresholdMin.getProgress() / 2.0f);
                float newMax = absMin + (seekBarThresholdMax.getProgress() / 2.0f);
                thresholdManager.saveThresholds(gatewayIdString, sensorIdString, newMin, newMax);
                loadLatestDataAndHistory();
            }
        };

        seekBarThresholdMin.setOnSeekBarChangeListener(listener);
        seekBarThresholdMax.setOnSeekBarChangeListener(listener);
    }

    private void updateThresholdLabel(TextView view, String label, float val) {
        if (view != null) view.setText(String.format(Locale.US, "%s: %.1f", label, val));
    }

    private void loadSensorMetadata() {
        if (editSensorInterval == null || switchReporting == null) return;
        Sensor sensorData = dbHelper.getSensorMetadata(sensorIdLong);
        if (sensorData != null) {
            Integer interval = sensorData.getIntervalSeconds();
            editSensorInterval.setText((interval != null && interval > 0) ? String.valueOf(interval) : "");
            switchReporting.setChecked(sensorData.isReportingEnabled());
            switchReporting.setEnabled(true);
        }
    }

    private void loadSensorNotificationSettings() {
        if (notificationIntervalContainer == null || editSensorNotificationInterval == null) return;
        boolean isPassive = isPassiveSensor(currentSensorType);
        notificationIntervalContainer.setVisibility(isPassive ? View.GONE : View.VISIBLE);

        if (!isPassive) {
            int savedInterval = notificationFrequencyManager.getFrequency(sensorIdLong, -1);
            editSensorNotificationInterval.setText(savedInterval > 0 ? String.valueOf(savedInterval) : "");
        }
    }

    private void saveSensorInterval() {
        if (editSensorInterval == null) return;
        String jwt = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwt == null) return;

        String txt = (editSensorInterval.getText() != null) ? editSensorInterval.getText().toString() : "";
        Integer val = null;
        try { if(!txt.isEmpty()) val = Integer.parseInt(txt); } catch(Exception ignored) {}

        String json = "{\"intervalSeconds\": " + val + "}";
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        Request req = new Request.Builder().url(Constants.SENSORS_ENDPOINT + "/" + sensorIdLong).put(body).addHeader("Authorization", "Bearer " + jwt).build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { runOnUiThread(() -> Toast.makeText(SensorDetailActivity.this, "Error", Toast.LENGTH_SHORT).show()); }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    Toast.makeText(SensorDetailActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                    intent.putExtra("FORCE_SYNC_NOW", true);
                    intent.putExtra("IS_SILENT", true);
                    startService(intent);
                });
            }
        });
    }

    private void saveSensorNotificationInterval() {
        if (editSensorNotificationInterval == null) return;
        String txt = (editSensorNotificationInterval.getText() != null) ? editSensorNotificationInterval.getText().toString() : "";
        int val = 0;
        try { if(!txt.isEmpty()) val = Integer.parseInt(txt); } catch(Exception ignored) {}
        notificationFrequencyManager.saveFrequency(sensorIdLong, val);
        Toast.makeText(this, "Saved Notif Interval", Toast.LENGTH_SHORT).show();
        hideKeyboard();
    }

    private void toggleReportingStatus() {
        if (switchReporting == null) return;
        String jwt = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwt == null) { switchReporting.setEnabled(true); return; }

        Request req = new Request.Builder()
                .url(Constants.SENSORS_ENDPOINT + "/" + sensorIdLong + "/toggle-reporting")
                .post(RequestBody.create(new byte[0]))
                .addHeader("Authorization", "Bearer " + jwt)
                .build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> switchReporting.setEnabled(true));
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "{}";
                if (response.isSuccessful()) {
                    try {
                        boolean newStatus = new JSONObject(body).getBoolean("reportingEnabled");
                        dbHelper.updateSensorReportingStatus(sensorIdLong, newStatus);
                        runOnUiThread(() -> {
                            switchReporting.setChecked(newStatus);
                            switchReporting.setEnabled(true);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "JSON error: " + e.getMessage());
                        runOnUiThread(() -> switchReporting.setEnabled(true));
                    }
                } else {
                    runOnUiThread(() -> switchReporting.setEnabled(true));
                }
            }
        });
    }

    private void toggleFavoriteStatus() {
        String jwt = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwt == null) return;
        boolean becomingFav = !isFavorite;
        isFavorite = becomingFav;
        updateFavoriteIcon();

        String url = Constants.FAVORITE_SENSORS_ENDPOINT;
        Request req;
        if (becomingFav) {
            JSONObject json = new JSONObject();
            try { json.put("id", sensorIdLong); } catch(Exception ignored){}
            req = new Request.Builder().url(url).post(RequestBody.create(json.toString(), MediaType.get("application/json"))).addHeader("Authorization", "Bearer " + jwt).build();
        } else {
            req = new Request.Builder().url(url + "/" + sensorIdLong).delete().addHeader("Authorization", "Bearer " + jwt).build();
        }
        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> { isFavorite = !becomingFav; updateFavoriteIcon(); });
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                        intent.putExtra("FORCE_SYNC_NOW", true);
                        intent.putExtra("IS_SILENT", true);
                        startService(intent);
                    });
                } else {
                    runOnUiThread(() -> { isFavorite = !becomingFav; updateFavoriteIcon(); });
                }
            }
        });
    }

    private void checkFavoriteStatus() {
        isFavorite = dbHelper.isFavoriteSensor(sensorIdLong);
        updateFavoriteIcon();
    }

    private void updateFavoriteIcon() {
        if (btnFavorite != null) btnFavorite.setImageResource(isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
    }

    private void forceReadingsSync() {
        Intent intent = new Intent(this, VpsClientService.class);
        intent.putExtra("FORCE_READINGS_NOW", true);
        startService(intent);
    }

    private boolean isPassiveSensor(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.equals("contact") || t.equals("button") || t.equals("motion") || t.equals("button_press");
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception e) {
            Log.e(TAG, "Keyboard hide error", e);
        }
    }
}