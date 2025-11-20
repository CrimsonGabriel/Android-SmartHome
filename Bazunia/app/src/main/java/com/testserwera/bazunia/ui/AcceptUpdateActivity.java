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
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class AcceptUpdateActivity extends AppCompatActivity {

    private static final String TAG = "AcceptUpdateActivity";
    private ProgressBar progressBar;
    private TextView tvStatus, tvPercent;
    private Button btnClose;
    private long assignmentId;
    private int notificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accept_update);

        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvPercent = findViewById(R.id.tvProgressPercent);
        btnClose = findViewById(R.id.btnClose);

        assignmentId = getIntent().getLongExtra("ASSIGNMENT_ID", -1);
        notificationId = getIntent().getIntExtra("NOTIFICATION_ID", 0);

        if (assignmentId == -1) {
            Toast.makeText(this, "Błąd ID aktualizacji", Toast.LENGTH_SHORT).show();
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
                } catch (InterruptedException e) { e.printStackTrace(); }

                int progress = i;
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                    tvPercent.setText(progress + "%");
                    if (progress == 50) tvStatus.setText(getString(R.string.update_status_installing));
                    if (progress == 80) tvStatus.setText(getString(R.string.update_status_verifying));
                });
            }

            // Po zakończeniu paska, wyślij request do backendu
            runOnUiThread(() -> sendUpdateStatus(assignmentId, "COMPLETED"));
        }).start();
    }

    private void sendUpdateStatus(long id, String status) {
        tvStatus.setText(getString(R.string.update_status_finalizing));
        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();
        try {
            json.put("status", status);
        } catch (Exception e) {}

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
                    tvStatus.setText("Błąd połączenia!");
                    Toast.makeText(AcceptUpdateActivity.this, "Błąd: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnClose.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        tvStatus.setText(getString(R.string.update_success));
                        progressBar.setProgress(100);
                        // Opcjonalnie zamknij sam po chwili
                        new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2000);
                    } else {
                        tvStatus.setText(getString(R.string.update_error_server, response.code()));
                        btnClose.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }
}