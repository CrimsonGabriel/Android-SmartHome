package com.testserwera.bazunia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

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

// ZMIANA: BaseActivity
public class MainActivity extends BaseActivity {

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

    // ZMIANA: Usunięto attachBaseContext (BaseActivity to robi)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Inicjalizacja Managera do śledzenia zmian w onResume
        appearanceManager = new AppearanceManager(this);
        currentTextScale = appearanceManager.getTextScale();
        currentButtonScale = appearanceManager.getButtonScale();

        // ZMIANA: Usunięto ręczne applyAppearance

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

        // Sprawdzenie czy zmieniła się skala (np. powrót z SettingsActivity)
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
            if (isSensorValueInAlertState(sensor)) {
                alertCount++;
            }
            if (sensor.batteryLevel > 0 && sensor.batteryLevel <= 20) {
                batteryLowCount++;
            }
        }

        if (alertCount > 0) {
            textAlertSummary.setText(String.format(Locale.getDefault(),
                    getString(R.string.alert_summary),
                    alertCount,
                    getPolishSensorSuffix(alertCount)));
            cardAlerts.setVisibility(View.VISIBLE);
        } else {
            cardAlerts.setVisibility(View.GONE);
        }

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
        boolean hasThresholds = thresholdManager.isThresholdSupported(sensor.type);

        try {
            float val = Float.parseFloat(sensor.value);

            if (hasThresholds) {
                Pair<Float, Float> defRange = thresholdManager.getDefaultRangeForType(sensor.type);
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defRange.first);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defRange.second);
                return val < min || val > max;

            } else {
                if ("flow".equalsIgnoreCase(sensor.type)) {
                    return val > 0.0f;
                }
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

    private void performHomeCheck() {
        showRiskBottomSheet(null);

        new Thread(() -> {
            try {
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

                JSONObject root = new JSONObject();
                root.put("isSafe", riskCount == 0);
                root.put("riskCount", riskCount);
                root.put("risks", risksArray);

                String finalJson = root.toString();

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

    private JSONObject checkSensorRiskLocally(SensorModel sensor) {
        boolean hasThresholds = thresholdManager.isThresholdSupported(sensor.type);

        try {
            float val = Float.parseFloat(sensor.value);

            if (hasThresholds) {
                Pair<Float, Float> defRange = thresholdManager.getDefaultRangeForType(sensor.type);
                float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defRange.first);
                float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defRange.second);

                if (val < min) {
                    return createRiskJson(sensor, "low", String.format(Locale.getDefault(), "%.1f", min));
                } else if (val > max) {
                    return createRiskJson(sensor, "high", String.format(Locale.getDefault(), "%.1f", max));
                }

            } else {
                if ("flow".equalsIgnoreCase(sensor.type)) {
                    if (val > 0.0f) return createRiskJson(sensor, "flow", null);
                }
                else {
                    if (val > 0.5f) return createRiskJson(sensor, "active", null);
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private JSONObject createRiskJson(SensorModel sensor, String issueType, String limitVal) {
        try {
            JSONObject json = new JSONObject();
            json.put("sensorName", sensor.name != null ? sensor.name : sensor.type);
            json.put("sensorValue", sensor.value);
            json.put("sensorType", sensor.type);
            json.put("issueType", issueType);
            json.put("limitVal", limitVal);
            return json;
        } catch (JSONException e) { return null; }
    }

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
                holder.issue.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorRisk));
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