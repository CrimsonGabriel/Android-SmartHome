package com.testserwera.bazunia.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.utils.Constants;

import android.content.SharedPreferences;
import okhttp3.*;
import java.io.IOException;
import java.util.Locale;
import org.json.JSONObject;


public class DeferUpdateActivity extends AppCompatActivity {

    private static final String TAG = "DeferUpdateActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Uwaga: Nie ustawiamy setContentView, ponieważ ta aktywność działa w tle

        final String updateKey = getIntent().getStringExtra("UPDATE_KEY");
        final int notificationId = getIntent().getIntExtra("NOTIFICATION_ID", 0);

        if (updateKey != null) {
            sendUpdateDecisionToVps(updateKey, "DEFERRED");
            Toast.makeText(this, getString(R.string.toast_update_deferred, updateKey), Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Brak klucza aktualizacji.");
        }

        // Zawsze usuń powiadomienie po akcji
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && notificationId != 0) {
            manager.cancel(notificationId);
        }

        // Aktywność musi się natychmiast zamknąć
        finish();
    }

    // Metoda do wysłania decyzji do VPS
    private void sendUpdateDecisionToVps(String key, String decision) {
        SharedPreferences authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);

        if (jwtToken == null) {
            Log.e(TAG, getString(R.string.log_error_no_token));
            return;
        }

        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("key", key);
            json.put("decision", decision);
        } catch (Exception e) { /* ... */ }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Constants.UPDATE_DECISION_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "BLAD DECYZJI: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "SUKCES: Decyzja '" + decision + "' dla " + key + " wysłana do VPS.");
                } else {
                    Log.w(TAG, String.format(Locale.getDefault(), "DECYZJA NIEUDANA, kod: %d", response.code()));
                }
            }
        });
    }
}