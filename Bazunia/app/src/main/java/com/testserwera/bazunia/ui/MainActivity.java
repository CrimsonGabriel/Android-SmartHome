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

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
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
}