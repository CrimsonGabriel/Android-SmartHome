package com.testserwera.bazunia.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.utils.Constants;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AcceptUpdateActivity extends AppCompatActivity {

    private static final String TAG = "AcceptUpdateActivity";

    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvPercent;
    private Button btnClose;
    private long assignmentId;
    // notificationId usunięte stąd (zmienna lokalna)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accept_update);

        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvPercent = findViewById(R.id.tvProgressPercent);
        btnClose = findViewById(R.id.btnClose);

        assignmentId = getIntent().getLongExtra("ASSIGNMENT_ID", -1);
        // Zmienna lokalna, bo używana tylko tutaj
        int notificationId = getIntent().getIntExtra("NOTIFICATION_ID", 0);

        if (assignmentId == -1) {
            Toast.makeText(this, R.string.error_update_id, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Usuń powiadomienie
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId);

        btnClose.setOnClickListener(v -> finish());

        // Rozpocznij symulację instalacji
        startInstallationProcess();
    }

    private void startInstallationProcess() {
        tvStatus.setText(getString(R.string.update_status_downloading));

        // Symulacja postępu w nowym wątku
        new Thread(() -> {
            for (int i = 0; i <= 100; i += 2) {
                try {
                    Thread.sleep(50); // Szybkość paska
                } catch (InterruptedException e) {
                    // Zamiana printStackTrace na logowanie
                    Log.e(TAG, "Przerwano wątek instalacji", e);
                }

                int progress = i;
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                    // Użycie resource string z placeholderem
                    tvPercent.setText(getString(R.string.progress_percent_format, progress));

                    if (progress == 50) tvStatus.setText(getString(R.string.update_status_installing));
                    if (progress == 80) tvStatus.setText(getString(R.string.update_status_verifying));
                });
            }

            // Po zakończeniu paska, wyślij request do backendu
            // Usunięto parametr "COMPLETED", bo jest wpisany na sztywno w metodzie
            runOnUiThread(() -> sendUpdateStatus(assignmentId));
        }).start();
    }

    // Usunięto parametr 'String status' - zawsze wysyłamy "COMPLETED"
    private void sendUpdateStatus(long id) {
        tvStatus.setText(getString(R.string.update_status_finalizing));
        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();
        try {
            json.put("status", "COMPLETED");
        } catch (Exception e) {
            // Obsługa pustego bloku catch
            Log.e(TAG, "Błąd tworzenia JSON statusu", e);
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));

        // Używamy nowego endpointu: /api/updates/{id}/status
        String url = Constants.UPDATE_BASE_ENDPOINT + "/" + id + "/status";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    tvStatus.setText(R.string.error_connection);
                    // Użycie stringa z placeholderem dla błędu
                    Toast.makeText(AcceptUpdateActivity.this,
                            getString(R.string.error_with_message, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    btnClose.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // Try-with-resources dla Response
                try (Response r = response) {
                    final boolean success = r.isSuccessful();
                    final int code = r.code(); // To jest int

                    runOnUiThread(() -> {
                        if (success) {
                            tvStatus.setText(getString(R.string.update_success));
                            progressBar.setProgress(100);
                            new Handler(Looper.getMainLooper()).postDelayed(AcceptUpdateActivity.this::finish, 2000);
                        } else {
                            // ⭐️ POPRAWKA TUTAJ: Przekazujemy 'code' (int) bezpośrednio, bez String.valueOf()
                            tvStatus.setText(getString(R.string.update_error_server, code));
                            btnClose.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }
}