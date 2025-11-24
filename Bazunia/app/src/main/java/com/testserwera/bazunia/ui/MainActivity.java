package com.testserwera.bazunia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.data.DatabaseHelper;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.SensorModel;
import com.testserwera.bazunia.data.ThresholdManager;
import com.testserwera.bazunia.data.VpsClientService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import androidx.appcompat.app.AlertDialog;
import com.testserwera.bazunia.utils.LocaleManager;
import android.util.Pair;
import androidx.credentials.CredentialManager;
import androidx.credentials.ClearCredentialStateRequest;
import android.os.CancellationSignal;

import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private BottomSheetDialog riskSheetDialog;
    private AppearanceManager appearanceManager;
    private String currentTextScale;
    private String currentButtonScale;
    private ThresholdManager thresholdManager;

    private MaterialCardView cardAlerts;
    private TextView textAlertSummary;

    private CredentialManager credentialManager;

    private MaterialCardView cardBattery;
    private TextView textBatterySummary;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d("MainActivity", "Zgoda na powiadomienia przyznana.");
                } else {
                    Log.w("MainActivity", "Użytkownik odmówił zgody na powiadomienia.");
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = new LocaleManager(newBase);
        super.attachBaseContext(localeManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        currentTextScale = appearanceManager.getTextScale();
        currentButtonScale = appearanceManager.getButtonScale();
        appearanceManager.applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        askNotificationPermission();
        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);

        cardAlerts = findViewById(R.id.cardAlerts);
        textAlertSummary = findViewById(R.id.textAlertSummary);
        cardBattery = findViewById(R.id.cardBattery);
        textBatterySummary = findViewById(R.id.textBatterySummary);
        MaterialCardView cardAllSensors = findViewById(R.id.cardAllSensors);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        appearanceManager.applyIconScale(btnSettings);
        appearanceManager.applyIconScale(btnLogout);

        credentialManager = CredentialManager.create(this);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnLogout.setOnClickListener(v -> showLogoutDialog());

        cardAllSensors.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, DataActivity.class)));

        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabCheck
                = findViewById(R.id.fabCheckHome);

        fabCheck.setOnClickListener(v -> performHomeCheck());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.logout_confirmation_title))
                .setMessage(getString(R.string.logout_confirmation_message))
                .setIcon(R.drawable.ic_logout)
                .setPositiveButton(getString(R.string.logout_positive_button), (dialog, which) -> performCredentialManagerLogout())
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show();
    }

    private void performCredentialManagerLogout() {
        ClearCredentialStateRequest request = new ClearCredentialStateRequest();
        CancellationSignal cancellationSignal = new CancellationSignal();
        Executor executor = Executors.newSingleThreadExecutor();

        credentialManager.clearCredentialStateAsync(
                request,
                cancellationSignal,
                executor,
                new androidx.credentials.CredentialManagerCallback<>() {
                    @Override
                    public void onResult(Void result) {
                        runOnUiThread(() -> stopServiceAndGoToLogin());
                    }

                    @Override
                    public void onError(@androidx.annotation.NonNull androidx.credentials.exceptions.ClearCredentialException e) {
                        Log.e("MainActivity", "Credential Manager: Błąd", e);
                        runOnUiThread(() -> stopServiceAndGoToLogin());
                    }
                }
        );
    }

    private void stopServiceAndGoToLogin() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        stopService(serviceIntent);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

        loadDashboardData();
    }

    private void loadDashboardData() {
        List<SensorModel> latestData = dbHelper.getLatestUniqueSensorData();

        int alertCount = 0;
        int batteryLowCount = 0;

        for (SensorModel sensor : latestData) {
            // 1. Sprawdzanie Stanu Alarmowego (Threshold/Binary)
            if (isSensorValueInAlertState(sensor)) {
                alertCount++;
            }

            // 2. Sprawdzanie Baterii (Niezależnie od alarmu)
            if (sensor.batteryLevel > 0 && sensor.batteryLevel <= 20) {
                batteryLowCount++;
            }
        }

        // --- OBSŁUGA KARTY ALARMÓW (CZERWONA) ---
        if (alertCount > 0) {
            textAlertSummary.setText(String.format(Locale.getDefault(),
                    getString(R.string.alert_summary),
                    alertCount,
                    getPolishSensorSuffix(alertCount)));
            cardAlerts.setVisibility(View.VISIBLE);
        } else {
            cardAlerts.setVisibility(View.GONE);
        }

        // --- OBSŁUGA KARTY BATERII (POMARAŃCZOWA) ---
        if (batteryLowCount > 0) {
            textBatterySummary.setText(String.format(Locale.getDefault(),
                    getString(R.string.battery_summary),
                    batteryLowCount,
                    getPolishSensorSuffix(batteryLowCount)));
            cardBattery.setVisibility(View.VISIBLE);
        } else {
            cardBattery.setVisibility(View.GONE);
        }
    }

    private boolean isSensorValueInAlertState(SensorModel sensor) {
        // Sprawdzamy, czy typ ma suwaki (Analogowy) czy jest binarny (ON/OFF)
        boolean hasThresholds = thresholdManager.isThresholdSupported(sensor.type);

        try {
            float val = Float.parseFloat(sensor.value);

            if (hasThresholds) {
                // --- 1. LOGIKA DLA ANALOGOWYCH ---
                Pair<Float, Float> defRange = thresholdManager.getDefaultRangeForType(sensor.type);
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defRange.first);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defRange.second);
                return val < min || val > max;

            } else {
                // --- 2. LOGIKA DLA BINARNYCH ---
                // Specjalny przypadek dla FLOW (Przepływ) > 0.0 -> ALARM
                if ("flow".equalsIgnoreCase(sensor.type)) {
                    return val > 0.0f;
                }
                // Reszta > 0.5 -> ALARM
                return val > 0.5f;
            }

        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getPolishSensorSuffix(int count) {
        if (count == 1) return getString(R.string.sensor_suffix_one);
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;
        if (count > 1 && lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14) ) {
            return getString(R.string.sensor_suffix_few);
        }
        return getString(R.string.sensor_suffix_many);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // 1. Zastąp metodę performHomeCheck tą wersją (LOKALNA LOGIKA):
    private void performHomeCheck() {
        // Pokaż loader
        showRiskBottomSheet(null);

        // Uruchamiamy wątek w tle, żeby nie mrozić UI przy liczeniu
        new Thread(() -> {
            try {
                // 1. Pobierz wszystkie najnowsze dane lokalnie
                List<SensorModel> sensors = dbHelper.getLatestUniqueSensorData();
                JSONArray risksArray = new JSONArray();
                int riskCount = 0;

                for (SensorModel sensor : sensors) {
                    JSONObject riskItem = checkSensorRiskLocally(sensor);
                    if (riskItem != null) {
                        risksArray.put(riskItem);
                        riskCount++;
                    }
                }

                // 2. Budujemy wynikowy JSON (taki sam format jak kiedyś z serwera, ale lokalny)
                JSONObject root = new JSONObject();
                root.put("isSafe", riskCount == 0);
                root.put("riskCount", riskCount);
                root.put("risks", risksArray);

                String finalJson = root.toString();

                // 3. Wracamy na główny wątek wyświetlić wynik
                runOnUiThread(() -> showRiskBottomSheet(finalJson));

            } catch (Exception e) {
                Log.e("HomeCheck", "Błąd lokalnego sprawdzania", e);
                runOnUiThread(() -> {
                    if (riskSheetDialog != null) riskSheetDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Błąd weryfikacji danych", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // 2. Metoda pomocnicza do analizy pojedynczego czujnika
    private JSONObject checkSensorRiskLocally(SensorModel sensor) {
        // Logika identyczna jak w liczniku na Dashboardzie!
        boolean hasThresholds = thresholdManager.isThresholdSupported(sensor.type);

        try {
            float val = Float.parseFloat(sensor.value);

            if (hasThresholds) {
                // --- ANALOGOWE (Temp, Lux, Wilgotność) ---
                Pair<Float, Float> defRange = thresholdManager.getDefaultRangeForType(sensor.type);
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defRange.first);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defRange.second);

                if (val < min) {
                    return createRiskJson(sensor, "low", String.format(Locale.getDefault(), "%.1f", min));
                } else if (val > max) {
                    return createRiskJson(sensor, "high", String.format(Locale.getDefault(), "%.1f", max));
                }

            } else {
                // --- BINARNE (Światło, Ruch, Drzwi, Flow) ---

                // Specjalny dla FLOW
                if ("flow".equalsIgnoreCase(sensor.type)) {
                    if (val > 0.0f) return createRiskJson(sensor, "flow", null);
                }
                // Reszta (Light, Motion, Contact)
                else {
                    if (val > 0.5f) return createRiskJson(sensor, "active", null);
                }
            }
        } catch (NumberFormatException e) {
            // Ignorujemy błędy parsowania
        }
        return null; // Brak ryzyka
    }

    private JSONObject createRiskJson(SensorModel sensor, String issueType, String limitVal) {
        try {
            JSONObject json = new JSONObject();
            json.put("sensorName", sensor.name != null ? sensor.name : sensor.type);
            json.put("sensorValue", sensor.value); // Aktualna wartość
            json.put("sensorType", sensor.type);
            json.put("issueType", issueType); // low, high, active, flow
            json.put("limitVal", limitVal);   // Próg, który przekroczono (opcjonalne)
            return json;
        } catch (JSONException e) { return null; }
    }

    // 3. Zastąp showRiskBottomSheet (bez zmian w logice wyświetlania, tylko obsługa null)
    private void showRiskBottomSheet(String jsonResponse) {
        if (riskSheetDialog == null) {
            riskSheetDialog = new BottomSheetDialog(this);
            ViewGroup root = findViewById(android.R.id.content);
            View view = getLayoutInflater().inflate(R.layout.bottom_sheet_risk, root, false);
            riskSheetDialog.setContentView(view);
            riskSheetDialog.setOnDismissListener(dialog -> riskSheetDialog = null);
        }

        ImageView imgStatus = riskSheetDialog.findViewById(R.id.imgMainStatus);
        TextView tvTitle = riskSheetDialog.findViewById(R.id.tvMainTitle);
        TextView tvDesc = riskSheetDialog.findViewById(R.id.tvMainDesc);
        RecyclerView recycler = riskSheetDialog.findViewById(R.id.recyclerRisks);
        MaterialButton btnClose = riskSheetDialog.findViewById(R.id.btnCloseSheet);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                riskSheetDialog.dismiss();
                riskSheetDialog = null;
            });
        }

        if (jsonResponse == null) {
            // Loader
            if (tvTitle != null) tvTitle.setText(R.string.risk_status_check);
            if (tvDesc != null) tvDesc.setText(R.string.please_wait);
            if (imgStatus != null) imgStatus.setImageResource(R.drawable.ic_search);
            if (recycler != null) recycler.setVisibility(View.GONE);
        } else {
            try {
                JSONObject root = new JSONObject(jsonResponse);
                boolean isSafe = root.getBoolean("isSafe");
                int count = root.getInt("riskCount");

                if (isSafe) {
                    if (imgStatus != null) {
                        imgStatus.setImageResource(R.drawable.ic_check);
                        imgStatus.setColorFilter(ContextCompat.getColor(this, R.color.colorSafe));
                    }
                    if (tvTitle != null) tvTitle.setText(R.string.home_safe_title);
                    if (tvDesc != null) tvDesc.setText(R.string.home_safe_desc);
                    if (recycler != null) recycler.setVisibility(View.GONE);
                } else {
                    if (imgStatus != null) {
                        imgStatus.setImageResource(R.drawable.ic_warning);
                        imgStatus.setColorFilter(ContextCompat.getColor(this, R.color.colorRisk));
                    }
                    if (tvTitle != null) tvTitle.setText(R.string.home_risk_title);
                    if (tvDesc != null) tvDesc.setText(getString(R.string.home_risk_desc, count));

                    if (recycler != null) {
                        JSONArray risksArray = root.getJSONArray("risks");
                        recycler.setVisibility(View.VISIBLE);
                        recycler.setLayoutManager(new LinearLayoutManager(this));
                        recycler.setAdapter(new RiskAdapter(risksArray));
                    }
                }
            } catch (JSONException e) {
                Log.e("RiskCheck", "JSON Error", e);
            }
        }

        if (!riskSheetDialog.isShowing()) {
            riskSheetDialog.show();
        }
    }

    // 4. Zastąp RiskAdapter (Nowa logika wyświetlania tekstów na podstawie issueType)
    private class RiskAdapter extends RecyclerView.Adapter<RiskAdapter.RiskViewHolder> {
        private final JSONArray data;

        public RiskAdapter(JSONArray data) {
            this.data = data;
        }

        @androidx.annotation.NonNull
        @Override
        public RiskViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_risk, parent, false);
            return new RiskViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull RiskViewHolder holder, int position) {
            try {
                JSONObject item = data.getJSONObject(position);

                String name = item.optString("sensorName", "Czujnik");
                String val = item.optString("sensorValue", "?");
                String type = item.optString("sensorType", "").toLowerCase();
                String issue = item.optString("issueType", "");
                String limit = item.optString("limitVal", "");

                holder.name.setText(name);

                // Dobieramy ikonę i tekst
                int iconRes = R.drawable.ic_warning;
                String msg;

                switch (issue) {
                    case "high":
                        msg = getString(R.string.risk_msg_too_high, val, limit);
                        iconRes = R.drawable.ic_temp;
                        break;
                    case "low":
                        msg = getString(R.string.risk_msg_too_low, val, limit);
                        iconRes = R.drawable.ic_temp;
                        break;
                    case "flow":
                        msg = getString(R.string.risk_msg_flow);
                        iconRes = R.drawable.ic_flow;
                        break;
                    case "active":
                        if (type.contains("motion")) {
                            msg = getString(R.string.risk_msg_active);
                            iconRes = R.drawable.ic_motion;
                        } else if (type.contains("light") || type.contains("socket")) {
                            msg = getString(R.string.risk_msg_on);
                            iconRes = R.drawable.ic_light;
                        } else if (type.contains("door") || type.contains("window") || type.contains("contact")) {
                            msg = getString(R.string.risk_msg_open);
                            iconRes = R.drawable.ic_open;
                        } else {
                            msg = getString(R.string.risk_msg_active);
                        }
                        break;
                    default:
                        msg = "Problem: " + val;
                        break;
                }

                holder.issue.setText(msg);
                holder.icon.setImageResource(iconRes);

                // Kolor tekstu zostawiamy czerwony (ostrzegawczy)
                holder.issue.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorRisk));

                // ⭐️ POPRAWKA: Nie ruszamy koloru ikony (zostaje oryginał)
                holder.icon.clearColorFilter();

            } catch (JSONException e) {
                Log.e("RiskAdapter", "Błąd parsowania", e);
            }
        }

        @Override
        public int getItemCount() {
            return data.length();
        }

        class RiskViewHolder extends RecyclerView.ViewHolder {
            TextView name, issue;
            ImageView icon;
            RiskViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvRiskSensorName);
                issue = itemView.findViewById(R.id.tvRiskIssue);
                icon = itemView.findViewById(R.id.imgRiskIcon);
            }
        }
    }
}