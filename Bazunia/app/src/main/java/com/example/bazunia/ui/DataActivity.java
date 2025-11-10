package com.example.bazunia.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

    private static final String TAG = "DataActivity";
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
        super.attachBaseContext(localeManager.setLocale(newBase));
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

        // Używamy poprawnego, 3-argumentowego konstruktora
        adapter = new GatewaySensorCursorAdapter(null, this, dbHelper);
        expandableListView.setAdapter(adapter);

        // Rejestracja dla menu kontekstowego
        registerForContextMenu(expandableListView);

        expandableListView.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            Log.d(TAG, "Kliknięto czujnik o ID: " + id);
            Intent intent = new Intent(DataActivity.this, SensorDetailActivity.class);
            Cursor groupCursor = (Cursor) adapter.getGroup(groupPosition);
            long gatewayId = groupCursor.getLong(groupCursor.getColumnIndexOrThrow("_id"));

            intent.putExtra("SENSOR_ID_LONG", id);
            intent.putExtra("GATEWAY_ID_LONG", gatewayId);
            startActivity(intent);
            return true;
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        ExpandableListView.ExpandableListContextMenuInfo info =
                (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP ||
                type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.data_context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        ExpandableListView.ExpandableListContextMenuInfo info =
                (ExpandableListView.ExpandableListContextMenuInfo) item.getMenuInfo();

        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        long id = info.id; // To jest ID bramki lub czujnika z kolumny "_id"

        int groupPos = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int childPos = ExpandableListView.getPackedPositionChild(info.packedPosition);

        int itemId = item.getItemId();
        if (itemId == R.id.menu_rename) {
            if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                // To jest BRAMKA
                Cursor cursor = (Cursor) adapter.getGroup(groupPos);
                String oldName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
                String oldDesc = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_DESCRIPTION));
                showRenameDialog(id, oldName, oldDesc, true); // true = isGateway
            } else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                // To jest CZUJNIK
                Cursor cursor = (Cursor) adapter.getChild(groupPos, childPos);
                String oldName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_NAME));
                String oldDesc = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_DESCRIPTION));
                showRenameDialog(id, oldName, oldDesc, false); // false = isGateway
            }
            return true;
        } else if (itemId == R.id.menu_delete) {
            if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                showDeleteGatewayDialog(id);
            } else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                // TODO: showDeleteSensorDialog(id);
                Toast.makeText(this, "Usuwanie czujnika - do zaimplementowania", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onContextItemSelected(item);
    }

    private void showRenameDialog(long id, String oldName, String oldDescription, boolean isGateway) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_rename, null);
        final EditText editName = dialogView.findViewById(R.id.edit_name);
        final EditText editDesc = dialogView.findViewById(R.id.edit_description);

        editName.setText(oldName);
        editDesc.setText(oldDescription);

        String title = isGateway ? getString(R.string.dialog_rename_gateway_title) : getString(R.string.dialog_rename_sensor_title);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                    String newName = editName.getText().toString().trim();
                    String newDesc = editDesc.getText().toString().trim();

                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Nazwa nie może być pusta", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isGateway) {
                        dbHelper.updateGatewayDetails(id, newName, newDesc);
                    } else {
                        dbHelper.updateSensorDetails(id, newName, newDesc);
                    }

                    loadGatewaysFromDb(); // Odśwież widok
                    updateDetailsOnServer(id, newName, newDesc, isGateway); // Wyślij do VPS
                })
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void updateDetailsOnServer(long id, String name, String description, boolean isGateway) {
        // <<< ⭐️⭐️⭐️ POPRAWKA 1 ⭐️⭐️⭐️ >>>
        // Musimy użyć 'LoginActivity.KEY_JWT_TOKEN', a nie 'LoginActivity.AUTH_PREFS'
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, "Błąd: Brak tokena logowania.", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = isGateway ?
                (Constants.GATEWAYS_ENDPOINT + "/" + id) :
                (Constants.SENSORS_ENDPOINT + "/" + id);

        JSONObject json = new JSONObject();
        try {
            json.put("name", name);
            json.put("description", description);
            if (isGateway) {
                json.put("folder", ""); // Przekaż pusty folder
            }
        } catch (JSONException e) {
            Log.e(TAG, "Błąd tworzenia JSON", e);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .put(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        String msg = isGateway ? getString(R.string.toast_gateway_updated) : getString(R.string.toast_sensor_updated);
                        Toast.makeText(DataActivity.this, msg, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
            }
        });
    }

    private void showDeleteGatewayDialog(long gatewayId) {
        String gatewayName = "ID: " + gatewayId;

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

    private void deleteGatewayOnServer(long gatewayId) {
        // <<< ⭐️⭐️⭐️ POPRAWKA 2 ⭐️⭐️⭐️ >>>
        // Musimy użyć 'LoginActivity.KEY_JWT_TOKEN', a nie 'LoginActivity.AUTH_PREFS'
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, "Błąd: Brak tokena logowania.", Toast.LENGTH_SHORT).show();
            return;
        }

        Request request = new Request.Builder()
                .url(Constants.GATEWAYS_ENDPOINT + "/" + gatewayId)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .delete()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, R.string.toast_gateway_deleted, Toast.LENGTH_SHORT).show();
                        Intent serviceIntent = new Intent(DataActivity.this, VpsClientService.class);
                        startService(serviceIntent);
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
            }
        });
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
        if (adapter != null) {
            adapter.changeCursor(newCursor);
        }
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
            adapter.changeCursor(null); // Zwalniamy cursor, gdy activity jest w tle
        }
        super.onPause();
    }
}