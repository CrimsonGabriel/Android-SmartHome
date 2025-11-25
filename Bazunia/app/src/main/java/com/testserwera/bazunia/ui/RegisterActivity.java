package com.testserwera.bazunia.ui;


import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
// import androidx.appcompat.app.AppCompatActivity; // ZMIANA: Niepotrzebne

import com.testserwera.bazunia.R;
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

// ZMIANA: BaseActivity
public class RegisterActivity extends BaseActivity {

    private static final String TAG = "RegisterActivity";

    private EditText editTextEmail, editTextPassword, editTextConfirmPassword;
    private MaterialButton btnRegister, btnLoginLink;
    private ProgressBar registerProgressBar;

    private final OkHttpClient httpClient = new OkHttpClient();

    // ZMIANA: Usunięto attachBaseContext

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ZMIANA: Usunięto AppearanceManager
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnLoginLink = findViewById(R.id.btnLoginLink);
        registerProgressBar = findViewById(R.id.registerProgressBar);

        btnRegister.setOnClickListener(v -> attemptRegistration());
        btnLoginLink.setOnClickListener(v -> finish());
    }

    private void attemptRegistration() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError(getString(R.string.register_error_email_invalid));
            editTextEmail.requestFocus();
            return;
        }
        if (password.length() < 8) {
            editTextPassword.setError(getString(R.string.register_error_password_short));
            editTextPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            editTextConfirmPassword.setError(getString(R.string.register_error_password_mismatch));
            editTextConfirmPassword.requestFocus();
            return;
        }

        registerUser(email, password);
    }

    private void registerUser(String email, String password) {
        showLoading(true);

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
            json.put("password", password);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            showLoading(false);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.REGISTER_ANDROID_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd rejestracji: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    showError(getString(R.string.login_error_server));
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response resp = response) {
                    final String responseBody = resp.body() != null ? resp.body().string() : "";

                    if (resp.isSuccessful()) {
                        Log.i(TAG, "Rejestracja wysłana pomyślnie. Czekanie na aktywację.");
                        runOnUiThread(() -> {
                            showLoading(false);
                            showSuccessDialog();
                        });
                    } else if (resp.code() == 409) {
                        Log.w(TAG, "Rejestracja nieudana: E-mail zajęty.");
                        runOnUiThread(() -> {
                            showLoading(false);
                            editTextEmail.setError(getString(R.string.register_error_email_taken));
                            editTextEmail.requestFocus();
                        });
                    } else {
                        Log.w(TAG, "Błąd serwera rejestracji, kod: " + resp.code() + ", body: " + responseBody);
                        runOnUiThread(() -> {
                            showLoading(false);
                            showError(getString(R.string.login_error_server));
                        });
                    }
                }
            }
        });
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.register_success_title)
                .setMessage(R.string.register_success_message)
                .setPositiveButton(R.string.register_button_ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void showLoading(boolean isLoading) {
        registerProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
        btnLoginLink.setEnabled(!isLoading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}