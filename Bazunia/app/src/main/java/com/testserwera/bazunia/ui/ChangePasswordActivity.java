package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.utils.Constants;
import com.google.android.material.appbar.MaterialToolbar;
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
public class ChangePasswordActivity extends BaseActivity {

    private static final String TAG = "ChangePasswordActivity";

    private EditText editTextCurrentPassword, editTextNewPassword, editTextConfirmNewPassword;
    private MaterialButton btnUpdatePassword;
    private ProgressBar progressBar;
    private OkHttpClient httpClient;
    private String currentJwtToken;

    // ZMIANA: Usunięto attachBaseContext

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ZMIANA: Usunięto ręczne AppearanceManager
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        httpClient = new OkHttpClient();

        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        currentJwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (currentJwtToken == null) {
            Log.e(TAG, "Krytyczny błąd: Brak tokena JWT. Wracam do poprzedniego ekranu.");
            Toast.makeText(this, "Błąd sesji, zaloguj się ponownie", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbarChangePassword);
        toolbar.setNavigationOnClickListener(v -> finish());

        editTextCurrentPassword = findViewById(R.id.editTextCurrentPassword);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmNewPassword = findViewById(R.id.editTextConfirmNewPassword);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);
        progressBar = findViewById(R.id.changePasswordProgressBar);

        btnUpdatePassword.setOnClickListener(v -> attemptPasswordChange());
    }

    private void attemptPasswordChange() {
        String currentPass = editTextCurrentPassword.getText().toString().trim();
        String newPass = editTextNewPassword.getText().toString().trim();
        String confirmPass = editTextConfirmNewPassword.getText().toString().trim();

        if (currentPass.isEmpty()) {
            editTextCurrentPassword.setError(getString(R.string.toast_error_current_password_empty));
            editTextCurrentPassword.requestFocus();
            return;
        }
        if (newPass.length() < 8) {
            editTextNewPassword.setError(getString(R.string.toast_error_new_password_short));
            editTextNewPassword.requestFocus();
            return;
        }
        if (!newPass.equals(confirmPass)) {
            editTextConfirmNewPassword.setError(getString(R.string.toast_error_new_password_mismatch));
            editTextConfirmNewPassword.requestFocus();
            return;
        }

        sendPasswordChangeToVps(currentPass, newPass);
    }

    private void sendPasswordChangeToVps(String currentPassword, String newPassword) {
        showLoading(true);

        JSONObject json = new JSONObject();
        try {
            json.put("currentPassword", currentPassword);
            json.put("newPassword", newPassword);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            showLoading(false);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.CHANGE_PASSWORD_ENDPOINT)
                .header("Authorization", "Bearer " + currentJwtToken)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd wysyłania zmiany hasła: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(ChangePasswordActivity.this, R.string.toast_password_change_error, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.i(TAG, "Hasło pomyślnie zmienione na serwerze.");
                    runOnUiThread(() -> {
                        Toast.makeText(ChangePasswordActivity.this, R.string.toast_password_changed_success, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    final String errorBody = response.body() != null ? response.body().string() : "Brak ciała odpowiedzi";
                    Log.w(TAG, "Serwer odrzucił zmianę hasła, kod: " + response.code() + ", Body: " + errorBody);

                    runOnUiThread(() -> {
                        showLoading(false);
                        if (response.code() == 403 || response.code() == 401) {
                            Toast.makeText(ChangePasswordActivity.this, R.string.toast_error_current_password_wrong, Toast.LENGTH_LONG).show();
                            editTextCurrentPassword.requestFocus();
                        } else {
                            Toast.makeText(ChangePasswordActivity.this, R.string.toast_password_change_error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnUpdatePassword.setEnabled(!isLoading);
        editTextCurrentPassword.setEnabled(!isLoading);
        editTextNewPassword.setEnabled(!isLoading);
        editTextConfirmNewPassword.setEnabled(!isLoading);
    }
}