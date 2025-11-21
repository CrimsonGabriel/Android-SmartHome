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
// Importy dla Google Sign Out
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import java.util.List;
import java.util.Locale;
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

    // [NOWA ZMIENNA] Klient Google potrzebny do wylogowania
    private GoogleSignInClient mGoogleSignInClient;

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
        // [POPRAWNE WYWOŁANIE] Zapewnia, że kontekst z nowym językiem jest ustawiony ZAWSZE przed onCreate.
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

        // [NOWY KOD] Inicjalizuj klienta Google (tak samo jak w LoginActivity)
        // WAŻNE: Użyj tego samego WEB Client ID, co w LoginActivity i na serwerze!
        String webClientId = "79063316759-iva8uesd0vlj3in6eaeralk2kdkgv5or.apps.googleusercontent.com"; // Upewnij się, że to WEB ID!

        // <<< POPRAWKA: Użyj .requestIdToken() i webClientId >>>
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnLogout.setOnClickListener(v -> showLogoutDialog());

        cardAllSensors.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, DataActivity.class)));

        // Znajdź FAB
        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabCheck
                = findViewById(R.id.fabCheckHome);

        // Obsługa kliknięcia
        fabCheck.setOnClickListener(v -> performHomeCheck());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.logout_confirmation_title))
                .setMessage(getString(R.string.logout_confirmation_message))
                .setIcon(R.drawable.ic_logout)
                .setPositiveButton(getString(R.string.logout_positive_button), (dialog, which) -> {

                    // [NOWA LOGIKA] Wyloguj się z Google NAJPIERW!
                    if (mGoogleSignInClient != null) {
                        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                            Log.d("MainActivity", "Google Sign Out completed.");
                            // Dopiero PO wylogowaniu z Google, zatrzymaj serwis i wróć do logowania
                            stopServiceAndGoToLogin();
                        });
                    } else {
                        // Na wszelki wypadek, gdyby klient nie był zainicjowany
                        Log.w("MainActivity", "GoogleSignInClient nie został zainicjowany przed wylogowaniem.");
                        stopServiceAndGoToLogin();
                    }
                })
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show();
    }

    // [NOWA METODA POMOCNICZA] Wydzielona logika po wylogowaniu z Google
    private void stopServiceAndGoToLogin() {
        Log.d("MainActivity", "Zatrzymywanie serwisu i powrót do LoginActivity...");
        // Zatrzymuje serwis
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        stopService(serviceIntent);

        // Wraca do LoginActivity
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Zakończ MainActivity
    }


    @Override
    protected void onResume() {
        super.onResume();

        /// [NOWA POPRAWKA: Wymuszenie przeładowania dla zmiany języka]
        if (LocaleManager.languageChanged) {
            LocaleManager.languageChanged = false; // Resetowanie flagi po użyciu
            recreate(); // Wymuszenie ponownego stworzenia Aktywności z nowym kontekstem
            return; // Ważne, aby zakończyć, zanim przejdziemy do dalszych sprawdzeń lub ładowania danych
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
        // Poprawka dla polskich przypadków: 2,3,4 to "czujniki", reszta "czujników"
        int lastDigit = count % 10;
        int lastTwoDigits = count % 100;
        if (count > 1 && lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14) ) {
            return getString(R.string.sensor_suffix_few);
        }
        return getString(R.string.sensor_suffix_many);
    }

    // [NOWA METODA] Pyta o zgodę na powiadomienia na Androidzie 13+
    private void askNotificationPermission() {
        // Sprawdzamy, czy działamy na Androidzie 13 (API 33) lub nowszym
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            // Sprawdzamy, czy zgoda NIE JEST jeszcze przyznana
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {

                // Wyświetl systemowe okno dialogowe z prośbą o zgodę
                Log.d("MainActivity", "Pytam o zgodę na powiadomienia...");
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Zgoda jest już przyznana
                Log.d("MainActivity", "Zgoda na powiadomienia jest już przyznana.");
            }
        }
        // Na starszych wersjach Androida (poniżej 13) zgoda jest domyślnie przyznana
    }
    // 1. Metoda wywołująca API
    private void performHomeCheck() {
        // 1. Pokaż stan ładowania
        showRiskBottomSheet(null);

        String jwtToken = getSharedPreferences(LoginActivity.AUTH_PREFS, MODE_PRIVATE)
                .getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Toast.makeText(this, "Brak tokena! Zaloguj się ponownie.", Toast.LENGTH_SHORT).show();
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
                // 2. BŁĄD SIECI (np. serwer wyłączony, złe IP)
                runOnUiThread(() -> {
                    Log.e("RiskCheck", "Błąd sieci: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Błąd połączenia: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Tutaj moglibyśmy zamknąć sheet albo zmienić tekst na "Błąd"
                });
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) throws java.io.IOException {
                if (response.isSuccessful() && response.body() != null) {
                    // 3. SUKCES - parsujemy JSON
                    String json = response.body().string();
                    runOnUiThread(() -> showRiskBottomSheet(json)); // To otworzy NOWY sheet z danymi (lub zaktualizuje obecny jeśli przerobisz logikę)
                } else {
                    // 4. BŁĄD SERWERA (np. 403 Forbidden, 500 Error)
                    String errorBody = response.body() != null ? response.body().string() : "";
                    runOnUiThread(() -> {
                        Log.e("RiskCheck", "Błąd serwera: " + response.code() + " " + errorBody);
                        Toast.makeText(MainActivity.this, "Błąd serwera: " + response.code(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    // 2. Metoda wyświetlająca BottomSheet

    private void showRiskBottomSheet(String jsonResponse) {
        // 1. Jeśli dialog nie istnieje, stwórz go i załaduj layout
        if (riskSheetDialog == null) {
            riskSheetDialog = new BottomSheetDialog(this);
            View view = getLayoutInflater().inflate(R.layout.bottom_sheet_risk, null);
            riskSheetDialog.setContentView(view);

            // Ważne: Jak użytkownik zamknie palcem/swipe'm, czyścimy zmienną
            riskSheetDialog.setOnDismissListener(dialog -> riskSheetDialog = null);
        }

        // 2. Pobierz widoki z istniejącego dialogu (nie tworzymy nowych!)
        // Używamy findViewById na obiekcie dialogu, a nie view
        ImageView imgStatus = riskSheetDialog.findViewById(R.id.imgMainStatus);
        TextView tvTitle = riskSheetDialog.findViewById(R.id.tvMainTitle);
        TextView tvDesc = riskSheetDialog.findViewById(R.id.tvMainDesc);
        RecyclerView recycler = riskSheetDialog.findViewById(R.id.recyclerRisks);
        MaterialButton btnClose = riskSheetDialog.findViewById(R.id.btnCloseSheet);

        // Obsługa przycisku Zamknij
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                riskSheetDialog.dismiss(); // Zamyka okno
                riskSheetDialog = null;    // Czyści zmienną
            });
        }

        if (jsonResponse == null) {
            // --- STAN ŁADOWANIA ---
            if (tvTitle != null) tvTitle.setText(R.string.risk_status_check);
            if (tvDesc != null) tvDesc.setText(R.string.please_wait);
            if (imgStatus != null) imgStatus.setImageResource(R.drawable.ic_search); // Ikona lupy/ładowania
            if (recycler != null) recycler.setVisibility(View.GONE);
        } else {
            // --- STAN WYNIKÓW ---
            try {
                JSONObject root = new JSONObject(jsonResponse);
                boolean isSafe = root.getBoolean("isSafe");
                int count = root.getInt("riskCount");

                if (isSafe) {
                    // STAN OK (ZIELONY)
                    if (imgStatus != null) {
                        imgStatus.setImageResource(R.drawable.ic_check);
                        imgStatus.setColorFilter(ContextCompat.getColor(this, R.color.colorSafe));
                    }
                    if (tvTitle != null) tvTitle.setText(R.string.home_safe_title);
                    if (tvDesc != null) tvDesc.setText(R.string.home_safe_desc);
                    if (recycler != null) recycler.setVisibility(View.GONE);
                } else {
                    // STAN ZAGROŻENIA (CZERWONY)
                    if (imgStatus != null) {
                        imgStatus.setImageResource(R.drawable.ic_warning);
                        imgStatus.setColorFilter(ContextCompat.getColor(this, R.color.colorRisk));
                    }
                    if (tvTitle != null) tvTitle.setText(R.string.home_risk_title);
                    if (tvDesc != null) tvDesc.setText(getString(R.string.home_risk_desc, count));

                    // Parsowanie listy
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

        // 3. Pokaż okno, jeśli jeszcze nie jest widoczne
        if (!riskSheetDialog.isShowing()) {
            riskSheetDialog.show();
        }
    }

    // 3. Prosty Adapter wewnętrzny do listy (Inner Class w MainActivity)
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
                holder.name.setText(item.getString("sensorName"));
                holder.issue.setText(item.getString("issue"));

                // Prosta logika ikon (możesz rozbudować)
                String iconType = item.optString("iconType", "warning");
                int iconRes = R.drawable.ic_warning;
                if (iconType.equals("window")) iconRes = R.drawable.ic_open; // Upewnij się że masz ic_open lub ic_window
                if (iconType.equals("light")) iconRes = R.drawable.ic_light;

                holder.icon.setImageResource(iconRes);
                holder.icon.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.colorRisk));

            } catch (JSONException e) { e.printStackTrace(); }
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