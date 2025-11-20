package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.VpsClientService;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.CleanupManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.LocaleManager;

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
    public static final String NOTIFICATION_PREFS = "NotificationSettings";
    public static final String KEY_GLOBAL_NOTIFICATION_INTERVAL = "global_notification_interval_minutes";

    // --- Zmienne Wyglądu ---
    private AppearanceManager appearanceManager;
    private LocaleManager localeManager;
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;
    private RadioGroup radioGroupLanguage;

    // --- Zmienne Lokalne (Czyszczenie telefonu) ---
    private CleanupManager cleanupManager;
    private TextInputEditText editCleanupDays;
    private TextInputEditText editCleanupSize;

    // --- Zmienne Zdalne (Serwer / Retencja) ---
    private MaterialButton btnRequestRetentionChange;
    private TextView txtCurrentServerSettings;

    // --- Zmienne Globalne Interwały ---
    private TextInputEditText editGlobalInterval;
    private MaterialButton btnSaveGlobalInterval;
    private TextInputEditText editGlobalNotificationInterval;
    private MaterialButton btnSaveGlobalNotificationInterval;

    // --- Inne ---
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;
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
        httpClient = new OkHttpClient();
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        notificationPrefs = getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE);
        cleanupManager = new CleanupManager(this);

        setupViews();

        // Ładowanie ustawień
        loadCurrentAppearanceSettings();
        loadCurrentNotificationSettings();

        // ⭐️ Pobieranie statusu retencji z serwera ⭐️
        fetchCurrentRetentionStatus();

        setupAppearanceListeners();
        setupClickListeners();
    }

    private void setupViews() {
        // Wygląd
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage);

        // Lokalne czyszczenie
        editCleanupDays = findViewById(R.id.editCleanupDays);
        editCleanupSize = findViewById(R.id.editCleanupSize);

        // Serwer - Retencja (Nowe pola)
        btnRequestRetentionChange = findViewById(R.id.btnRequestRetentionChange);
        txtCurrentServerSettings = findViewById(R.id.txtCurrentServerSettings);

        // Interwały Globalne
        editGlobalInterval = findViewById(R.id.editGlobalInterval);
        btnSaveGlobalInterval = findViewById(R.id.btnSaveGlobalInterval);
        editGlobalNotificationInterval = findViewById(R.id.editGlobalNotificationInterval);
        btnSaveGlobalNotificationInterval = findViewById(R.id.btnSaveGlobalNotificationInterval);
    }

    private void setupClickListeners() {
        // Obsługa przycisków zapisu interwałów
        btnSaveGlobalInterval.setOnClickListener(v -> saveGlobalInterval());
        btnSaveGlobalNotificationInterval.setOnClickListener(v -> saveGlobalNotificationInterval());

        // Obsługa przycisku wniosku o retencję (NOWE)
        if (btnRequestRetentionChange != null) {
            btnRequestRetentionChange.setOnClickListener(v -> showRetentionRequestDialog());
        }

        // Nawigacja
        MaterialButton btnAccountSettings = findViewById(R.id.btnAccountSettings);
        if (btnAccountSettings != null) {
            btnAccountSettings.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, AccountSettingsActivity.class);
                startActivity(intent);
            });
        }

        MaterialButton btnBackSettings = findViewById(R.id.btnBackSettings);
        if (btnBackSettings != null) {
            btnBackSettings.setOnClickListener(v -> finish());
        }
    }

    // ============================================================
    // ⭐️ LOGIKA RETENCJI DANYCH (SERVER RETENTION POLICY) ⭐️
    // ============================================================

    /**
     * 1. Pobiera aktualny stan z serwera (obecne ustawienia + status wniosku).
     */
    private void fetchCurrentRetentionStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        // Adres endpointu
        String url = Constants.VPS_SERVER_IP + "/api/retention/status";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Błąd sieci - ignorujemy cicho lub logujemy
                Log.e(TAG, "Błąd pobierania statusu retencji: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String days = json.optString("currentDays", "0");
                        String size = json.optString("currentSize", "0");
                        String status = json.optString("status", "NONE");

                        // Formatowanie tekstu
                        // np. "Obecnie na serwerze: 30 dni, 50000 rekordów."
                        final String displayText = String.format(getString(R.string.cleanup_current_settings), days, size);

                        // Jeśli wniosek jest PENDING, dodaj dopisek
                        final String statusText = "PENDING".equals(status) ? "\n(" + getString(R.string.cleanup_request_status_pending) + ")" : "";

                        runOnUiThread(() -> {
                            if (txtCurrentServerSettings != null) {
                                txtCurrentServerSettings.setText(displayText + statusText);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * 2. Wyświetla dialog z polami do wpisania propozycji.
     */
    private void showRetentionRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Tworzymy layout programowo (LinearLayout z dwoma polami)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final TextInputEditText inputDays = new TextInputEditText(this);
        inputDays.setHint(getString(R.string.cleanup_request_days_label));
        inputDays.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputDays);

        final TextInputEditText inputSize = new TextInputEditText(this);
        inputSize.setHint(getString(R.string.cleanup_request_size_label));
        inputSize.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputSize);

        builder.setView(layout)
                .setTitle(getString(R.string.cleanup_request_dialog_title))
                .setMessage(getString(R.string.cleanup_request_dialog_message))
                .setPositiveButton(getString(R.string.cleanup_request_send_button), (dialog, which) -> {
                    String days = inputDays.getText() != null ? inputDays.getText().toString() : "";
                    String size = inputSize.getText() != null ? inputSize.getText().toString() : "";
                    sendRetentionRequest(days, size);
                })
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show();
    }

    /**
     * 3. Wysyła wniosek JSON do serwera.
     */
    private void sendRetentionRequest(String days, String size) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        JSONObject json = new JSONObject();
        try {
            if (!days.isEmpty()) json.put("days", days);
            if (!size.isEmpty()) json.put("size", size);
        } catch (Exception e) { return; }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        String url = Constants.VPS_SERVER_IP + "/api/retention/request";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_api_error), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SettingsActivity.this, getString(R.string.cleanup_request_sent_toast), Toast.LENGTH_LONG).show();
                        // Odśwież status od razu, żeby zobaczyć "PENDING"
                        fetchCurrentRetentionStatus();
                    } else {
                        Toast.makeText(SettingsActivity.this, getString(R.string.toast_api_error), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ============================================================
    // POZOSTAŁE METODY (WYGLĄD, JĘZYK, INTERWAŁY)
    // ============================================================

    private void loadCurrentAppearanceSettings() {
        // --- Tryb Ciemny ---
        int currentTheme = appearanceManager.getTheme();
        switchTheme.setChecked(currentTheme == AppearanceManager.THEME_DARK);

        // --- Rozmiar Tekstu ---
        String currentTextScale = appearanceManager.getTextScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentTextScale)) radioGroupTextScale.check(R.id.radioTextSmall);
        else if (AppearanceManager.SCALE_LARGE.equals(currentTextScale)) radioGroupTextScale.check(R.id.radioTextLarge);
        else radioGroupTextScale.check(R.id.radioTextMedium);

        // --- Rozmiar Przycisków ---
        String currentButtonScale = appearanceManager.getButtonScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentButtonScale)) radioGroupButtonScale.check(R.id.radioButtonSmall);
        else if (AppearanceManager.SCALE_LARGE.equals(currentButtonScale)) radioGroupButtonScale.check(R.id.radioButtonLarge);
        else radioGroupButtonScale.check(R.id.radioButtonMedium);

        // --- Język ---
        String currentLanguage = localeManager.getLanguage();
        if (LocaleManager.LANGUAGE_POLISH.equals(currentLanguage)) radioGroupLanguage.check(R.id.radioLanguagePolish);
        else radioGroupLanguage.check(R.id.radioLanguageEnglish);

        // --- Czyszczenie Lokalne ---
        int cleanupDays = cleanupManager.getCleanupDays();
        if (cleanupDays > 0) editCleanupDays.setText(String.valueOf(cleanupDays));
        else {
            editCleanupDays.setText("");
            editCleanupDays.setHint(getString(R.string.cleanup_disabled_hint));
        }
        int cleanupSize = cleanupManager.getCleanupSize();
        if (cleanupSize > 0) editCleanupSize.setText(String.valueOf(cleanupSize));
        else editCleanupSize.setText("");
    }

    private void setupAppearanceListeners() {
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int newTheme = isChecked ? AppearanceManager.THEME_DARK : AppearanceManager.THEME_LIGHT;
            if (appearanceManager.getTheme() != newTheme) {
                appearanceManager.saveTheme(newTheme);
                recreate();
            }
        });

        radioGroupTextScale.setOnCheckedChangeListener((group, checkedId) -> {
            String newScale = AppearanceManager.SCALE_MEDIUM;
            if (checkedId == R.id.radioTextSmall) newScale = AppearanceManager.SCALE_SMALL;
            else if (checkedId == R.id.radioTextLarge) newScale = AppearanceManager.SCALE_LARGE;

            if (!appearanceManager.getTextScale().equals(newScale)) {
                appearanceManager.saveTextScale(newScale);
                recreate();
            }
        });

        radioGroupButtonScale.setOnCheckedChangeListener((group, checkedId) -> {
            String newScale = AppearanceManager.SCALE_MEDIUM;
            if (checkedId == R.id.radioButtonSmall) newScale = AppearanceManager.SCALE_SMALL;
            else if (checkedId == R.id.radioButtonLarge) newScale = AppearanceManager.SCALE_LARGE;

            if (!appearanceManager.getButtonScale().equals(newScale)) {
                appearanceManager.saveButtonScale(newScale);
                recreate();
            }
        });

        radioGroupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String newLanguageCode = (checkedId == R.id.radioLanguageEnglish) ? LocaleManager.LANGUAGE_ENGLISH : LocaleManager.LANGUAGE_POLISH;
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
            } catch (NumberFormatException e) { /* ignoruj */ }
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

    private void loadCurrentNotificationSettings() {
        int savedInterval = notificationPrefs.getInt(KEY_GLOBAL_NOTIFICATION_INTERVAL, 0);
        if (savedInterval > 0) editGlobalNotificationInterval.setText(String.valueOf(savedInterval));
        else editGlobalNotificationInterval.setText("");
    }

    private void saveGlobalNotificationInterval() {
        String intervalStr = editGlobalNotificationInterval.getText() != null ? editGlobalNotificationInterval.getText().toString() : "";
        int intervalToSave = 0;
        if (!intervalStr.isEmpty()) {
            try {
                intervalToSave = Integer.parseInt(intervalStr);
                if (intervalToSave <= 0) intervalToSave = 0;
            } catch (NumberFormatException e) {
                editGlobalNotificationInterval.setError("Nieprawidłowa liczba");
                return;
            }
        }
        notificationPrefs.edit().putInt(KEY_GLOBAL_NOTIFICATION_INTERVAL, intervalToSave).apply();
        Toast.makeText(this, R.string.toast_global_notification_interval_saved, Toast.LENGTH_SHORT).show();
        hideKeyboard();
    }

    private void saveGlobalInterval() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }
        String intervalStr = editGlobalInterval.getText() != null ? editGlobalInterval.getText().toString() : "";
        int intervalToSend = 60;
        if (!intervalStr.isEmpty()) {
            try {
                intervalToSend = Integer.parseInt(intervalStr);
                if (intervalToSend <= 5) { intervalToSend = 5; editGlobalInterval.setText("5"); }
            } catch (NumberFormatException e) {
                editGlobalInterval.setError("Nieprawidłowa liczba");
                return;
            }
        } else {
            editGlobalInterval.setText("60");
        }

        Toast.makeText(this, R.string.toast_global_interval_updating, Toast.LENGTH_SHORT).show();
        String url = Constants.VPS_SERVER_IP + "/api/sensors/interval/global";

        JSONObject jsonBody = new JSONObject();
        try { jsonBody.put("interval", intervalToSend); } catch (Exception e) { return; }
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_api_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            int updatedCount = json.optInt("updatedCount", 0);
                            Toast.makeText(SettingsActivity.this, String.format(getString(R.string.toast_global_interval_success), updatedCount), Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this, responseBody, Toast.LENGTH_LONG).show();
                        }
                        Intent serviceIntent = new Intent(SettingsActivity.this, VpsClientService.class);
                        serviceIntent.putExtra("FORCE_SYNC_NOW", true);
                        serviceIntent.putExtra("IS_SILENT", true);
                        startService(serviceIntent);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, getString(R.string.toast_api_error) + ": " + responseBody, Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) { /* ignoruj */ }
    }
}