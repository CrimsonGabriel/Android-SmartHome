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

import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.R;
import com.example.bazunia.data.VpsClientService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;

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

    public static final String AUTH_PREFS = "AuthPrefs";
    public static final String KEY_ID_TOKEN = "idToken";
    public static final String KEY_USER_EMAIL = "userEmail";

    private GoogleSignInClient mGoogleSignInClient;
    private final OkHttpClient httpClient = new OkHttpClient();

    // Widoki Główne
    private SignInButton btnGoogleSignIn;
    private ProgressBar loginProgressBar;
    private LinearLayout logoSection;

    // Widoki dla sekcji 2FA
    private LinearLayout twoFaLoginSection;
    private EditText editTextLogin2FA;

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
                        show2FAInputUI(false); // Upewnij się, że widok logowania jest poprawny
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

        // 4. Obsługa kliknięcia "Weryfikuj 2FA"
        btnVerifyLogin2FA.setOnClickListener(v -> verifyLogin2FA());
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

                            saveTokenToPrefs(idToken, userEmail);

                            if (requires2FA) {
                                Log.d(TAG, "Serwer wymaga 2FA. Pokazuję UI 2FA.");
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, "Wymagana weryfikacja 2FA", Toast.LENGTH_SHORT).show();
                                    show2FAInputUI(true);
                                    showLoading(false);
                                });
                            } else {
                                Log.d(TAG, "2FA nie jest wymagane. Loguję...");
                                runOnUiThread(() -> {
                                    Toast.makeText(LoginActivity.this, welcomeMsg, Toast.LENGTH_SHORT).show();
                                    startApp();
                                });
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

    private void verifyLogin2FA() {
        String code = editTextLogin2FA.getText().toString().trim();
        if (code.length() != 6) {
            showError("Kod 2FA musi mieć 6 cyfr.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        String idToken = prefs.getString(KEY_ID_TOKEN, null);

        if (idToken == null) {
            showError("Błąd sesji (brak tokena). Zaloguj się ponownie.");
            show2FAInputUI(false);
            return;
        }

        showLoading(true);
        Log.d(TAG, "Wysyłanie kodu 2FA do weryfikacji logowania...");

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("totpCode", code);
        } catch (JSONException e) { /*...*/ }
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.LOGIN_2FA_VERIFY_ENDPOINT)
                .header("Authorization", "Bearer " + idToken)
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
                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this, "Zalogowano pomyślnie!", Toast.LENGTH_SHORT).show();
                            startApp();
                        });
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

    private void saveTokenToPrefs(String idToken, String email) {
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ID_TOKEN, idToken)
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

    // Poprawka: Metoda odpowiedzialna tylko za ProgressBar
    private void showLoading(boolean isLoading) {
        loginProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    // Poprawka: Dodana brakująca metoda do przełączania widoków
    private void show2FAInputUI(boolean show) {
        if (show) {
            logoSection.setVisibility(View.GONE);
            btnGoogleSignIn.setVisibility(View.GONE);
            twoFaLoginSection.setVisibility(View.VISIBLE);
        } else {
            logoSection.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);
            twoFaLoginSection.setVisibility(View.GONE);
        }
    }

    // Poprawka: Dodana brakująca metoda do pokazywania błędów
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}