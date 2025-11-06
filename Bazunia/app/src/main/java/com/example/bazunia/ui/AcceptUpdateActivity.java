package com.example.bazunia.ui;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bazunia.R;
import com.example.bazunia.data.VpsClientService;
import com.example.bazunia.ui.LoginActivity;
import com.example.bazunia.utils.Constants;
import android.content.SharedPreferences;
import okhttp3.*;
import java.io.IOException;
import java.util.Locale;
import org.json.JSONObject;


public class AcceptUpdateActivity extends AppCompatActivity {

    private static final String TAG = "AcceptUpdateActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String updateKey = getIntent().getStringExtra("UPDATE_KEY");
        final int notificationId = getIntent().getIntExtra("NOTIFICATION_ID", 0);

        if (updateKey != null) {
            sendUpdateDecisionToVps(updateKey, "ACCEPTED");

            // W zależności od klucza (App/GW-01), wywołaj odpowiednią akcję
            if ("App".equals(updateKey)) {
                Toast.makeText(this, getString(R.string.toast_update_app_starting), Toast.LENGTH_LONG).show();
                // W realnym świecie: otwórz Google Play Store lub rozpocznij pobieranie APK
            } else {
                Toast.makeText(this, getString(R.string.toast_update_gateway_starting, updateKey), Toast.LENGTH_LONG).show();
                // W realnym świecie: wyślij polecenie do bramki
            }
        } else {
            Log.e(TAG, "Brak klucza aktualizacji.");
        }

        // Zawsze usuń powiadomienie po akcji
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && notificationId != 0) {
            manager.cancel(notificationId);
        }

        finish();
    }

    // Metoda do wysłania decyzji do VPS (taka sama jak w DeferUpdateActivity)
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