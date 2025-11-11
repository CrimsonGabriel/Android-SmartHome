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
import com.example.bazunia.data.SensorModel;
import com.example.bazunia.data.ThresholdManager;
import com.example.bazunia.data.VpsClientService;
import com.google.android.material.button.MaterialButton;

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

    // NOWE POLA
    private ImageButton btnFavorite;
    private boolean isFavorite = false;
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;
    private BroadcastReceiver syncStatusReceiver;
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

        // NOWA INICJALIZACJA
        httpClient = new OkHttpClient();
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);

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

        // ⭐️⭐️⭐️ POPRAWKA BŁĘDU 1 ⭐️⭐️⭐️
        // Usunięto błędny tag z tej linii
        btnFavorite = findViewById(R.id.btnFavorite);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        appearanceManager.applyIconScale(btnSettings);
        appearanceManager.applyIconScale(btnBack);

        //appearanceManager.applyIconScale(btnFavorite); // Możesz też dodać skalowanie dla tego

        btnRefresh.setOnClickListener(v -> forceReadingsSync());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnBack.setOnClickListener(v -> finish());

        // LISTENER DLA ULUBIONYCH
        btnFavorite.setOnClickListener(v -> toggleFavoriteStatus());

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

        // SPRAWDŹ STATUS ULUBIONYCH
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

                    // Prosty debounce, aby uniknąć podwójnych Toastów (limit 1 na 3 sekundy)
                    if (now - lastSyncToastTime < 3000) {
                        Log.d("SensorDetailActivity", "SyncStatusReceiver: Zignorowano zduplikowany broadcast o sukcesie.");
                        return; // Zignoruj ten broadcast, jest za wcześnie
                    }

                    if (success) {
                        Toast.makeText(context, R.string.sync_success, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, R.string.sync_error, Toast.LENGTH_SHORT).show();
                    }

                    // Zapisz czas ostatniego Toasta
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


        if (syncStatusReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStatusReceiver);
        }

    }

    // NOWA METODA
    private void checkFavoriteStatus() {
        isFavorite = dbHelper.isFavoriteSensor(sensorIdLong);
        updateFavoriteIcon(); // Wywołaj nową metodę
    }

    // ⭐️ ZAKTUALIZOWANA METODA ⭐️
    private void toggleFavoriteStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Optymistyczna aktualizacja UI
        final boolean becomingFavorite = !isFavorite;
        isFavorite = becomingFavorite;
        updateFavoriteIcon(); // ⭐️ POPRAWKA: Bezpośrednio aktualizuj ikonę

        String url = Constants.FAVORITE_SENSORS_ENDPOINT;
        Request request;

        if (becomingFavorite) {
            // DODAJ DO ULUBIONYCH
            JSONObject json = new JSONObject();
            try { json.put("id", sensorIdLong); } catch (Exception e) {}
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();
        } else {
            // USUŃ Z ULUBIONYCH
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
                    // 2. Wycofaj zmianę UI w razie błędu
                    isFavorite = !becomingFavorite;
                    updateFavoriteIcon(); // ⭐️ POPRAWKA ⭐️
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SensorDetailActivity.this,
                                becomingFavorite ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites,
                                Toast.LENGTH_SHORT).show();


                        // 3. Wymuś pełną (CICHĄ) synchronizację w tle
                        Intent serviceIntent = new Intent(SensorDetailActivity.this, VpsClientService.class);
                        serviceIntent.putExtra("FORCE_SYNC_NOW", true);

                        serviceIntent.putExtra("IS_SILENT", true);
                        startService(serviceIntent);
                    } else {
                        Toast.makeText(SensorDetailActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show();
                        // 4. Wycofaj zmianę UI w razie błędu serwera
                        isFavorite = !becomingFavorite;
                        updateFavoriteIcon(); // ⭐️ POPRAWKA ⭐️
                    }
                });
                response.close();
            }
        });
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

                        // ⭐️⭐️⭐️ POPRAWKA BŁĘDU 2 ⭐️⭐️⭐️
                        // Zmieniono 'getColumnIndexOrTry' na 'getColumnIndexOrThrow'
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
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        // Używamy nowego extra "FORCE_READINGS_NOW"
        serviceIntent.putExtra("FORCE_READINGS_NOW", true);
        startService(serviceIntent);

        // NIE pokazujemy Toast "sync_started" tutaj - poczekamy na odpowiedź serwisu
    }
    /**
     * Aktualizuje WYGLĄD ikony na podstawie aktualnego stanu zmiennej 'isFavorite'.
     */
    private void updateFavoriteIcon() {
        if (btnFavorite != null) {
            btnFavorite.setImageResource(isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        }
    }
}