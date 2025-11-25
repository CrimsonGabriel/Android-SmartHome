package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
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
import com.google.android.material.textfield.TextInputLayout;
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

    private AppearanceManager appearanceManager;
    private LocaleManager localeManager;
    private CleanupManager cleanupManager;

    // UI Controls - Appearance
    private MaterialSwitch switchTheme;
    private RadioGroup radioGroupTextScale;
    private RadioGroup radioGroupButtonScale;
    private RadioGroup radioGroupLanguage;

    // UI Controls - Summaries
    private TextView txtRetentionSummary;

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
        loadCurrentAppearanceSettings();
        fetchCurrentRetentionStatus(); // To update the summary text
        setupAppearanceListeners();
        setupClickListeners();
    }

    private void setupViews() {
        // Appearance
        switchTheme = findViewById(R.id.switchThemeSettings);
        radioGroupTextScale = findViewById(R.id.radioGroupTextScale);
        radioGroupButtonScale = findViewById(R.id.radioGroupButtonScale);
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage);

        // Summaries
        txtRetentionSummary = findViewById(R.id.txtRetentionSummary);
    }

    private void setupClickListeners() {
        // 1. Global Interval (Server Sync)
        LinearLayout rowGlobalInterval = findViewById(R.id.rowGlobalInterval);
        rowGlobalInterval.setOnClickListener(v -> showGlobalIntervalDialog());

        // 2. Notification Interval
        LinearLayout rowNotificationInterval = findViewById(R.id.rowNotificationInterval);
        rowNotificationInterval.setOnClickListener(v -> showNotificationIntervalDialog());

        // 3. Cleanup & Retention
        LinearLayout rowCleanup = findViewById(R.id.rowCleanup);
        rowCleanup.setOnClickListener(v -> showCleanupDialog());

        // 4. Account Settings
        LinearLayout rowAccount = findViewById(R.id.rowAccountSettings);
        rowAccount.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, AccountSettingsActivity.class);
            startActivity(intent);
        });

        // 5. Sharing Settings
        LinearLayout rowSharing = findViewById(R.id.rowSharingSettings);
        rowSharing.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, GatewayShareActivity.class);
            startActivity(intent);
        });

        // Back Button
        MaterialButton btnBackSettings = findViewById(R.id.btnBackSettings);
        btnBackSettings.setOnClickListener(v -> finish());
    }

    // ============================================================
    //  DIALOGI I LOGIKA BIZNESOWA (Nowe podejście "Professional")
    // ============================================================

    /**
     * Shows a dialog to edit the Global Synchronization Interval.
     * Contains logic previously in saveGlobalInterval().
     */
    private void showGlobalIntervalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.settings_interval_title));
        builder.setMessage(getString(R.string.settings_interval_hint));

        // Create layout for dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.default_interval_value));

        // We will default to "60" or empty.
        input.setText(getString(R.string.default_interval_value));

        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.settings_interval_save_button), (dialog, which) -> {
            Editable text = input.getText();
            String value = (text != null) ? text.toString() : "";
            saveGlobalIntervalLogic(value);
        });
        builder.setNegativeButton(getString(R.string.dialog_cancel_button), null);
        builder.show();
    }

    private void saveGlobalIntervalLogic(String intervalStr) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.toast_error_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        int intervalToSend = 60;
        if (!intervalStr.isEmpty()) {
            try {
                intervalToSend = Integer.parseInt(intervalStr);
                if (intervalToSend <= 5) intervalToSend = 5;
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.error_invalid_number), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(this, R.string.toast_global_interval_updating, Toast.LENGTH_SHORT).show();
        String url = Constants.VPS_SERVER_IP + "/api/sensors/interval/global";

        JSONObject jsonBody = new JSONObject();
        try { jsonBody.put("interval", intervalToSend); } catch (Exception e) { Log.e(TAG, "JSON error", e); return; }
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        getString(R.string.toast_api_error_with_reason, e.getMessage()),
                        Toast.LENGTH_LONG).show());
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
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(R.string.toast_api_error_with_reason, responseBody),
                            Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    /**
     * Shows a dialog to edit the Notification Check Interval (Local preference).
     */
    private void showNotificationIntervalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.settings_notification_interval_title));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        // Load current value
        int savedInterval = notificationPrefs.getInt(KEY_GLOBAL_NOTIFICATION_INTERVAL, 0);
        if (savedInterval > 0) input.setText(String.valueOf(savedInterval));

        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.settings_notification_interval_save_button), (dialog, which) -> {
            Editable text = input.getText();
            String value = (text != null) ? text.toString() : "";
            saveGlobalNotificationIntervalLogic(value);
        });
        builder.setNegativeButton(getString(R.string.dialog_cancel_button), null);
        builder.show();
    }

    private void saveGlobalNotificationIntervalLogic(String intervalStr) {
        int intervalToSave = 0;
        if (!intervalStr.isEmpty()) {
            try {
                intervalToSave = Integer.parseInt(intervalStr);
                if (intervalToSave <= 0) intervalToSave = 0;
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.error_invalid_number), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        notificationPrefs.edit().putInt(KEY_GLOBAL_NOTIFICATION_INTERVAL, intervalToSave).apply();
        Toast.makeText(this, R.string.toast_global_notification_interval_saved, Toast.LENGTH_SHORT).show();
    }

    /**
     * Shows a complex dialog for Cleanup Settings (Local & Server Request).
     * This combines the local preferences and the server request button into one clean view.
     */
    private void showCleanupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.settings_data_cleanup_title));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);

        // --- LOCAL SETTINGS ---
        TextView headerLocal = new TextView(this);
        headerLocal.setText(getString(R.string.cleanup_local_label));
        headerLocal.setTextAppearance(androidx.appcompat.R.style.TextAppearance_AppCompat_Medium);

        // FIX: Resolve 'colorPrimary' programmatically instead of hardcoded resource
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        headerLocal.setTextColor(typedValue.data);

        layout.addView(headerLocal);

        final TextInputLayout daysLayout = new TextInputLayout(this);
        daysLayout.setHint(getString(R.string.cleanup_days_hint));
        daysLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final TextInputEditText inputDays = new TextInputEditText(this);
        inputDays.setInputType(InputType.TYPE_CLASS_NUMBER);
        // Load current
        int currentDays = cleanupManager.getCleanupDays();
        if (currentDays > 0) inputDays.setText(String.valueOf(currentDays));
        daysLayout.addView(inputDays);
        layout.addView(daysLayout);

        // Spacer
        layout.addView(new View(this), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20));

        final TextInputLayout sizeLayout = new TextInputLayout(this);
        sizeLayout.setHint(getString(R.string.cleanup_size_hint));
        sizeLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final TextInputEditText inputSize = new TextInputEditText(this);
        inputSize.setInputType(InputType.TYPE_CLASS_NUMBER);
        // Load current
        int currentSize = cleanupManager.getCleanupSize();
        if (currentSize > 0) inputSize.setText(String.valueOf(currentSize));
        sizeLayout.addView(inputSize);
        layout.addView(sizeLayout);

        // --- SERVER BUTTON ---
        // Spacer
        layout.addView(new View(this), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40));

        MaterialButton btnServerRequest = new MaterialButton(this);
        btnServerRequest.setText(getString(R.string.cleanup_gateway_label));
        btnServerRequest.setIconResource(R.drawable.ic_settings_server);
        btnServerRequest.setOnClickListener(v -> {
            // Close parent dialog and show the specific server request dialog
            // Note: we just stack the dialogs here, or we could dismiss 'dialog' first if we had reference
            showRetentionRequestDialog();
        });
        layout.addView(btnServerRequest);

        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.settings_interval_save_button), (dialog, which) -> {
            // FIX: Null check before calling toString()
            String daysStr = (inputDays.getText() != null) ? inputDays.getText().toString() : "";
            String sizeStr = (inputSize.getText() != null) ? inputSize.getText().toString() : "";

            saveCleanupSettingsLogic(daysStr, sizeStr);
        });
        builder.setNegativeButton(getString(R.string.dialog_cancel_button), null);

        builder.show();
    }

    private void saveCleanupSettingsLogic(String daysStr, String sizeStr) {
        int days = 0;
        if (daysStr != null && !daysStr.isEmpty()) {
            try {
                days = Integer.parseInt(daysStr);
            } catch (NumberFormatException e) {
                // Ignore invalid number, use 0
                Log.w(TAG, "Invalid cleanup days number: " + daysStr);
            }
        }
        cleanupManager.saveCleanupDays(days);

        int size = 0;
        if (sizeStr != null && !sizeStr.isEmpty()) {
            try {
                size = Integer.parseInt(sizeStr);
            } catch (NumberFormatException e) {
                // Ignore invalid number, use 0
                Log.w(TAG, "Invalid cleanup size number: " + sizeStr);
            }
        }
        cleanupManager.saveCleanupSize(size);

        // Refresh summary text on main screen
        fetchCurrentRetentionStatus();
        Toast.makeText(this, getString(R.string.toast_local_settings_saved), Toast.LENGTH_SHORT).show();
    }

    /**
     * Dialog specifically for the SERVER retention request (API call).
     */
    private void showRetentionRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.cleanup_request_dialog_title));
        builder.setMessage(getString(R.string.cleanup_request_dialog_message));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final TextInputEditText inputDays = new TextInputEditText(this);
        inputDays.setHint(getString(R.string.cleanup_request_days_label));
        inputDays.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputDays);

        final TextInputEditText inputSize = new TextInputEditText(this);
        inputSize.setHint(getString(R.string.cleanup_request_size_label));
        inputSize.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputSize);

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.cleanup_request_send_button), (dialog, which) -> {
            String days = inputDays.getText() != null ? inputDays.getText().toString() : "";
            String size = inputSize.getText() != null ? inputSize.getText().toString() : "";
            sendRetentionRequest(days, size);
        });
        builder.setNegativeButton(getString(R.string.dialog_cancel_button), null);
        builder.show();
    }

    // ============================================================
    //  API LOGIC (Retencja)
    // ============================================================

    private void fetchCurrentRetentionStatus() {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        String url = Constants.VPS_SERVER_IP + "/api/retention/status";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd pobierania statusu retencji", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String days = json.optString("currentDays", "0");
                        String size = json.optString("currentSize", "0");
                        String status = json.optString("status", "NONE");

                        final String mainSettingsText = String.format(getString(R.string.cleanup_current_settings), days, size);
                        final String fullText;
                        if ("PENDING".equals(status)) {
                            String pendingLabel = getString(R.string.cleanup_request_status_pending);
                            String suffix = getString(R.string.status_pending_suffix, pendingLabel);
                            fullText = mainSettingsText + " " + suffix;
                        } else {
                            fullText = mainSettingsText;
                        }

                        runOnUiThread(() -> {
                            if (txtRetentionSummary != null) {
                                txtRetentionSummary.setText(fullText);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Błąd parsowania JSON statusu retencji", e);
                    }
                }
            }
        });
    }

    private void sendRetentionRequest(String days, String size) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        JSONObject json = new JSONObject();
        try {
            if (!days.isEmpty()) json.put("days", days);
            if (!size.isEmpty()) json.put("size", size);
        } catch (Exception e) { Log.e(TAG, "JSON error", e); return; }

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
                        fetchCurrentRetentionStatus();
                    } else {
                        Toast.makeText(SettingsActivity.this, getString(R.string.toast_api_error), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ============================================================
    // APPEARANCE LOGIC
    // ============================================================

    private void loadCurrentAppearanceSettings() {
        int currentTheme = appearanceManager.getTheme();
        switchTheme.setChecked(currentTheme == AppearanceManager.THEME_DARK);

        String currentTextScale = appearanceManager.getTextScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentTextScale)) radioGroupTextScale.check(R.id.radioTextSmall);
        else if (AppearanceManager.SCALE_LARGE.equals(currentTextScale)) radioGroupTextScale.check(R.id.radioTextLarge);
        else radioGroupTextScale.check(R.id.radioTextMedium);

        String currentButtonScale = appearanceManager.getButtonScale();
        if (AppearanceManager.SCALE_SMALL.equals(currentButtonScale)) radioGroupButtonScale.check(R.id.radioButtonSmall);
        else if (AppearanceManager.SCALE_LARGE.equals(currentButtonScale)) radioGroupButtonScale.check(R.id.radioButtonLarge);
        else radioGroupButtonScale.check(R.id.radioButtonMedium);

        String currentLanguage = localeManager.getLanguage();
        if (LocaleManager.LANGUAGE_POLISH.equals(currentLanguage)) radioGroupLanguage.check(R.id.radioLanguagePolish);
        else radioGroupLanguage.check(R.id.radioLanguageEnglish);
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
}