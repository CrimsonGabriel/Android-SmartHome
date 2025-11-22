package com.testserwera.bazunia.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.LocaleManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AccountSettingsActivity extends AppCompatActivity {

    private static final String TAG = "AccountSettingsActivity";
    private static final String PREFS_2FA = "TwoFAPrefs";
    private static final String KEY_2FA_ENABLED = "is2FAEnabled";

    private OkHttpClient httpClient;
    private SharedPreferences sharedPreferences2FA;
    private TextView text2FAStatus;
    private MaterialButton btnToggle2FA;
    private LinearLayout setupSection;
    private ImageView qrCodeImageView;
    private MaterialButton btnCopySecret;
    private EditText editText2FA;
    private MaterialButton btnVerify2FA;
    private LinearLayout disableSection;
    private EditText editTextDisable2FA;
    private MaterialButton btnConfirmDisable2FA;
    private ProgressBar progressBar2FA;

    // Zmieniono na zmienną lokalną w onCreate (pole usunięte)
    // private MaterialButton btnChangePassword;

    private String currentJwtToken;
    private String currentSecretKey;


    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = new LocaleManager(newBase);
        super.attachBaseContext(localeManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new AppearanceManager(this).applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_settings);

        httpClient = new OkHttpClient();
        sharedPreferences2FA = getSharedPreferences(PREFS_2FA, Context.MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbarAccountSettings);
        toolbar.setNavigationOnClickListener(v -> finish());

        text2FAStatus = findViewById(R.id.text2FAStatus);
        btnToggle2FA = findViewById(R.id.btnToggle2FA);
        setupSection = findViewById(R.id.setupSection);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        btnCopySecret = findViewById(R.id.btnCopySecret);
        editText2FA = findViewById(R.id.editText2FA);
        btnVerify2FA = findViewById(R.id.btnVerify2FA);
        disableSection = findViewById(R.id.disableSection);
        editTextDisable2FA = findViewById(R.id.editTextDisable2FA);
        btnConfirmDisable2FA = findViewById(R.id.btnConfirmDisable2FA);
        progressBar2FA = findViewById(R.id.progressBar2FA);

        // Zmienna lokalna
        MaterialButton btnChangePassword = findViewById(R.id.btnChangePassword);

        setup2FAListeners();

        btnChangePassword.setOnClickListener(v -> {
            Intent intent = new Intent(AccountSettingsActivity.this, ChangePasswordActivity.class);
            startActivity(intent);
        });

        retrieveJwtToken();
        fetch2FAStatusFromServer();
    }

    // --- CAŁA LOGIKA 2FA JEST PRZENIESIONA TUTAJ ---

    private void setup2FAListeners() {
        btnToggle2FA.setOnClickListener(v -> toggle2FA());
        btnVerify2FA.setOnClickListener(v -> verify2FA());
        btnConfirmDisable2FA.setOnClickListener(v -> disable2FA());
        btnCopySecret.setOnClickListener(v -> copySecretToClipboard());
    }

    private void copySecretToClipboard() {
        if (currentSecretKey != null && !currentSecretKey.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Klucz 2FA", currentSecretKey);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Klucz 2FA skopiowany do schowka", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Brak klucza do skopiowania", Toast.LENGTH_SHORT).show();
        }
    }

    private void retrieveJwtToken() {
        // Używamy tych samych SharedPreferences, co LoginActivity
        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        currentJwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (currentJwtToken != null) {
            Log.d(TAG, "Pobrano token JWT z SharedPreferences.");
        } else {
            Log.e(TAG, "Nie znaleziono tokena JWT w SharedPreferences. Użytkownik nie jest zalogowany.");
            Toast.makeText(this, "Błąd: Brak zalogowanego użytkownika (brak tokena).", Toast.LENGTH_LONG).show();
            // Możemy też zamknąć aktywność, bo nic tu nie zadziała
            finish();
        }
    }

    private void update2FAUIState() {
        boolean isEnabled = sharedPreferences2FA.getBoolean(KEY_2FA_ENABLED, false);
        runOnUiThread(() -> {
            if (isEnabled) {
                text2FAStatus.setText(R.string.status_2fa_enabled);
                btnToggle2FA.setText(R.string.disable_2fa);
            } else {
                text2FAStatus.setText(R.string.status_2fa_disabled);
                btnToggle2FA.setText(R.string.enable_2fa);
            }
            setupSection.setVisibility(View.GONE);
            disableSection.setVisibility(View.GONE);
            progressBar2FA.setVisibility(View.GONE);
            editText2FA.setText("");
            editTextDisable2FA.setText("");
        });
    }

    private void toggle2FA() {
        if (currentJwtToken == null) { return; }
        boolean isEnabled = sharedPreferences2FA.getBoolean(KEY_2FA_ENABLED, false);

        runOnUiThread(() -> {
            if (isEnabled) {
                setupSection.setVisibility(View.GONE);
                disableSection.setVisibility(View.VISIBLE);
            } else {
                disableSection.setVisibility(View.GONE);
                start2FASetup();
            }
        });
    }

    private void start2FASetup() {
        showLoading(true);
        Log.d(TAG, "Rozpoczynanie konfiguracji 2FA...");
        if (currentJwtToken == null) { return; }

        Request request = new Request.Builder()
                .url(Constants.SETUP_2FA_ENDPOINT)
                .header("Authorization", "Bearer " + currentJwtToken)
                .post(RequestBody.create(new byte[0]))
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { handleApiError(e.getMessage()); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    handleSetupResponse(resp);
                }
            }
        });
    }

    private void handleSetupResponse(Response response) throws IOException {
        final String responseBody = response.body() != null ? response.body().string() : "";

        String endpoint = Constants.SETUP_2FA_ENDPOINT.substring(Constants.VPS_SERVER_IP.length());

        if (response.isSuccessful()) {
            try {
                JSONObject json = new JSONObject(responseBody);
                String otpauthUrl = json.getString("otpauth_url");
                currentSecretKey = json.getString("secret");
                generateAndShowQrCode(otpauthUrl);
                runOnUiThread(() -> {
                    setupSection.setVisibility(View.VISIBLE);
                    showLoading(false);
                });
            } catch (JSONException e) { handleApiError("Błąd parsowania odpowiedzi " + endpoint + ": " + e.getMessage()); }
        } else { handleApiErrorFromServer(response.code(), responseBody, endpoint); }
    }

    private void generateAndShowQrCode(String text) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            int qrCodeSize = 600;
            Bitmap bitmap = barcodeEncoder.encodeBitmap(text, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize);
            runOnUiThread(() -> qrCodeImageView.setImageBitmap(bitmap));
        } catch (Exception e) {
            Log.e(TAG, "Błąd generowania kodu QR", e);
            runOnUiThread(() -> showError("Nie udało się wygenerować kodu QR."));
        }
    }

    private void verify2FA() {
        String code = editText2FA.getText().toString().trim();
        if (isCodeInvalid(code)) return;
        showLoading(true);
        Log.d(TAG, "Weryfikowanie kodu 2FA...");
        JSONObject jsonBody = createJsonPayload(code);
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.VERIFY_2FA_ENDPOINT)
                .header("Authorization", "Bearer " + currentJwtToken)
                .post(body)
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { handleApiError(e.getMessage()); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    handleVerifyResponse(resp);
                }
            }
        });
    }

    private void handleVerifyResponse(Response response) throws IOException {
        final String responseBody = response.body() != null ? response.body().string() : "";
        String endpoint = Constants.VERIFY_2FA_ENDPOINT.substring(Constants.VPS_SERVER_IP.length());

        if (response.isSuccessful()) {
            Log.i(TAG, "Weryfikacja 2FA udana!");
            save2FAState(true);
            runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(AccountSettingsActivity.this, R.string.two_fa_enabled_success, Toast.LENGTH_SHORT).show();
                update2FAUIState();
                currentSecretKey = null;
            });
        } else { handleApiErrorFromServer(response.code(), responseBody, endpoint); }
    }

    private void disable2FA() {
        String code = editTextDisable2FA.getText().toString().trim();
        if (isCodeInvalid(code)) return;
        showLoading(true);
        Log.d(TAG, "Wyłączanie 2FA...");
        JSONObject jsonBody = createJsonPayload(code);
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.DISABLE_2FA_ENDPOINT)
                .header("Authorization", "Bearer " + currentJwtToken)
                .post(body)
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { handleApiError(e.getMessage()); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    handleDisableResponse(resp);
                }
            }
        });
    }

    private void handleDisableResponse(Response response) throws IOException {
        final String responseBody = response.body() != null ? response.body().string() : "";
        String endpoint = Constants.DISABLE_2FA_ENDPOINT.substring(Constants.VPS_SERVER_IP.length());

        if (response.isSuccessful()) {
            Log.i(TAG, "Wyłączenie 2FA udane!");
            save2FAState(false);
            runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(AccountSettingsActivity.this, R.string.two_fa_disabled_success, Toast.LENGTH_SHORT).show();
                update2FAUIState();
            });
        } else { handleApiErrorFromServer(response.code(), responseBody, endpoint); }
    }

    private boolean isCodeInvalid(String code) {
        if (code == null || code.length() != 6) {
            runOnUiThread(() -> showError("Kod 2FA musi mieć 6 cyfr."));
            return true;
        }
        if (currentJwtToken == null) {
            runOnUiThread(() -> showError(getString(R.string.two_fa_error_generic) + " (Brak tokena)"));
            retrieveJwtToken();
            return true;
        }
        return false;
    }

    private JSONObject createJsonPayload(String code) {
        JSONObject jsonBody = new JSONObject();
        try { jsonBody.put("totpCode", code); } catch (JSONException e) { Log.e(TAG,"Json error", e); }
        return jsonBody;
    }

    private void handleApiError(String message) {
        Log.e(TAG, "Błąd API 2FA (sieć): " + message);
        runOnUiThread(() -> { showLoading(false); showError(getString(R.string.two_fa_error_generic) + " (Błąd sieci)"); });
    }

    private void handleApiErrorFromServer(int code, String body, String endpoint) {
        Log.w(TAG, "Błąd serwera " + endpoint + ": " + code + " Body: " + body);
        String errorMsg = getString(R.string.two_fa_error_generic) + " (Kod: " + code + ")";
        try {
            JSONObject errorJson = new JSONObject(body);
            errorMsg = errorJson.optString("error", errorMsg);
            if (errorMsg.toLowerCase().contains("nieprawidłowy kod 2fa")) { errorMsg = getString(R.string.two_fa_invalid_code); }
        } catch (JSONException ignored) {
            if (body != null && body.toLowerCase().contains("nieprawidłowy kod 2fa")) { errorMsg = getString(R.string.two_fa_invalid_code); }
        }
        final String finalErrorMsg = errorMsg;
        runOnUiThread(() -> {
            showLoading(false);
            showError(finalErrorMsg);
            if (endpoint.contains("/2fa/setup") && code != 200) { setupSection.setVisibility(View.GONE); }
        });
    }

    private void save2FAState(boolean enabled) {
        sharedPreferences2FA.edit().putBoolean(KEY_2FA_ENABLED, enabled).apply();
        Log.d(TAG, "Zapisano stan 2FA: " + enabled);
    }

    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            progressBar2FA.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnToggle2FA.setEnabled(!isLoading);
            btnVerify2FA.setEnabled(!isLoading);
            btnConfirmDisable2FA.setEnabled(!isLoading);
            editText2FA.setEnabled(!isLoading);
            editTextDisable2FA.setEnabled(!isLoading);
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void fetch2FAStatusFromServer() {
        if (currentJwtToken == null) {
            Log.e(TAG, "Brak tokena, nie można pobrać statusu 2FA.");
            update2FAUIState();
            return;
        }
        showLoading(true);
        Log.d(TAG, "Pobieranie aktualnego statusu 2FA z serwera...");
        Request request = new Request.Builder()
                .url(Constants.CHECK_2FA_STATUS_ENDPOINT)
                .header("Authorization", "Bearer " + currentJwtToken)
                .get()
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd pobierania statusu 2FA: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    showError("Błąd synchronizacji statusu 2FA.");
                    update2FAUIState();
                });
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        Log.d(TAG, "Otrzymano status 2FA z serwera: " + responseBody);
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            boolean serverStatus = json.getBoolean("is2FAEnabled");
                            save2FAState(serverStatus);
                            runOnUiThread(() -> {
                                showLoading(false);
                                update2FAUIState();
                            });
                        } catch (JSONException e) {
                            handleApiError("Błąd parsowania statusu 2FA: " + e.getMessage());
                        }
                    } else {
                        String endpoint = Constants.CHECK_2FA_STATUS_ENDPOINT.substring(Constants.VPS_SERVER_IP.length());
                        handleApiErrorFromServer(resp.code(), responseBody, endpoint);
                        runOnUiThread(AccountSettingsActivity.this::update2FAUIState);
                    }
                }
            }
        });
    }
}