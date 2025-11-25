package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
// import androidx.appcompat.app.AppCompatActivity; // ZMIANA: Niepotrzebne
import androidx.core.content.ContextCompat;

import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.CustomCredential;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.VpsClientService;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

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

// ZMIANA: BaseActivity
public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    public static final String AUTH_PREFS = "AuthPrefs";
    public static final String KEY_JWT_TOKEN = "jwtToken";
    public static final String KEY_USER_EMAIL = "userEmail";

    private CredentialManager credentialManager;
    private final OkHttpClient httpClient = new OkHttpClient();

    private MaterialButton btnGoogleSignIn;
    private ProgressBar loginProgressBar;
    private LinearLayout logoSection;
    private LinearLayout emailLoginSection;
    private LinearLayout registerLinkSection;
    private LinearLayout twoFaLoginSection;
    private TextInputEditText editTextEmail;
    private TextInputEditText editTextPassword;
    private EditText editTextLogin2FA;

    private boolean isEmail2FaFlow = false;

    // ZMIANA: Usunięto attachBaseContext

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ZMIANA: Usunięto AppearanceManager
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loginProgressBar = findViewById(R.id.loginProgressBar);
        logoSection = findViewById(R.id.logoSection);
        twoFaLoginSection = findViewById(R.id.twoFaLoginSection);
        emailLoginSection = findViewById(R.id.emailLoginSection);
        registerLinkSection = findViewById(R.id.registerLinkSection);

        editTextLogin2FA = findViewById(R.id.editTextLogin2FA);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);

        MaterialButton btnVerifyLogin2FA = findViewById(R.id.btnVerifyLogin2FA);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        MaterialButton btnRegisterLink = findViewById(R.id.btnRegisterLink);
        MaterialButton btnForgotPassword = findViewById(R.id.btnForgotPassword);

        credentialManager = CredentialManager.create(this);

        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        btnVerifyLogin2FA.setOnClickListener(v -> verifyLogin2FA());
        btnLogin.setOnClickListener(v -> performEmailLogin());
        btnRegisterLink.setOnClickListener(v -> navigateToRegister());
        btnForgotPassword.setOnClickListener(v -> navigateToPasswordReset());
    }

    private void signInWithGoogle() {
        showLoading(true);
        Log.d(TAG, "Rozpoczynanie logowania Google (Credential Manager)...");

        String webClientId = "79063316759-iva8uesd0vlj3in6eaeralk2kdkgv5or.apps.googleusercontent.com";

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new android.os.CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new androidx.credentials.CredentialManagerCallback<>() {
                    @Override
                    public void onResult(androidx.credentials.GetCredentialResponse result) {
                        handleSignInSuccess(result);
                    }

                    @Override
                    public void onError(@NonNull androidx.credentials.exceptions.GetCredentialException e) {
                        Log.e(TAG, "Błąd logowania Credential Manager", e);
                        showLoading(false);

                        String msg = e.getMessage();
                        if (msg != null && msg.contains("No credential")) {
                            Log.d(TAG, "Logowanie anulowane przez użytkownika.");
                        } else {
                            showError(getString(R.string.login_error_prefix, msg));
                        }
                    }
                }
        );
    }

    private void handleSignInSuccess(androidx.credentials.GetCredentialResponse result) {
        androidx.credentials.Credential credential = result.getCredential();

        if (credential instanceof CustomCredential &&
                credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {

            try {
                GoogleIdTokenCredential googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.getData());

                String idToken = googleIdTokenCredential.getIdToken();
                String email = googleIdTokenCredential.getId();

                Log.i(TAG, "Pobrano token Google dla: " + email);
                sendTokenToVps(idToken);

            } catch (Exception e) {
                Log.e(TAG, "Błąd parsowania Google ID Token", e);
                showLoading(false);
                showError(getString(R.string.login_error_processing));
            }
        } else {
            Log.e(TAG, "Nieoczekiwany typ poświadczeń: " + credential.getType());
            showLoading(false);
            showError(getString(R.string.login_error_unknown));
        }
    }

    private void sendTokenToVps(String idToken) {
        isEmail2FaFlow = false;
        JSONObject json = new JSONObject();
        try {
            json.put("token", idToken);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON tokena", e);
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.GOOGLE_AUTH_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania tokena do VPS: " + e.getMessage());
                runOnUiThread(() -> {
                    showError(getString(R.string.error_server_connection));
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        processLoginResponse(responseBody, idToken);
                    } else {
                        Log.w(TAG, "Serwer VPS odrzucił logowanie, kod: " + resp.code());
                        runOnUiThread(() -> {
                            showError(getString(R.string.login_error_rejected));
                            showLoading(false);
                        });
                    }
                }
            }
        });
    }

    private void processLoginResponse(String responseBody, String tempTokenIf2FA) {
        try {
            JSONObject respJson = new JSONObject(responseBody);
            String userEmail = respJson.optString("email", "");
            boolean requires2FA = respJson.optBoolean("requires2FA", false);
            boolean requiresPasswordSetup = respJson.optBoolean("requiresPasswordSetup", false);

            String jwtToken = respJson.optString("jwt");

            if (requires2FA) {
                String tokenToSave = (tempTokenIf2FA != null) ? tempTokenIf2FA : jwtToken;
                saveTokenToPrefs(tokenToSave, userEmail);

                Log.d(TAG, "Wymagane 2FA.");
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, R.string.login_msg_2fa_required, Toast.LENGTH_SHORT).show();
                    show2FAInputUI(true);
                    showLoading(false);
                });
            } else if (!jwtToken.isEmpty()) {
                saveTokenToPrefs(jwtToken, userEmail);
                if (requiresPasswordSetup) {
                    runOnUiThread(this::startCreatePasswordActivity);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                        startApp();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    showError(getString(R.string.login_error_no_token));
                    showLoading(false);
                });
            }
        } catch (JSONException e) {
            Log.e(TAG, "Błąd JSON", e);
            runOnUiThread(() -> {
                showError(getString(R.string.error_data_processing));
                showLoading(false);
            });
        }
    }

    private void verifyLogin2FA() {
        Editable text = editTextLogin2FA.getText();
        String code = (text != null) ? text.toString().trim() : "";

        if (code.length() != 6) {
            showError(getString(R.string.login_error_2fa_length));
            return;
        }

        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        String tempToken = prefs.getString(KEY_JWT_TOKEN, null);

        if (tempToken == null) {
            showError(getString(R.string.login_error_session));
            show2FAInputUI(false);
            return;
        }

        showLoading(true);
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("totpCode", code);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON 2FA", e);
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        String endpointUrl = isEmail2FaFlow ? Constants.LOGIN_EMAIL_2FA_VERIFY_ENDPOINT : Constants.LOGIN_2FA_VERIFY_ENDPOINT;

        Request request = new Request.Builder()
                .url(endpointUrl)
                .header("Authorization", "Bearer " + tempToken)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    showError(getString(R.string.error_connection_short));
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        try {
                            JSONObject respJson = new JSONObject(responseBody);
                            String newJwt = respJson.optString("token");
                            boolean requiresPasswordSetup = respJson.optBoolean("requiresPasswordSetup", false);

                            if (!newJwt.isEmpty()) {
                                saveTokenToPrefs(newJwt, prefs.getString(KEY_USER_EMAIL, ""));
                            }

                            if (requiresPasswordSetup) {
                                runOnUiThread(LoginActivity.this::startCreatePasswordActivity);
                            } else {
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                                    startApp();
                                });
                            }
                        } catch (JSONException e) {
                            runOnUiThread(() -> showError(getString(R.string.error_server_data)));
                        }
                    } else {
                        runOnUiThread(() -> {
                            showError(getString(R.string.login_error_2fa_invalid));
                            showLoading(false);
                            editTextLogin2FA.setText("");
                        });
                    }
                }
            }
        });
    }

    private void performEmailLogin() {
        Editable emailText = editTextEmail.getText();
        Editable passText = editTextPassword.getText();

        String email = (emailText != null) ? emailText.toString().trim() : "";
        String password = (passText != null) ? passText.toString().trim() : "";

        if (email.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_missing_creds));
            return;
        }

        showLoading(true);
        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd JSON Email Login", e);
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.LOGIN_EMAIL_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    showError(getString(R.string.error_server_short));
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        isEmail2FaFlow = true;
                        processLoginResponse(responseBody, null);
                    } else {
                        runOnUiThread(() -> {
                            showError(getString(R.string.login_error_invalid_creds));
                            showLoading(false);
                        });
                    }
                }
            }
        });
    }

    private void saveTokenToPrefs(String jwtToken, String email) {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_JWT_TOKEN, jwtToken)
                .putString(KEY_USER_EMAIL, email)
                .apply();
    }

    private void startApp() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        startForegroundService(serviceIntent);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        loginProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void show2FAInputUI(boolean show) {
        if (show) {
            logoSection.setVisibility(View.GONE);
            btnGoogleSignIn.setVisibility(View.GONE);
            emailLoginSection.setVisibility(View.GONE);
            registerLinkSection.setVisibility(View.GONE);
            twoFaLoginSection.setVisibility(View.VISIBLE);
        } else {
            logoSection.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);
            emailLoginSection.setVisibility(View.VISIBLE);
            registerLinkSection.setVisibility(View.VISIBLE);
            twoFaLoginSection.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void startCreatePasswordActivity() {
        Intent intent = new Intent(this, CreatePasswordActivity.class);
        startActivity(intent);
    }

    private void navigateToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void navigateToPasswordReset() {
        Intent intent = new Intent(this, RequestPasswordResetActivity.class);
        Editable text = editTextEmail.getText();
        String email = (text != null) ? text.toString().trim() : "";
        if (!email.isEmpty()) {
            intent.putExtra("USER_EMAIL", email);
        }
        startActivity(intent);
    }
}