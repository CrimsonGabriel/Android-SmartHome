package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.VpsClientService;
import com.testserwera.bazunia.utils.Constants;
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

public class CreatePasswordActivity extends BaseActivity {

    private static final String TAG = "CreatePasswordActivity";
    private EditText editTextPassword, editTextConfirmPassword;
    private MaterialButton btnSetPassword;
    private ProgressBar progressBar;
    private OkHttpClient httpClient;
    private String currentJwtToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_password);

        httpClient = new OkHttpClient();

        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        currentJwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (currentJwtToken == null) {
            Log.e(TAG, "Krytyczny błąd: Brak tokena JWT na ekranie tworzenia hasła. Wracam do logowania.");
            Toast.makeText(this, "Błąd sesji, zaloguj się ponownie", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        btnSetPassword = findViewById(R.id.btnSetPassword);
        progressBar = findViewById(R.id.createPasswordProgressBar);

        btnSetPassword.setOnClickListener(v -> attemptPasswordSet());
    }

    private void attemptPasswordSet() {
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();

        if (password.length() < 8) {
            editTextPassword.setError(getString(R.string.error_password_short));
            editTextPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            editTextConfirmPassword.setError(getString(R.string.error_password_mismatch));
            editTextConfirmPassword.requestFocus();
            return;
        }

        sendPasswordToVps(password);
    }

    private void sendPasswordToVps(String password) {
        showLoading(true);

        JSONObject json = new JSONObject();
        try {
            json.put("newPassword", password);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            showLoading(false);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.SET_PASSWORD_ENDPOINT)
                .header("Authorization", "Bearer " + currentJwtToken)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania hasła: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(CreatePasswordActivity.this, R.string.toast_password_set_error, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        Log.i(TAG, "Hasło pomyślnie ustawione na serwerze.");
                        runOnUiThread(() -> {
                            Toast.makeText(CreatePasswordActivity.this, R.string.toast_password_set_success, Toast.LENGTH_SHORT).show();
                            startApp();
                        });
                    } else {
                        Log.w(TAG, "Serwer odrzucił ustawienie hasła, kod: " + r.code());
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(CreatePasswordActivity.this, R.string.toast_password_set_error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            }
        });
    }

    private void startApp() {
        Log.d(TAG, "Ustawiono hasło. Uruchamiam serwis i MainActivity.");
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        startForegroundService(serviceIntent);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finishAffinity();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSetPassword.setEnabled(!isLoading);
        editTextPassword.setEnabled(!isLoading);
        editTextConfirmPassword.setEnabled(!isLoading);
    }
}