package com.example.bazunia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SensorDetailActivity extends AppCompatActivity {

    private static final String TYPE_DOOR_CONTACT = "door_contact";
    private static final int HISTORY_LIMIT = 10;
    private TextView textSensorTitle, textSensorDetails, textThresholdMin, textThresholdMax;
    private SeekBar seekBarThresholdMin, seekBarThresholdMax;
    private LinearLayout thresholdContainer;
    private ListView listSensorHistory;
    private DatabaseHelper dbHelper;
    private String gatewayId, sensorId, currentSensorType = "";
    private ThresholdManager thresholdManager;

    // NOWOŚĆ: BroadcastReceiver do odbierania sygnałów z serwisu
    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constants.ACTION_DATA_UPDATED.equals(intent.getAction())) {
                // Po prostu odśwież widok z najnowszych danych w bazie
                loadLatestDataAndHistory();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        new AppearanceManager(this).applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_detail);

        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);

        // ... (findViewById bez zmian)
        textSensorTitle = findViewById(R.id.textSensorTitle);
        textSensorDetails = findViewById(R.id.textSensorDetails);
        seekBarThresholdMin = findViewById(R.id.seekBarThresholdMin);
        textThresholdMin = findViewById(R.id.textThresholdMin);
        seekBarThresholdMax = findViewById(R.id.seekBarThresholdMax);
        textThresholdMax = findViewById(R.id.textThresholdMax);
        thresholdContainer = findViewById(R.id.thresholdContainer);
        listSensorHistory = findViewById(R.id.listSensorHistory);

        // PODŁĄCZENIE IKONY USTAWIEŃ (już to masz)
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // PODŁĄCZENIE IKONY POWROTU
        findViewById(R.id.btnBackSensorDetail).setOnClickListener(v -> {
            finish(); // Zamyka ekran szczegółów i wraca do listy
        });

        Intent intent = getIntent();
        gatewayId = intent.getStringExtra("GATEWAY_ID");
        sensorId = intent.getStringExtra("SENSOR_ID");

        textSensorTitle.setText("Dane dla Czujnika " + sensorId + " (Bramka " + gatewayId + ")");

        loadLatestDataAndHistory();
        setupThresholdControls();
        // USUNIĘTO: `setupSocket()` - już niepotrzebne
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        loadLatestDataAndHistory();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
    }

    // Logika setupThresholdControls, updateSeekBarUI, loadLatestDataAndHistory,
    // updateSensorDetailsUI, loadSensorHistory pozostaje BEZ ZMIAN

    private void setupThresholdControls() {
        boolean isHumidity = "humidity".equalsIgnoreCase(currentSensorType);
        float defaultMin = isHumidity ? 5.0f : 18.0f;
        float defaultMax = isHumidity ? 30.0f : 22.0f;

        float savedMin = thresholdManager.getMinThreshold(gatewayId, sensorId, defaultMin);
        float savedMax = thresholdManager.getMaxThreshold(gatewayId, sensorId, defaultMax);

        updateSeekBarUI(seekBarThresholdMin, textThresholdMin, "Próg minimalny", savedMin);
        updateSeekBarUI(seekBarThresholdMax, textThresholdMax, "Próg maksymalny", savedMax);

        seekBarThresholdMin.setOnSeekBarChangeListener(createSeekBarListener(false));
        seekBarThresholdMax.setOnSeekBarChangeListener(createSeekBarListener(true));
    }

    private SeekBar.OnSeekBarChangeListener createSeekBarListener(boolean isMaxSlider) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (float) progress / 2.0f;
                TextView targetTextView = isMaxSlider ? textThresholdMax : textThresholdMin;
                String label = isMaxSlider ? "Próg maksymalny" : "Próg minimalny";
                targetTextView.setText(String.format(Locale.getDefault(), "%s: %.1f", label, value));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float newMin = (float) seekBarThresholdMin.getProgress() / 2.0f;
                float newMax = (float) seekBarThresholdMax.getProgress() / 2.0f;
                thresholdManager.saveThresholds(gatewayId, sensorId, newMin, newMax);
                // Po zapisaniu progu odśwież UI, aby natychmiast zobaczyć zmianę koloru
                loadLatestDataAndHistory();
            }
        };
    }

    private void updateSeekBarUI(SeekBar seekBar, TextView textView, String label, float value) {
        seekBar.setProgress((int) (value * 2.0));
        textView.setText(String.format(Locale.getDefault(), "%s: %.1f", label, value));
    }

    private void loadLatestDataAndHistory() {
        SensorModel latestModel = dbHelper.getLatestSensorData(gatewayId, sensorId);
        if (latestModel != null) {
            currentSensorType = latestModel.type;
            updateSensorDetailsUI(latestModel);
        } else {
            textSensorDetails.setText("Brak danych w bazie dla tego czujnika.");
        }
    }

    private void updateSensorDetailsUI(SensorModel model) {
        String displayData = "Typ: " + model.type + "\n"
                + "Wartość: " + model.value + "\n"
                + "Timestamp: " + model.getFormattedTimestamp();
        textSensorDetails.setText(displayData);

        if (TYPE_DOOR_CONTACT.equalsIgnoreCase(model.type)) {
            thresholdContainer.setVisibility(View.GONE);
            textSensorDetails.setTextColor("1".equals(model.value) ? Color.RED : Color.BLACK);
        } else {
            thresholdContainer.setVisibility(View.VISIBLE);
            // POPRAWKA: Pobierz prawidłowe domyślne progi
            boolean isHumidity = "humidity".equalsIgnoreCase(model.type);
            float defaultMin = isHumidity ? 5.0f : 18.0f;
            float defaultMax = isHumidity ? 30.0f : 22.0f;
            float min = thresholdManager.getMinThreshold(gatewayId, sensorId, defaultMin);
            float max = thresholdManager.getMaxThreshold(gatewayId, sensorId, defaultMax);
            try {
                float currentValue = Float.parseFloat(model.value);
                textSensorDetails.setTextColor(currentValue < min || currentValue > max ? Color.RED : Color.BLACK);
            } catch (NumberFormatException e) {
                textSensorDetails.setTextColor(Color.BLACK);
            }
        }
        loadSensorHistory();
    }

    private void loadSensorHistory() {
        Cursor historyCursor = dbHelper.getSensorHistory(gatewayId, sensorId, HISTORY_LIMIT);
        List<String> historyList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        if (historyCursor != null && historyCursor.moveToFirst()) {
            do {
                String value = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VALUE));
                long timestamp = Long.parseLong(historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIMESTAMP)));
                String type = historyCursor.getString(historyCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TYPE));
                historyList.add(String.format(Locale.getDefault(), "[%s] Typ: %s, Wartość: %s", sdf.format(new Date(timestamp)), type, value));
            } while (historyCursor.moveToNext());
            historyCursor.close();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        listSensorHistory.setAdapter(adapter);
    }
}