package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.util.Log;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.testserwera.bazunia.data.VpsClientService;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.CleanupManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.LocaleManager;
import com.testserwera.bazunia.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText; // ⭐️ DODANY IMPORT

// ⭐️ DODANE IMPORTY SIECIOWE ⭐️
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // Zmienne Wyglądu
    private AppearanceManager appearanceManager;
    private LocaleManager localeManager;
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;
    private MaterialButton btnGatewayCleanupSettings;
    private RadioGroup radioGroupLanguage;
    private CleanupManager cleanupManager;
    private com.google.android.material.textfield.TextInputEditText editCleanupDays;
    private com.google.android.material.textfield.TextInputEditText editCleanupSize;
    private TextInputEditText editGlobalInterval;
    private MaterialButton btnSaveGlobalInterval;
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;
    public static final String NOTIFICATION_PREFS = "NotificationSettings";
    public static final String KEY_GLOBAL_NOTIFICATION_INTERVAL = "global_notification_interval_minutes";

    private TextInputEditText editGlobalNotificationInterval;
    private MaterialButton btnSaveGlobalNotificationInterval;
    private SharedPreferences notificationPrefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager tempLocaleManager = new LocaleManager(newBase);
        super.attachBaseContext(tempLocaleManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        appearanceManager.applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        localeManager = new LocaleManager(this);

        // ⭐️ INICJALIZACJA SIECI ⭐️
        httpClient = new OkHttpClient();
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        notificationPrefs = getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE);
        // Inicjalizacja komponentów
        cleanupManager = new CleanupManager(this);
        setupViews();
        loadCurrentAppearanceSettings();
        setupAppearanceListeners();
        btnSaveGlobalInterval.setOnClickListener(v -> saveGlobalInterval());
        btnSaveGlobalNotificationInterval.setOnClickListener(v -> saveGlobalNotificationInterval());

        // 🔽🔽🔽 DODAJ WYWOŁANIE TEJ METODY 🔽🔽🔽
        loadCurrentNotificationSettings();

        // --- LOGIKA OBSŁUGI PRZYCISKÓW ---

        MaterialButton btnAccountSettings = findViewById(R.id.btnAccountSettings);
        btnAccountSettings.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, AccountSettingsActivity.class);
            startActivity(intent);
        });

        MaterialButton btnBackSettings = findViewById(R.id.btnBackSettings);
        btnBackSettings.setOnClickListener(v -> finish());

        btnGatewayCleanupSettings.setOnClickListener(v -> showGatewayCleanupDialog());

        // ⭐️ NOWY LISTENER ⭐️
        btnSaveGlobalInterval.setOnClickListener(v -> saveGlobalInterval());

    }

    private void setupViews() {
        // Znajdowanie widoków i przypisywanie do zmiennych składowych
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage);
        editCleanupDays = findViewById(R.id.editCleanupDays);
        editCleanupSize = findViewById(R.id.editCleanupSize);
        btnGatewayCleanupSettings = findViewById(R.id.btnGatewayCleanupSettings);

        // ⭐️ NOWE WIDOKI ⭐️
        editGlobalInterval = findViewById(R.id.editGlobalInterval);
        btnSaveGlobalInterval = findViewById(R.id.btnSaveGlobalInterval);
        editGlobalNotificationInterval = findViewById(R.id.editGlobalNotificationInterval);
        btnSaveGlobalNotificationInterval = findViewById(R.id.btnSaveGlobalNotificationInterval);
    }


    private void loadCurrentAppearanceSettings() {
        // --- Tryb Ciemny ---
        int currentTheme = appearanceManager.getTheme();
        switchTheme.setChecked(currentTheme == AppearanceManager.THEME_DARK);

        // --- Rozmiar Tekstu ---
        String currentTextScale = appearanceManager.getTextScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentTextScale)) {
            radioGroupTextScale.check(R.id.radioTextSmall);
        } else if (AppearanceManager.SCALE_LARGE.equals(currentTextScale)) {
            radioGroupTextScale.check(R.id.radioTextLarge);
        } else {
            radioGroupTextScale.check(R.id.radioTextMedium);
        }

        // --- Rozmiar Przycisków ---
        String currentButtonScale = appearanceManager.getButtonScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentButtonScale)) {
            radioGroupButtonScale.check(R.id.radioButtonSmall);
        } else if (AppearanceManager.SCALE_LARGE.equals(currentButtonScale)) {
            radioGroupButtonScale.check(R.id.radioButtonLarge);
        } else {
            radioGroupButtonScale.check(R.id.radioButtonMedium);
        }

        // --- Logika Języka ---
        String currentLanguage = localeManager.getLanguage();
        if (LocaleManager.LANGUAGE_POLISH.equals(currentLanguage)) {
            radioGroupLanguage.check(R.id.radioLanguagePolish);
        } else {
            radioGroupLanguage.check(R.id.radioLanguageEnglish);
        }

        // --- Czyszczenie Danych ---
        int cleanupDays = cleanupManager.getCleanupDays();
        if (cleanupDays > 0) {
            editCleanupDays.setText(String.valueOf(cleanupDays));
        } else {
            editCleanupDays.setText("");
            editCleanupDays.setHint(getString(R.string.cleanup_disabled_hint));
        }
        int cleanupSize = cleanupManager.getCleanupSize();
        if (cleanupSize > 0) {
            editCleanupSize.setText(String.valueOf(cleanupSize));
        } else {
            editCleanupSize.setText("");
        }
    }


    private void setupAppearanceListeners() {
        // Listener dla switchTheme
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int newTheme = isChecked ? AppearanceManager.THEME_DARK : AppearanceManager.THEME_LIGHT;
            if (appearanceManager.getTheme() != newTheme) {
                appearanceManager.saveTheme(newTheme);
                recreate();
            }
        });

        // Listener dla radioGroupTextScale
        radioGroupTextScale.setOnCheckedChangeListener((group, checkedId) -> {
            String newScale;
            if (checkedId == R.id.radioTextSmall) newScale = AppearanceManager.SCALE_SMALL;
            else if (checkedId == R.id.radioTextLarge) newScale = AppearanceManager.SCALE_LARGE;
            else newScale = AppearanceManager.SCALE_MEDIUM;
            if (!appearanceManager.getTextScale().equals(newScale)) {
                appearanceManager.saveTextScale(newScale);
                recreate();
            }
        });

        // Listener dla radioGroupButtonScale
        radioGroupButtonScale.setOnCheckedChangeListener((group, checkedId) -> {
            String newScale;
            if (checkedId == R.id.radioButtonSmall) newScale = AppearanceManager.SCALE_SMALL;
            else if (checkedId == R.id.radioButtonLarge) newScale = AppearanceManager.SCALE_LARGE;
            else newScale = AppearanceManager.SCALE_MEDIUM;
            if (!appearanceManager.getButtonScale().equals(newScale)) {
                appearanceManager.saveButtonScale(newScale);
                recreate();
            }
        });

        // Listener dla Języka
        radioGroupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String newLanguageCode;
            if (checkedId == R.id.radioLanguageEnglish) {
                newLanguageCode = LocaleManager.LANGUAGE_ENGLISH;
            } else {
                newLanguageCode = LocaleManager.LANGUAGE_POLISH;
            }

            if (!localeManager.getLanguage().equals(newLanguageCode)) {
                localeManager.saveLanguage(newLanguageCode);
                LocaleManager.languageChanged = true;
                recreate();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCleanupSettings();
    }

    private void saveCleanupSettings() {
        String input = editCleanupDays.getText() != null ? editCleanupDays.getText().toString() : "";
        int days = 0;
        if (!input.isEmpty()) {
            try {
                days = Integer.parseInt(input);
                if (days < 1) days = 0;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Nieprawidłowa wartość dni czyszczenia.");
            }
        }
        cleanupManager.saveCleanupDays(days);
        String inputSize = editCleanupSize.getText() != null ? editCleanupSize.getText().toString() : "";
        int size = 0;
        if (!inputSize.isEmpty()) {
            try {
                size = Integer.parseInt(inputSize);
                if (size < 0) size = 0;
            } catch (NumberFormatException e) { /* ignoruj */ }
        }
        cleanupManager.saveCleanupSize(size);
    }


    private void showGatewayCleanupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.cleanup_gateway_dialog_title))
                .setMessage(getString(R.string.cleanup_gateway_dialog_message))
                .setPositiveButton(getString(R.string.cleanup_gateway_go_to_web), (dialog, which) -> {
                    Toast.makeText(this, getString(R.string.cleanup_gateway_web_toast), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show();
    }

    /**
     * Wczytuje zapisany globalny interwał powiadomień i ustawia pole tekstowe.
     */
    private void loadCurrentNotificationSettings() {
        int savedInterval = notificationPrefs.getInt(KEY_GLOBAL_NOTIFICATION_INTERVAL, 0); // 0 = wyłączone
        if (savedInterval > 0) {
            editGlobalNotificationInterval.setText(String.valueOf(savedInterval));
        } else {
            editGlobalNotificationInterval.setText("");
        }
    }

    // 🔽🔽🔽 DODAJ TĘ NOWĄ METODĘ 🔽🔽🔽
    /**
     * Zapisuje globalny interwał powiadomień do SharedPreferences.
     */
    /**
     * Zapisuje globalny interwał powiadomień do SharedPreferences.
     */
    private void saveGlobalNotificationInterval() {
        String intervalStr = editGlobalNotificationInterval.getText() != null ? editGlobalNotificationInterval.getText().toString() : "";
        int intervalToSave = 0; // Domyślnie 0 (bez limitu)

        if (!intervalStr.isEmpty()) {
            try {
                intervalToSave = Integer.parseInt(intervalStr);
                if (intervalToSave <= 0) {
                    intervalToSave = 0;
                }
            } catch (NumberFormatException e) {
                editGlobalNotificationInterval.setError("Nieprawidłowa liczba");
                return;
            }
        }

        notificationPrefs.edit()
                .putInt(KEY_GLOBAL_NOTIFICATION_INTERVAL, intervalToSave)
                .apply();

        Toast.makeText(this, R.string.toast_global_notification_interval_saved, Toast.LENGTH_SHORT).show();

        // 🔽🔽🔽 POPRAWIONY BLOK 🔽🔽🔽
        // Ukryj klawiaturę (opcjonalne, ale miłe)
        try {
            // Używamy prostej nazwy klasy dzięki importowi
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
            // ignoruj
        }
        // 🔼🔼🔼 KONIEC POPRAWKI 🔼🔼🔼
    }

    // ⭐️ ⭐️ ⭐️ NOWA METODA (Z POPRAWKĄ String.format) ⭐️ ⭐️ ⭐️
    /**
     * Wysyła żądanie do backendu, aby ustawił globalny interwał.
     */
    private void saveGlobalInterval() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        String intervalStr = editGlobalInterval.getText() != null ? editGlobalInterval.getText().toString() : "";
        int intervalToSend = 60; // Domyślnie 60, jeśli pole jest puste

        if (!intervalStr.isEmpty()) {
            try {
                intervalToSend = Integer.parseInt(intervalStr);
                if (intervalToSend <= 5) { // Ustawmy twardy limit minimalny
                    intervalToSend = 5;
                    editGlobalInterval.setText("5");
                }
            } catch (NumberFormatException e) {
                editGlobalInterval.setError("Nieprawidłowa liczba");
                return;
            }
        } else {
            editGlobalInterval.setText("60");
        }

        Toast.makeText(this, R.string.toast_global_interval_updating, Toast.LENGTH_SHORT).show();

        // Używamy endpointu, który dodaliśmy do AdminController
        String url = Constants.VPS_SERVER_IP + "/api/sensors/interval/global";

        // Tworzymy JSON: {"interval": 120}
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("interval", intervalToSend);
        } catch (Exception e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body) // Używamy POST
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        // ⭐️ POPRAWKA 1 ⭐️
                        getString(R.string.toast_api_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        try {
                            // Próbujemy sparsować odpowiedź, aby dostaGć liczbę zaktualizowanych
                            JSONObject json = new JSONObject(responseBody);
                            // Używamy "updatedCount" - klucz, który zdefiniowaliśmy w AdminController
                            int updatedCount = json.optInt("updatedCount", 0);
                            Toast.makeText(SettingsActivity.this,
                                    String.format(getString(R.string.toast_global_interval_success), updatedCount),
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            // Jeśli odpowiedź jest inna (np. błąd), pokażmy ją
                            Toast.makeText(SettingsActivity.this, responseBody, Toast.LENGTH_LONG).show();
                        }

                        // Wymuś cichą synchronizację definicji, aby RPi pobrało zmiany
                        // A Android zaktualizował lokalną DB
                        Intent serviceIntent = new Intent(SettingsActivity.this, VpsClientService.class);
                        serviceIntent.putExtra("FORCE_SYNC_NOW", true);
                        serviceIntent.putExtra("IS_SILENT", true);
                        startService(serviceIntent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            // ⭐️ POPRAWKA 2 ⭐️
                            getString(R.string.toast_api_error) + ": " + responseBody, Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }
}