package com.example.bazunia.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bazunia.utils.LocaleManager;
import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.R;
import com.example.bazunia.data.VpsClientService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import com.google.android.material.textfield.TextInputEditText;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    public static final String AUTH_PREFS = "AuthPrefs";
    public static final String KEY_JWT_TOKEN = "jwtToken"; // ZMIENIONY KLUCZ
    public static final String KEY_USER_EMAIL = "userEmail";

    private GoogleSignInClient mGoogleSignInClient;
    private final OkHttpClient httpClient = new OkHttpClient();

    // Widoki Główne
    private MaterialButton btnGoogleSignIn;
    private ProgressBar loginProgressBar;
    private LinearLayout logoSection;

    // ⭐️ NOWE WIDOKI DLA EMAIL/HASŁO ⭐️
    private LinearLayout emailLoginSection;
    private LinearLayout registerLinkSection;
    private TextInputEditText editTextEmail;
    private TextInputEditText editTextPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnRegisterLink;
    private MaterialButton btnForgotPassword;


    private LinearLayout twoFaLoginSection;

    private EditText editTextLogin2FA;
    private boolean isEmail2FaFlow = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        // [POPRAWNE WYWOŁANIE] To jest kluczowe dla poprawnej zmiany języka w Aktywności.
        LocaleManager localeManager = new LocaleManager(newBase);
        super.attachBaseContext(localeManager.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new AppearanceManager(this).applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Znajdź widoki
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loginProgressBar = findViewById(R.id.loginProgressBar);
        logoSection = findViewById(R.id.logoSection);
        twoFaLoginSection = findViewById(R.id.twoFaLoginSection);
        editTextLogin2FA = findViewById(R.id.editTextLogin2FA);
        MaterialButton btnVerifyLogin2FA = findViewById(R.id.btnVerifyLogin2FA);

        // ⭐️ NOWE WIDOKI (Email/Hasło) ⭐️
        emailLoginSection = findViewById(R.id.emailLoginSection);
        registerLinkSection = findViewById(R.id.registerLinkSection);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegisterLink = findViewById(R.id.btnRegisterLink);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);

        // 1. Konfiguracja Google Sign-In (bez zmian)
        String webClientId = "79063316759-iva8uesd0vlj3in6eaeralk2kdkgv5or.apps.googleusercontent.com"; // WEB ID
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 2. Rejestracja launchera (bez zmian)
        ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleSignInResult(task);
                    } else {
                        Log.w(TAG, "Logowanie Google anulowane lub błąd. Kod: " + result.getResultCode());
                        showLoading(false);
                        show2FAInputUI(false); // Upewnij się, że widok logowania jest poprawny
                    }
                }
        );

        // 3. Obsługa kliknięcia Google (bez zmian)
        btnGoogleSignIn.setOnClickListener(v -> {
            Log.d(TAG, "Rozpoczynanie logowania Google...");
            showLoading(true);
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
        });

        // 4. Obsługa kliknięcia "Weryfikuj 2FA" (bez zmian)
        btnVerifyLogin2FA.setOnClickListener(v -> verifyLogin2FA());

        // ⭐️ 5. NOWA OBSŁUGA KLIKNIĘĆ (Email/Hasło) ⭐️
        btnLogin.setOnClickListener(v -> performEmailLogin());
        btnRegisterLink.setOnClickListener(v -> navigateToRegister());
        btnForgotPassword.setOnClickListener(v -> navigateToPasswordReset());
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.i(TAG, "Logowanie Google udane dla: " + account.getEmail());
            String idToken = account.getIdToken();
            if (idToken == null) {
                Log.e(TAG, "Nie udało się pobrać ID Tokena od Google.");
                showError("Błąd: Nie otrzymano tokena od Google.");
                showLoading(false);
                return;
            }
            sendTokenToVps(idToken);
        } catch (ApiException e) {
            Log.e(TAG, "Logowanie Google nieudane, kod błędu: " + e.getStatusCode());
            showError(getString(R.string.login_invalid_credentials));
            showLoading(false);
            show2FAInputUI(false);
        }
    }

    private void sendTokenToVps(String idToken) {
        isEmail2FaFlow = false;
        Log.d(TAG, "Wysyłanie tokena do weryfikacji na VPS...");
        JSONObject json = new JSONObject();
        try {
            json.put("token", idToken);
        } catch (JSONException e) { /* Błąd parsowania jest mało prawdopodobny */ }
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
                    showError("Błąd połączenia z serwerem.");
                    showLoading(false);
                    show2FAInputUI(false);
                });
            }



            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        Log.i(TAG, "SUKCES: VPS zweryfikował token Google!");
                        try {
                            JSONObject respJson = new JSONObject(responseBody);
                            String welcomeMsg = respJson.optString("message", getString(R.string.login_welcome_default));
                            String userEmail = respJson.optString("email", "");
                            boolean requires2FA = respJson.optBoolean("requires2FA", false);
                            String jwtToken = respJson.optString("jwt", null); // <--- ODCZYT JWT

                            if (!requires2FA && jwtToken != null) { // ⭐️ TYLKO JEŚLI JEST JWT I NIE WYMAGA 2FA
                                saveTokenToPrefs(jwtToken, userEmail); // <--- ZAPIS POPRAWNEGO JWT
                            }

                            if (requires2FA) {
                                // ⭐️ UWAGA: Jeśli wymaga 2FA, token ID Google JEST UŻYWANY JAKO SESJA TYMCZASOWA
                                saveTokenToPrefs(idToken, userEmail); // Zapisujemy idToken TYMCZASOWO do weryfikacji 2FA
                                Log.d(TAG, "Serwer wymaga 2FA. Pokazuję UI 2FA.");
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, "Wymagana weryfikacja 2FA", Toast.LENGTH_SHORT).show();
                                    show2FAInputUI(true);
                                    showLoading(false);
                                });
                            } else {
                                Log.d(TAG, "2FA nie jest wymagane.");
                                // ⭐️ NOWA LOGIKA SPRAWDZANIA HASŁA ⭐️
                                boolean requiresPasswordSetup = respJson.optBoolean("requiresPasswordSetup", false);

                                if (requiresPasswordSetup) {
                                    runOnUiThread(LoginActivity.this::startCreatePasswordActivity);
                                } else {
                                    runOnUiThread(() -> {
                                        Toast.makeText(LoginActivity.this, welcomeMsg, Toast.LENGTH_SHORT).show();
                                        startApp();
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Błąd parsowania odpowiedzi VPS", e);
                            runOnUiThread(LoginActivity.this::startApp);
                        }
                    } else {
                        Log.w(TAG, "Serwer VPS odrzucił logowanie, kod: " + resp.code() + ", body: " + responseBody);
                        runOnUiThread(() -> {
                            showError("Serwer odrzucił logowanie.");
                            showLoading(false);
                            show2FAInputUI(false);
                            mGoogleSignInClient.signOut();
                        });
                    }
                }
            }
        });
    }

    // ⭐️⭐️ ZAKTUALIZOWANA METODA ⭐️⭐️
    private void verifyLogin2FA() {
        String code = editTextLogin2FA.getText().toString().trim();
        if (code.length() != 6) {
            showError("Kod 2FA musi mieć 6 cyfr.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        // Pobieramy token (jest to idToken Google LUB tymczasowy JWT dla e-maila)
        String tempToken = prefs.getString(KEY_JWT_TOKEN, null);

        if (tempToken == null) {
            showError("Błąd sesji (brak tokena). Zaloguj się ponownie.");
            show2FAInputUI(false);
            return;
        }

        showLoading(true);
        Log.d(TAG, "Wysyłanie kodu 2FA do weryfikacji...");

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("totpCode", code);
        } catch (JSONException e) { /*...*/ }
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        // ⭐️ NOWA LOGIKA: Wybierz endpoint na podstawie flagi ⭐️
        String endpointUrl;
        if (isEmail2FaFlow) {
            Log.d(TAG, "Używam endpointu 2FA dla E-mail.");
            // Musisz dodać LOGIN_EMAIL_2FA_VERIFY_ENDPOINT do Constants.java!
            endpointUrl = Constants.LOGIN_EMAIL_2FA_VERIFY_ENDPOINT;
        } else {
            Log.d(TAG, "Używam endpointu 2FA dla Google.");
            endpointUrl = Constants.LOGIN_2FA_VERIFY_ENDPOINT;
        }

        Request request = new Request.Builder()
                .url(endpointUrl)
                .header("Authorization", "Bearer " + tempToken) // Wysyłamy odpowiedni token
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania kodu 2FA: " + e.getMessage());
                runOnUiThread(() -> {
                    showError("Błąd połączenia z serwerem.");
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        Log.i(TAG, "SUKCES: Kod 2FA poprawny. Loguję.");
                        try {
                            JSONObject respJson = new JSONObject(responseBody);
                            String newJwt = respJson.optString("token", null);

                            // ⭐️ NOWA LOGIKA SPRAWDZANIA HASŁA ⭐️
                            boolean requiresPasswordSetup = respJson.optBoolean("requiresPasswordSetup", false);

                            if (newJwt != null) {
                                saveTokenToPrefs(newJwt, prefs.getString(KEY_USER_EMAIL, ""));
                            }

                            if (requiresPasswordSetup) {
                                runOnUiThread(LoginActivity.this::startCreatePasswordActivity);
                            } else {
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, "Zalogowano pomyślnie!", Toast.LENGTH_SHORT).show();
                                    startApp();
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Błąd parsowania odpowiedzi 2FA", e);
                            runOnUiThread(LoginActivity.this::startApp);
                        }

                    } else {
                        Log.w(TAG, "Serwer VPS odrzucił kod 2FA, kod: " + resp.code() + ", body: " + responseBody);
                        runOnUiThread(() -> {
                            showError("Nieprawidłowy kod 2FA. Spróbuj ponownie.");
                            showLoading(false);
                            editTextLogin2FA.setText("");
                        });
                    }
                }
            }
        });
    }

    private void saveTokenToPrefs(String jwtToken, String email) {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_JWT_TOKEN, jwtToken) // Zapisujemy JWT/idToken
                .putString(KEY_USER_EMAIL, email)
                .apply();
        Log.d(TAG, "Zapisano token i e-mail w SharedPreferences.");
    }

    private void startApp() {
        Log.d(TAG, "Autoryzacja kompletna. Uruchamiam serwis i MainActivity.");
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
            btnForgotPassword.setVisibility(View.GONE);

            twoFaLoginSection.setVisibility(View.VISIBLE);
        } else {
            logoSection.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);

            emailLoginSection.setVisibility(View.VISIBLE);
            registerLinkSection.setVisibility(View.VISIBLE);
            btnForgotPassword.setVisibility(View.VISIBLE);
            twoFaLoginSection.setVisibility(View.GONE);
        }
    }

    // Poprawka: Dodana brakująca metoda do pokazywania błędów
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ⭐️⭐️ ZAKTUALIZOWANA METODA ⭐️⭐️
    private void performEmailLogin() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // Walidacja (bez zmian)
        if (email.isEmpty()) {
            editTextEmail.setError(getString(R.string.login_error_email_empty));
            editTextEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            editTextPassword.setError(getString(R.string.login_error_password_empty));
            editTextPassword.requestFocus();
            return;
        }

        showLoading(true);
        Log.d(TAG, "Próba logowania e-mailem: " + email);

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
        } catch (JSONException e) { /* Błąd parsowania jest mało prawdopodobny */ }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.LOGIN_EMAIL_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd logowania e-mailem: " + e.getMessage());
                runOnUiThread(() -> {
                    showError(getString(R.string.login_error_server));
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";

                    if (resp.isSuccessful()) {
                        Log.i(TAG, "SUKCES: Logowanie e-mail udane! Odpowiedź: " + responseBody);
                        try {
                            // ⭐️ NOWA LOGIKA PARSOWANIA AuthResponse ⭐️
                            JSONObject respJson = new JSONObject(responseBody);
                            String jwtToken = respJson.optString("jwt", null);
                            boolean requires2FA = respJson.optBoolean("requires2FA", false);

                            if (jwtToken == null) {
                                Log.e(TAG, "Logowanie udane, ale brak tokena JWT w odpowiedzi.");
                                runOnUiThread(() -> {
                                    showError(getString(R.string.login_error_server));
                                    showLoading(false);
                                });
                                return;
                            }

                            // Zapisujemy token (będzie tymczasowy lub stały) i email
                            saveTokenToPrefs(jwtToken, email);

                            if (requires2FA) {
                                int d = Log.d(TAG, "Serwer wymaga 2FA dla logowania e-mailem.");
                                // Ustaw flagę, aby verifyLogin2FA wiedziało, co robić
                                isEmail2FaFlow = true;
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, "Wymagana weryfikacja 2FA", Toast.LENGTH_SHORT).show();
                                    show2FAInputUI(true);
                                    showLoading(false);
                                });
                            } else {
                                Log.d(TAG, "Logowanie e-mail: 2FA nie jest wymagane.");
                                isEmail2FaFlow = false;

                                // ⭐️ NOWA LOGIKA SPRAWDZANIA HASŁA ⭐️
                                // (Dla logowania e-mail to zawsze będzie 'false', ale dla spójności)
                                boolean requiresPasswordSetup = respJson.optBoolean("requiresPasswordSetup", false);

                                if (requiresPasswordSetup) {
                                    runOnUiThread(LoginActivity.this::startCreatePasswordActivity);
                                } else {
                                    runOnUiThread(() -> {
                                        Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                                        startApp();
                                    });

                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Błąd parsowania odpowiedzi logowania e-mail", e);
                            runOnUiThread(() -> {
                                showError(getString(R.string.login_error_server));
                                showLoading(false);
                            });
                        }
                    } else if (resp.code() == 401 || resp.code() == 403) {
                        Log.w(TAG, "Serwer VPS odrzucił logowanie e-mail, kod: " + resp.code());
                        runOnUiThread(() -> {
                            showError(getString(R.string.login_error_invalid_credentials));
                            showLoading(false);
                        });
                    } else {
                        Log.w(TAG, "Błąd serwera logowania e-mail, kod: " + resp.code() + ", body: " + responseBody);
                        runOnUiThread(() -> {
                            showError(getString(R.string.login_error_server));
                            showLoading(false);
                        });
                    }
                }
            }
        });
    }

    private void startCreatePasswordActivity() {
        Log.d(TAG, "Wymagane ustawienie hasła. Uruchamiam CreatePasswordActivity.");
        Intent intent = new Intent(this, CreatePasswordActivity.class);
        startActivity(intent);
        // NIE kończymy LoginActivity, aby użytkownik mógł wrócić, jeśli np. naciśnie 'wstecz'
        // finish();
    }

    // ⭐️⭐️ METODY PRZENIESIONE WE WŁAŚCIWE MIEJSCE (NA POZIOM KLASY) ⭐️⭐️

    private void navigateToRegister() {
        // Teraz 'this' poprawnie odnosi się do LoginActivity
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void navigateToPasswordReset() {
        // Teraz 'this' poprawnie odnosi się do LoginActivity
        Intent intent = new Intent(this, RequestPasswordResetActivity.class);
        // Przekaż e-mail, jeśli użytkownik już go wpisał
        String email = editTextEmail.getText().toString().trim();
        if (!email.isEmpty()) {
            intent.putExtra("USER_EMAIL", email);
        }
        startActivity(intent);
    }

}