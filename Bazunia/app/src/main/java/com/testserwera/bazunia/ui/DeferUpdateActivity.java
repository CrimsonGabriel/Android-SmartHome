package com.testserwera.bazunia.ui;

import com.testserwera.bazunia.R;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.testserwera.bazunia.utils.Constants;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;


public class DeferUpdateActivity extends AppCompatActivity {

    private static final String TAG = "DeferUpdateActivity";
    private static final String UPDATE_PREFS = "UpdatePrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Bez UI - działamy w tle

        long assignmentId = getIntent().getLongExtra("ASSIGNMENT_ID", -1);
        String urgency = getIntent().getStringExtra("URGENCY");
        int notificationId = getIntent().getIntExtra("NOTIFICATION_ID", 0);

        if (assignmentId != -1) {
            handleDefer(assignmentId, urgency, notificationId);
        } else {
            finish();
        }
    }

    private void handleDefer(long id, String urgency, int notifId) {
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE);

        // 1. Zwiększamy licznik odroczeń dla tego konkretnego ID
        String countKey = "defer_count_" + id;
        int currentCount = prefs.getInt(countKey, 0);
        prefs.edit().putInt(countKey, currentCount + 1).apply();

        // 2. Logika Snooze (5 minut)
        long snoozeTime = 5 * 60 * 1000;
        prefs.edit().putLong("snooze_" + id, System.currentTimeMillis() + snoozeTime).apply();

        // 3. Usuń powiadomienie
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(notifId);

        // 4. Komunikaty (Twoja poprzednia logika)
        if ("REQUIRED".equals(urgency)) {
            // Jeśli to było pierwsze (i ostatnie) odłożenie
            if (currentCount == 0) {
                Toast.makeText(this, R.string.toast_update_postponed_timer, Toast.LENGTH_SHORT).show();
            } else {
                // Teoretycznie tu nie wejdzie, bo przycisk zniknie, ale dla bezpieczeństwa:
                Toast.makeText(this, "Tej aktualizacji nie można już odłożyć!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.toast_update_postponed_generic, Toast.LENGTH_SHORT).show();
        }

        sendUpdateStatus(id);
    }

    private void sendUpdateStatus(long id) {
        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Log.e(TAG, "Brak tokena, nie można wysłać statusu.");
            finish();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();
        try {
            // Wpisujemy status na sztywno, co rozwiązuje warning
            json.put("status", "DEFERRED");
        } catch (Exception e) {
            // Rozwiązanie problemu "Empty catch block"
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
                Log.e(TAG, "Nie udało się wysłać statusu DEFERRED", e);
                finish();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.w(TAG, "Backend odrzucił DEFERRED: " + r.code());
                    } else {
                        Log.i(TAG, "Status DEFERRED zapisany w backendzie.");
                    }
                } finally {
                    finish();
                }
            }
        });
    }
}