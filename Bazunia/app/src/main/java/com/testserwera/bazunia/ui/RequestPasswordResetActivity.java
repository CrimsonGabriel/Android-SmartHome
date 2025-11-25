package com.testserwera.bazunia.ui;

import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
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
public class RequestPasswordResetActivity extends BaseActivity {

    private static final String TAG = "RequestResetActivity";

    private EditText editTextEmail;
    private MaterialButton btnRequestReset;
    private ProgressBar resetProgressBar;
    private final OkHttpClient httpClient = new OkHttpClient();

    // ZMIANA: Usunięto attachBaseContext

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ZMIANA: Usunięto AppearanceManager
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_password_reset);

        editTextEmail = findViewById(R.id.editTextEmail);
        btnRequestReset = findViewById(R.id.btnRequestReset);
        resetProgressBar = findViewById(R.id.resetProgressBar);

        MaterialToolbar toolbar = findViewById(R.id.toolbarRequestReset);
        toolbar.setNavigationOnClickListener(v -> finish());

        String email = getIntent().getStringExtra("USER_EMAIL");
        if (email != null) {
            editTextEmail.setText(email);
        }

        btnRequestReset.setOnClickListener(v -> attemptResetRequest());
    }

    private void attemptResetRequest() {
        String email = editTextEmail.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError(getString(R.string.register_error_email_invalid));
            editTextEmail.requestFocus();
            return;
        }

        sendResetRequest(email);
    }

    private void sendResetRequest(String email) {
        showLoading(true);

        JSONObject json = new JSONObject();
        try {
            json.put("email", email);
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            showLoading(false);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.REQUEST_PASSWORD_RESET_ENDPOINT)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd prośby o reset: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    showError(getString(R.string.toast_reset_password_error));
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(RequestPasswordResetActivity.this,
                            R.string.toast_reset_password_success,
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void showLoading(boolean isLoading) {
        resetProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRequestReset.setEnabled(!isLoading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}