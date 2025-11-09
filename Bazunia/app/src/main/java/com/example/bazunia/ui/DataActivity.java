package com.example.bazunia.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.annotation.NonNull; // <<< OTO POPRAWKA (DODANY IMPORT)
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.bazunia.R;
import com.example.bazunia.data.DatabaseHelper;
import com.example.bazunia.data.VpsClientService;
import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.utils.LocaleManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DataActivity extends AppCompatActivity {

    private static final String TAG = "DataActivity"; // <<< Dodano TAG do logowania
    private ExpandableListView expandableListView;
    private DatabaseHelper dbHelper;
    private GatewaySensorCursorAdapter adapter;
    private BroadcastReceiver dataUpdateReceiver;

    private AppearanceManager appearanceManager;
    private String currentTextScale;
    private String currentButtonScale;

    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        LocaleManager localeManager = new LocaleManager(newBase);
        super.attachBaseContext(localeManager.setLocale(newBase)); // <<< POPRAWKA LITERÓWKI
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        currentTextScale = appearanceManager.getTextScale();
        currentButtonScale = appearanceManager.getButtonScale();
        appearanceManager.applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        dbHelper = new DatabaseHelper(this);
        httpClient = new OkHttpClient();
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);

        expandableListView = findViewById(R.id.expandableListView);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);
        FloatingActionButton fabAddGateway = findViewById(R.id.fab_add_gateway);

        adapter = new GatewaySensorCursorAdapter(null, this);
        expandableListView.setAdapter(adapter);


        expandableListView.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            Log.d(TAG, "Kliknięto czujnik o ID: " + id);

            Intent intent = new Intent(DataActivity.this, SensorDetailActivity.class);

            // Musimy pobrać ID bramki z rodzica
            Cursor groupCursor = (Cursor) adapter.getGroup(groupPosition);

            // <<< POPRAWKA: Użyj "_id" zamiast G_COLUMN_ID >>>
            long gatewayId = groupCursor.getLong(groupCursor.getColumnIndexOrThrow("_id"));

            intent.putExtra("SENSOR_ID_LONG", id); // ID Czujnika (np. 111)
            intent.putExtra("GATEWAY_ID_LONG", gatewayId); // ID Bramki (np. 100)
            startActivity(intent);
            return true;
        });

        // Długie przytrzymanie grupy (bramki) - Req 2.2, 2.3, 2.5
        expandableListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (ExpandableListView.getPackedPositionType(id) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                // 'id' w tym przypadku to ID bramki z bazy (np. 100)
                showGatewayContextMenu(id);
                return true;
            }
            return false;
        });

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.data_refreshed_manually), Toast.LENGTH_SHORT).show();
            Intent serviceIntent = new Intent(this, VpsClientService.class);
            startService(serviceIntent);
        });

        fabAddGateway.setOnClickListener(v -> {
            Toast.makeText(this, "Proces dodawania bramki (parowanie) - niezaimplementowane.", Toast.LENGTH_LONG).show();
        });

        setupBroadcastReceiver();
        loadGatewaysFromDb();
    }

    private void setupBroadcastReceiver() {
        dataUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadGatewaysFromDb();
            }
        };
    }

    private void loadGatewaysFromDb() {
        Cursor newCursor = dbHelper.getAllGateways();
        adapter.changeCursor(newCursor);
    }

    private void showGatewayContextMenu(long gatewayId) {
        // ... (bez zmian)
        final CharSequence[] items = {
                getString(R.string.menu_rename), // Req 2.2
                getString(R.string.menu_move_folder), // Req 2.3
                getString(R.string.menu_delete) // Req 2.5
        };

        new AlertDialog.Builder(this)
                .setTitle("Opcje Bramki")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showRenameGatewayDialog(gatewayId);
                            break;
                        case 1:
                            // TODO: Implementacja dialogu zmiany folderu
                            break;
                        case 2:
                            showDeleteGatewayDialog(gatewayId);
                            break;
                    }
                })
                .show();
    }

    private void showRenameGatewayDialog(long gatewayId) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_gateway, null);
        final EditText editName = dialogView.findViewById(R.id.edit_gateway_name);
        final EditText editDesc = dialogView.findViewById(R.id.edit_gateway_description);

        // TODO: Wypełnij pola aktualnymi danymi z dbHelper.getGatewayDetails(gatewayId)

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_rename_gateway_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                    String newName = editName.getText().toString();
                    String newDesc = editDesc.getText().toString();
                    updateGatewayOnServer(gatewayId, newName, newDesc, null);
                })
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void showDeleteGatewayDialog(long gatewayId) {
        // TODO: Pobierz nazwę bramki z bazy
        String gatewayName = "Bramka " + gatewayId;

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_gateway_title)
                .setMessage(String.format(getString(R.string.dialog_delete_gateway_message), gatewayName))
                .setIcon(R.drawable.ic_warning)
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    deleteGatewayOnServer(gatewayId);
                })
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void updateGatewayOnServer(long gatewayId, String name, String description, String folder) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        JSONObject json = new JSONObject();
        try {
            json.put("name", name);
            json.put("description", description);
            json.put("folder", folder);
        } catch (JSONException e) {
            // <<< POPRAWKA: Lepsze logowanie niż printStackTrace >>>
            Log.e(TAG, "Błąd tworzenia JSON dla aktualizacji bramki", e);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.GATEWAYS_ENDPOINT + "/" + gatewayId)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .put(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            // <<< POPRAWKA: Dodano adnotacje @NonNull >>>
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
            }
            // <<< POPRAWKA: Dodano adnotacje @NonNull >>>
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, R.string.toast_gateway_updated, Toast.LENGTH_SHORT).show();
                        startService(new Intent(DataActivity.this, VpsClientService.class));
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void deleteGatewayOnServer(long gatewayId) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        Request request = new Request.Builder()
                .url(Constants.GATEWAYS_ENDPOINT + "/" + gatewayId)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .delete()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            // <<< POPRAWKA: Dodano adnotacje @NonNull >>>
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show());
            }
            // <<< POPRAWKA: Dodano adnotacje @NonNull >>>
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, R.string.toast_gateway_deleted, Toast.LENGTH_SHORT).show();
                        startService(new Intent(DataActivity.this, VpsClientService.class));
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (LocaleManager.languageChanged) {
            LocaleManager.languageChanged = false;
            recreate();
            return;
        }
        if (appearanceManager != null && (!currentTextScale.equals(appearanceManager.getTextScale()) ||
                !currentButtonScale.equals(appearanceManager.getButtonScale()))) {
            recreate();
            return;
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        loadGatewaysFromDb();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        if (adapter != null) {
            adapter.changeCursor(null);
        }
        super.onPause();
    }
}