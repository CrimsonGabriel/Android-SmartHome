package com.example.bazunia;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText; // Potrzebny import
import android.widget.LinearLayout; // Potrzebny import
import android.widget.ProgressBar;
import android.widget.TextView; // Potrzebny import
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton; // Potrzebny import

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

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // Nazwy dla SharedPreferences (publiczne, żeby VpsClientService mógł je odczytać)
    public static final String AUTH_PREFS = "AuthPrefs";
    public static final String KEY_ID_TOKEN = "idToken";
    public static final String KEY_USER_EMAIL = "userEmail";

    private GoogleSignInClient mGoogleSignInClient;
    private final OkHttpClient httpClient = new OkHttpClient();

    // Widoki Główne
    private SignInButton btnGoogleSignIn;
    private ProgressBar loginProgressBar;
    private LinearLayout logoSection; // Kontener na logo/tekst

    // [NOWE WIDOKI] Dla sekcji 2FA
    private LinearLayout twoFaLoginSection;
    private EditText editTextLogin2FA;
    private MaterialButton btnVerifyLogin2FA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new AppearanceManager(this).applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Znajdź widoki
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loginProgressBar = findViewById(R.id.loginProgressBar);
        // Użyjemy ID z XMLa, które nadałem (musisz je sprawdzić)
        logoSection = findViewById(R.id.logoSection);
        // Jeśli masz ID w XMLu dla sekcji logo (np. logoSection), użyj go:
        // logoSection = findViewById(R.id.logoSection); // Zakładając, że dodałeś to ID w XML

        // [NOWY KOD] Znajdź widoki 2FA
        twoFaLoginSection = findViewById(R.id.twoFaLoginSection);
        editTextLogin2FA = findViewById(R.id.editTextLogin2FA);
        btnVerifyLogin2FA = findViewById(R.id.btnVerifyLogin2FA);


        // 1. Konfiguracja Google Sign-In
        String webClientId = "79063316759-iva8uesd0vlj3in6eaeralk2kdkgv5or.apps.googleusercontent.com"; // WEB ID
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 2. Rejestracja launchera
        ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleSignInResult(task);
                    } else {
                        Log.w(TAG, "Logowanie Google anulowane lub błąd. Kod: " + result.getResultCode());
                        showLoading(false);
                    }
                }
        );

        // 3. Obsługa kliknięcia Google
        btnGoogleSignIn.setOnClickListener(v -> {
            Log.d(TAG, "Rozpoczynanie logowania Google...");
            showLoading(true);
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
        });

        // [NOWY KOD] 4. Obsługa kliknięcia "Weryfikuj 2FA"
        btnVerifyLogin2FA.setOnClickListener(v -> {
            verifyLogin2FA();
        });
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.i(TAG, "Logowanie Google udane dla: " + account.getEmail());
            String idToken = account.getIdToken();
            if (idToken == null) {
                // ... (obsługa błędu) ...
                Log.e(TAG, "Nie udało się pobrać ID Tokena od Google.");
                showError("Błąd: Nie otrzymano tokena od Google.");
                showLoading(false);
                return;
            }
            // Mamy token, wysyłamy go na nasz serwer VPS
            sendTokenToVps(idToken);

        } catch (ApiException e) {
            // ... (obsługa błędu) ...
            Log.e(TAG, "Logowanie Google nieudane, kod błędu: " + e.getStatusCode());
            showError(getString(R.string.login_invalid_credentials));
            showLoading(false);
        }
    }

    // Wysyła token Google do /auth/google
    private void sendTokenToVps(String idToken) {
        Log.d(TAG, "Wysyłanie tokena do weryfikacji na VPS...");
        JSONObject json = new JSONObject();
        try { json.put("token", idToken); } catch (JSONException e) { /*...*/ }
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.GOOGLE_AUTH_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania tokena do VPS: " + e.getMessage());
                runOnUiThread(() -> { showError("Błąd połączenia z serwerem."); showLoading(false); });
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // [POPRAWKA WYCIEKU]
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        Log.i(TAG, "SUKCES: VPS zweryfikował token Google!");
                        try {
                            JSONObject respJson = new JSONObject(responseBody);
                            String welcomeMsg = respJson.optString("message", getString(R.string.login_welcome_default));
                            String userEmail = respJson.optString("email", "");

                            // [NOWA LOGIKA 2FA] Sprawdź, czy serwer wymaga 2FA
                            boolean requires2FA = respJson.optBoolean("requires2FA", false);

                            // [NOWA LOGIKA] Zapisz token i e-mail. Jest to potrzebne
                            // zarówno dla VpsClientService, jak i do weryfikacji 2FA.
                            saveTokenToPrefs(idToken, userEmail);

                            if (requires2FA) {
                                // 2FA jest WŁĄCZONE. Pokaż UI do wpisania kodu.
                                Log.d(TAG, "Serwer wymaga 2FA. Pokazuję UI 2FA.");
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, "Wymagana weryfikacja 2FA", Toast.LENGTH_SHORT).show();
                                    show2FAInputUI(true); // Pokaż sekcję 2FA
                                    showLoading(false); // Ukryj ProgressBar
                                });
                            } else {
                                // 2FA jest WYŁĄCZONE. Zaloguj normalnie.
                                Log.d(TAG, "2FA nie jest wymagane. Loguję...");
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, welcomeMsg, Toast.LENGTH_SHORT).show();
                                    startApp(); // Uruchom aplikację
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Błąd parsowania odpowiedzi VPS", e);
                            runOnUiThread(LoginActivity.this::startApp); // Na wszelki wypadek
                        }
                    } else {
                        // ... (obsługa błędu serwera, bez zmian) ...
                        Log.w(TAG, "Serwer VPS odrzucił logowanie, kod: " + resp.code() + ", body: " + responseBody);
                        runOnUiThread(() -> { showError("Serwer odrzucił logowanie."); showLoading(false); mGoogleSignInClient.signOut(); });
                    }
                } // [KONIEC POPRAWKI WYCIEKU]
            }
        });
    }

    // [NOWA METODA] Wysyła kod 2FA do weryfikacji
    private void verifyLogin2FA() {
        String code = editTextLogin2FA.getText().toString().trim();
        if (code.length() != 6) {
            showError("Kod 2FA musi mieć 6 cyfr.");
            return;
        }

        // Pobierz zapisany token (musi tam być po kroku 1)
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        String idToken = prefs.getString(KEY_ID_TOKEN, null);

        if (idToken == null) {
            showError("Błąd sesji (brak tokena). Zaloguj się ponownie.");
            show2FAInputUI(false); // Ukryj 2FA, pokaż Google
            return;
        }

        showLoading(true); // Pokaż ProgressBar
        Log.d(TAG, "Wysyłanie kodu 2FA do weryfikacji logowania...");

        JSONObject jsonBody = new JSONObject();
        try { jsonBody.put("totpCode", code); } catch (JSONException e) { /*...*/ }
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.LOGIN_2FA_VERIFY_ENDPOINT) // Nowy endpoint
                .header("Authorization", "Bearer " + idToken) // Token Google
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania kodu 2FA: " + e.getMessage());
                runOnUiThread(() -> { showError("Błąd połączenia z serwerem."); showLoading(false); });
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) { // Poprawka wycieku
                    final String responseBody = resp.body() != null ? resp.body().string() : "";
                    if (resp.isSuccessful()) {
                        // SUKCES! Kod 2FA poprawny.
                        Log.i(TAG, "SUKCES: Kod 2FA poprawny. Loguję.");
                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this, "Zalogowano pomyślnie!", Toast.LENGTH_SHORT).show();
                            startApp(); // WPUŚĆ GO!
                        });
                    } else {
                        // BŁĄD! Zły kod 2FA.
                        Log.w(TAG, "Serwer VPS odrzucił kod 2FA, kod: " + resp.code() + ", body: " + responseBody);
                        runOnUiThread(() -> {
                            showError("Nieprawidłowy kod 2FA. Spróbuj ponownie.");
                            showLoading(false); // Tylko ukryj kręciołek, zostaw UI 2FA
                            editTextLogin2FA.setText(""); // Wyczyść pole
                        });
                    }
                }
            }
        });
    }

    // Zapisuje token do SharedPreferences
    private void saveTokenToPrefs(String idToken, String email) {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ID_TOKEN, idToken)
                .putString(KEY_USER_EMAIL, email)
                .apply();
        Log.d(TAG, "Zapisano token i e-mail w SharedPreferences.");
    }

    // Uruchamia serwis i MainActivity
    private void startApp() {
        Log.d(TAG, "Autoryzacja kompletna. Uruchamiam serwis i MainActivity.");
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        startForegroundService(serviceIntent);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    // [ZMODYFIKOWANA METODA] Pokazuje/ukrywa ProgressBar
    private void showLoading(boolean isLoading) {
        loginProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // Ukryj oba przyciski logowania, gdy się kręci
        if (isLoading) {
            btnGoogleSignIn.setVisibility(View.GONE);
            twoFaLoginSection.setVisibility(View.GONE);
            // Możesz też ukryć logo, jeśli chcesz
            // logoSection.setVisibility(View.GONE);
        } else {
            // Jeśli nie ładujemy, pokaż domyślny przycisk Google
            // (chyba że UI 2FA jest aktywne)
            if (twoFaLoginSection.getVisibility() == View.GONE) {
                btnGoogleSignIn.setVisibility(View.VISIBLE);
                // logoSection.setVisibility(View.VISIBLE);
            }
        }
    }

    // [NOWA METODA] Przełącza widok między Google a 2FA
    private void show2FAInputUI(boolean show) {
        if (show) {
            btnGoogleSignIn.setVisibility(View.GONE);
            twoFaLoginSection.setVisibility(View.VISIBLE);
            // logoSection.setVisibility(View.GONE); // Opcjonalnie ukryj logo
        } else {
            btnGoogleSignIn.setVisibility(View.VISIBLE);
            twoFaLoginSection.setVisibility(View.GONE);
            // logoSection.setVisibility(View.VISIBLE);
        }
        editTextLogin2FA.setText(""); // Zawsze czyść pole
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Logika cichego logowania (bez zmian)
    @Override
    protected void onStart() {
        super.onStart();
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getIdToken() != null) {
            Log.d(TAG, "Znaleziono ostatnio zalogowanego użytkownika. Weryfikuję token...");
            showLoading(true);
            sendTokenToVps(account.getIdToken());
        } else {
            // Jeśli nie ma cichego logowania, upewnij się, że UI jest czyste
            show2FAInputUI(false); // Pokaż przycisk Google
        }
    }
}