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

// --- STARE IMPORTY USUNIĘTE (GoogleSignIn) ---
// Zamiast nich używamy Credential Manager:
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

import okhttp3.*;
import com.testserwera.bazunia.utils.Constants;
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

    // [NOWA ZMIENNA] Zamiast GoogleSignInClient używamy CredentialManager
    private CredentialManager credentialManager;

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
        MaterialCardView cardAllSensors = findViewById(R.id.cardAllSensors);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);

        appearanceManager.applyIconScale(btnSettings);
        appearanceManager.applyIconScale(btnLogout);

        // [NOWY KOD] Inicjalizacja CredentialManager
        // To jest nowoczesny odpowiednik starego klienta
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

    // [NOWA METODA] Wylogowanie za pomocą Credential Manager
    private void performCredentialManagerLogout() {
        // Nowoczesne wylogowanie (czyści stan zapamiętanych poświadczeń)
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
                        Log.d("MainActivity", "Credential Manager: Stan wyczyszczony pomyślnie.");
                        // Musimy wrócić na wątek główny, żeby dotknąć UI (startActivity)
                        runOnUiThread(() -> stopServiceAndGoToLogin());
                    }

                    @Override
                    public void onError(@androidx.annotation.NonNull androidx.credentials.exceptions.ClearCredentialException e) {
                        Log.e("MainActivity", "Credential Manager: Błąd czyszczenia stanu", e);
                        // Nawet jak wystąpi błąd (np. brak sieci), i tak wylogowujemy z apki lokalnie
                        runOnUiThread(() -> stopServiceAndGoToLogin());
                    }
                }
        );
    }

    private void stopServiceAndGoToLogin() {
        Log.d("MainActivity", "Zatrzymywanie serwisu i powrót do LoginActivity...");
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

        for (SensorModel sensor : latestData) {
            if (isSensorValueInAlertState(sensor)) {
                alertCount++;
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
    }

    private boolean isSensorValueInAlertState(SensorModel sensor) {
        if (getString(R.string.sensor_type_door_contact).equalsIgnoreCase(sensor.type)) {
            return getString(R.string.door_contact_open_value).equals(sensor.value);
        }

        boolean isHumidity = getString(R.string.sensor_type_humidity).equalsIgnoreCase(sensor.type);
        float defaultMin = isHumidity ? 5.0f : 18.0f;
        float defaultMax = isHumidity ? 30.0f : 22.0f;

        float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
        float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);

        try {
            float currentValue = Float.parseFloat(sensor.value);
            return currentValue < min || currentValue > max;
        } catch (NumberFormatException e) {
            Log.w("MainActivity", String.format(Locale.getDefault(),
                    getString(R.string.error_numeric_parse), sensor.value));
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
                Log.d("MainActivity", "Pytam o zgodę na powiadomienia...");
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d("MainActivity", "Zgoda na powiadomienia jest już przyznana.");
            }
        }
    }

    private void performHomeCheck() {
        showRiskBottomSheet(null);

        String jwtToken = getSharedPreferences(LoginActivity.AUTH_PREFS, MODE_PRIVATE)
                .getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_no_token, Toast.LENGTH_SHORT).show();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Constants.RISK_REPORT_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    Log.e("RiskCheck", "Błąd sieci: " + e.getMessage());
                    Toast.makeText(MainActivity.this,
                            getString(R.string.error_connection_prefix, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) throws java.io.IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    runOnUiThread(() -> showRiskBottomSheet(json));
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        Log.e("RiskCheck", "Błąd serwera: " + response.code() + " " + errorBody);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.error_server_prefix, String.valueOf(response.code())),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
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

                // 1. Nazwa czujnika
                holder.name.setText(item.optString("sensorName", "?"));

                // 2. Pobieramy typ ikony/problemu (np. "light", "window", "door_contact")
                String iconType = item.optString("iconType", "warning");

                // 3. --- TUTAJ JEST ZMIANA (TŁUMACZENIE) ---
                // Zamiast brać tekst z serwera, sprawdzamy typ i dajemy własny string
                switch (iconType) {
                    case "light":
                        holder.issue.setText(R.string.risk_issue_light);
                        break;
                    case "window":
                        holder.issue.setText(R.string.risk_issue_window);
                        break;
                    case "door":
                    case "door_contact":
                    case "contact":
                        // Obsługa różnych nazw dla drzwi/kontaktronów
                        holder.issue.setText(R.string.risk_issue_door);
                        break;
                    default:
                        // Jeśli to jakiś inny, nieznany typ, wyświetlamy to co przysłał serwer
                        // lub domyślny komunikat "Wykryto problem"
                        String serverMsg = item.optString("issue", "");
                        if (!serverMsg.isEmpty()) {
                            holder.issue.setText(serverMsg);
                        } else {
                            holder.issue.setText(R.string.risk_issue_default);
                        }
                        break;
                }

                // 4. Ustawianie Ikony (bez zmian)
                int iconRes = R.drawable.ic_warning;
                if ("window".equals(iconType) || "door".equals(iconType) || "door_contact".equals(iconType)) {
                    iconRes = R.drawable.ic_open;
                } else if ("light".equals(iconType)) {
                    iconRes = R.drawable.ic_light;
                }

                holder.icon.setImageResource(iconRes);

                // Kolor czerwony dla ostrzeżenia
                holder.issue.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorRisk));
                holder.icon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.colorRisk));

            } catch (JSONException e) {
                Log.e("RiskAdapter", "Błąd parsowania JSON w liście ryzyka", e);
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