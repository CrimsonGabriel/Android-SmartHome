package com.example.bazunia;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
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

    private GoogleSignInClient mGoogleSignInClient;
    private SignInButton btnGoogleSignIn;
    private ProgressBar loginProgressBar;

    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new AppearanceManager(this).applyAppearance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loginProgressBar = findViewById(R.id.loginProgressBar);

        // 1. Konfiguracja Google Sign-In
        // WAŻNE: Użyj swojego Client ID z VPS (tego "Web application")
        String webClientId = "79063316759-iva8uesd0vlj3in6eaeralk2kdkgv5or.apps.googleusercontent.com";

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 2. Rejestracja launchera do obsługi wyniku logowania
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

        // 3. Obsługa kliknięcia przycisku
        btnGoogleSignIn.setOnClickListener(v -> {
            Log.d(TAG, "Rozpoczynanie logowania Google...");
            showLoading(true);
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            signInLauncher.launch(signInIntent);
        });
    }

    /**
     * Wywoływane po pomyślnym zalogowaniu przez użytkownika w Google.
     */
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

            // Mamy token, wysyłamy go na nasz serwer VPS do weryfikacji
            sendTokenToVps(idToken);

        } catch (ApiException e) {
            Log.e(TAG, "Logowanie Google nieudane, kod błędu: " + e.getStatusCode());
            showError(getString(R.string.login_invalid_credentials));
            showLoading(false);
        }
    }

    /**
     * Wysyła token Google na nasz serwer VPS (POST /auth/google).
     */
    private void sendTokenToVps(String idToken) {
        Log.d(TAG, "Wysyłanie tokena do weryfikacji na VPS...");

        JSONObject json = new JSONObject();
        try {
            json.put("token", idToken);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            showLoading(false);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.GOOGLE_AUTH_ENDPOINT) // Używamy nowej stałej
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania tokena do VPS: " + e.getMessage());
                runOnUiThread(() -> {
                    showError("Błąd połączenia z serwerem.");
                    showLoading(false);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    Log.i(TAG, "SUKCES: VPS zweryfikował użytkownika!");
                    try {
                        JSONObject respJson = new JSONObject(responseBody);
                        String welcomeMsg = respJson.optString("message", getString(R.string.login_welcome_default));

                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this, welcomeMsg, Toast.LENGTH_SHORT).show();
                            startApp();
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "Błąd parsowania odpowiedzi VPS", e);
                        runOnUiThread(LoginActivity.this::startApp);
                    }

                } else {
                    Log.w(TAG, "Serwer VPS odrzucił logowanie, kod: " + response.code() + ", body: " + responseBody);
                    runOnUiThread(() -> {
                        showError("Serwer odrzucił logowanie. Możliwe, że nie masz uprawnień.");
                        showLoading(false);
                        // Dodatkowe wylogowanie z Google na kliencie
                        mGoogleSignInClient.signOut();
                    });
                }
            }
        });
    }

    /**
     * Uruchamia VpsClientService i przechodzi do MainActivity.
     */
    private void startApp() {
        Log.d(TAG, "Autoryzacja kompletna. Uruchamiam serwis i MainActivity.");

        Intent serviceIntent = new Intent(this, VpsClientService.class);
        startForegroundService(serviceIntent);

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        btnGoogleSignIn.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        loginProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Ciche logowanie
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getIdToken() != null) {
            Log.d(TAG, "Znaleziono ostatnio zalogowanego użytkownika. Weryfikuję token...");
            showLoading(true);
            sendTokenToVps(account.getIdToken());
        }
    }
}

