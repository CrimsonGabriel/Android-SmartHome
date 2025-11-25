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
// ZMIANA: Nie importujemy już AppCompatActivity, bo dziedziczymy po BaseActivity (w tym samym pakiecie)
// import androidx.appcompat.app.AppCompatActivity;

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

// ZMIANA: Dziedziczymy po BaseActivity
public class AcceptUpdateActivity extends BaseActivity {

    private static final String TAG = "AcceptUpdateActivity";

    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvPercent;
    private Button btnClose;
    private long assignmentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseActivity załatwia motywy
        setContentView(R.layout.activity_accept_update);

        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvPercent = findViewById(R.id.tvProgressPercent);
        btnClose = findViewById(R.id.btnClose);

        assignmentId = getIntent().getLongExtra("ASSIGNMENT_ID", -1);
        int notificationId = getIntent().getIntExtra("NOTIFICATION_ID", 0);

        if (assignmentId == -1) {
            Toast.makeText(this, R.string.error_update_id, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notificationId);

        btnClose.setOnClickListener(v -> finish());

        startInstallationProcess();
    }

    private void startInstallationProcess() {
        tvStatus.setText(getString(R.string.update_status_downloading));

        new Thread(() -> {
            for (int i = 0; i <= 100; i += 2) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Przerwano wątek instalacji", e);
                }

                int progress = i;
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                    tvPercent.setText(getString(R.string.progress_percent_format, progress));

                    if (progress == 50) tvStatus.setText(getString(R.string.update_status_installing));
                    if (progress == 80) tvStatus.setText(getString(R.string.update_status_verifying));
                });
            }

            runOnUiThread(() -> sendUpdateStatus(assignmentId));
        }).start();
    }

    private void sendUpdateStatus(long id) {
        tvStatus.setText(getString(R.string.update_status_finalizing));
        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();
        try {
            json.put("status", "COMPLETED");
        } catch (Exception e) {
            Log.e(TAG, "Błąd tworzenia JSON statusu", e);
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
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
                    Toast.makeText(AcceptUpdateActivity.this,
                            getString(R.string.error_with_message, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    btnClose.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    final boolean success = r.isSuccessful();
                    final int code = r.code();

                    runOnUiThread(() -> {
                        if (success) {
                            tvStatus.setText(getString(R.string.update_success));
                            progressBar.setProgress(100);
                            new Handler(Looper.getMainLooper()).postDelayed(AcceptUpdateActivity.this::finish, 2000);
                        } else {
                            tvStatus.setText(getString(R.string.update_error_server, code));
                            btnClose.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }
}