package com.example.bazunia.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.example.bazunia.utils.LocaleManager;
import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.data.DatabaseHelper;
import com.example.bazunia.R;
import com.example.bazunia.data.SensorModel;
import com.example.bazunia.data.ThresholdManager;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SensorDetailActivity extends AppCompatActivity {

    private static final int HISTORY_LIMIT = 10;
    private TextView textSensorDetails, textThresholdMin, textThresholdMax;
    private SeekBar seekBarThresholdMin, seekBarThresholdMax;
    private LinearLayout thresholdContainer;
    private ListView listSensorHistory;
    private DatabaseHelper dbHelper;
    private ThresholdManager thresholdManager;

    private long gatewayIdLong;
    private long sensorIdLong;
    // Zatrzymujemy stare String ID do odpytywania tabeli odczytów (READINGS)
    private String gatewayIdString, sensorIdString;

    private String currentSensorType = "";

    private AppearanceManager appearanceManager;
    private String currentTextScale;
    private String currentButtonScale;

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

        appearanceManager.applyIconScale(btnSettings);
        appearanceManager.applyIconScale(btnBack);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnBack.setOnClickListener(v -> finish());

        Intent intent = getIntent();
        // Pobieramy nowe long ID przekazane z DataActivity
        gatewayIdLong = intent.getLongExtra("GATEWAY_ID_LONG", -1);
        sensorIdLong = intent.getLongExtra("SENSOR_ID_LONG", -1);

        // Konwertujemy long na String dla starych metod DB (do odczytów)
        gatewayIdString = String.valueOf(gatewayIdLong);
        sensorIdString = String.valueOf(sensorIdLong);

        if (gatewayIdLong == -1 || sensorIdLong == -1) {
            Log.e("SensorDetailActivity", getString(R.string.log_error_gateway_sensor_null));
            finish();
            return;
        }

        // TODO: W przyszłości pobierz nazwę czujnika i bramki z tabel METADATA
        textSensorTitle.setText(String.format(getString(R.string.sensor_detail_title), sensorIdString, gatewayIdString));

        loadLatestDataAndHistory();
        setupThresholdControls();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (LocaleManager.languageChanged) {
            LocaleManager.languageChanged = false;
            recreate();
        }
        if (appearanceManager != null && (!currentTextScale.equals(appearanceManager.getTextScale()) ||
                !currentButtonScale.equals(appearanceManager.getButtonScale()))) {
            recreate();
            return;
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        loadLatestDataAndHistory();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
    }

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
        seekBar.setProgress((int) (value * 2.0));
        textView.setText(String.format(Locale.getDefault(), getString(R.string.threshold_label_format), label, value));
    }

    private void loadLatestDataAndHistory() {
        SensorModel latestModel = dbHelper.getLatestSensorData(gatewayIdString, sensorIdString);
        if (latestModel != null) {
            currentSensorType = latestModel.type;
            updateSensorDetailsUI(latestModel);
        } else {
            textSensorDetails.setText(getString(R.string.sensor_detail_no_data));
            loadSensorHistory(); // Mimo wszystko załaduj historię (może być pusta)
        }
    }

    private void updateSensorDetailsUI(SensorModel model) {
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
        List<String> historyList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        try (Cursor historyCursor = dbHelper.getSensorHistory(gatewayIdString, sensorIdString, HISTORY_LIMIT)) {
            if (historyCursor != null && historyCursor.moveToFirst()) {
                do {
                    try {
                        String value = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VALUE));
                        // <<< OTO POPRAWKA >>>
                        long timestamp = Long.parseLong(historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP)));
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
}