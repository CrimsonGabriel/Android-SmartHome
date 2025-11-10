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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// ⭐️ ZMIANA: Implementujemy nowy interfejs adaptera ⭐️
public class DataActivity extends AppCompatActivity implements FolderAdapter.FolderCallback {

    private static final String TAG = "DataActivity";

    // ⭐️ ZMIANA: Pola dla RecyclerView ⭐️
    private RecyclerView recyclerView;
    private FolderAdapter adapter;
    private List<Object> displayItems = new ArrayList<>();
    private Map<Long, Boolean> folderExpansionState = new HashMap<>();
    private static final long UNCATEGORIZED_PARENT_ID = -99L;
    private Map<String, Boolean> gatewayExpansionState = new HashMap<>();
    private Object currentContextMenuItem; // Do przechowywania obiektu dla menu kontekstowego

    private DatabaseHelper dbHelper;
    private BroadcastReceiver dataUpdateReceiver;
    private AppearanceManager appearanceManager;
    private String currentTextScale, currentButtonScale;
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

        // ⭐️ ZMIANA: Inicjalizacja RecyclerView ⭐️
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderAdapter(this, displayItems, this);
        recyclerView.setAdapter(adapter);

        // Rejestracja dla menu kontekstowego
        registerForContextMenu(recyclerView);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);

        // ⭐️ ZMIANA: FAB dodaje folder ⭐️
        FloatingActionButton fabAddFolder = findViewById(R.id.fab_add_folder);
        fabAddFolder.setOnClickListener(v -> showCreateFolderDialog(null));

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, getString(R.string.data_refreshed_manually), Toast.LENGTH_SHORT).show();
            forceSync();
        });

        setupBroadcastReceiver();
        loadDisplayListFromDb();
    }

    private void forceSync() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        serviceIntent.putExtra("FORCE_SYNC_NOW", true); // To synchronizuje bramki i foldery (w zależności od implementacji serwisu)
        startService(serviceIntent);
    }

    // ⭐️ ZMIANA: Całkowicie nowa metoda do budowania listy ⭐️
    private void loadDisplayListFromDb() {
        displayItems.clear();

        // --- 1. Sekcja ULUBIONE ---
        displayItems.add(new FolderAdapter.SectionHeader(getString(R.string.section_favorites)));
        try (Cursor favGateways = dbHelper.getFavoriteGatewaysCursor()) {
            while (favGateways.moveToNext()) {
                // Bramki w ulubionych nie rozwijają się. parentFolderId = 0 (nieistotne)
                displayItems.add(new FolderAdapter.GatewayItem(favGateways, 0L, false, false));
            }
        }
        try (Cursor favSensors = dbHelper.getFavoriteSensorsCursor()) {
            while (favSensors.moveToNext()) {
                String gwName = "Bramka " + favSensors.getLong(favSensors.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_GATEWAY_ID));
                displayItems.add(new FolderAdapter.SensorItem(favSensors, gwName));
            }
        }

        // --- 2. Sekcje FOLDERÓW ---
        try (Cursor folders = dbHelper.getFoldersCursor()) {
            while (folders.moveToNext()) {
                long folderId = folders.getLong(folders.getColumnIndexOrThrow("_id"));
                boolean isExpanded = folderExpansionState.getOrDefault(folderId, false);
                FolderAdapter.FolderItem folderItem = new FolderAdapter.FolderItem(folders, isExpanded);
                displayItems.add(folderItem);

                if (isExpanded) {
                    try (Cursor gatewaysInFolder = dbHelper.getGatewaysForFolderCursor(folderId)) {
                        while (gatewaysInFolder.moveToNext()) {
                            long gatewayId = gatewaysInFolder.getLong(gatewaysInFolder.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));

                            // NOWY KLUCZ ZŁOŻONY (Folder)
                            String stateKey = "folder_" + folderId + "_gw_" + gatewayId;
                            boolean gwExpanded = gatewayExpansionState.getOrDefault(stateKey, false);

                            // Przekazujemy folderId jako parentId
                            FolderAdapter.GatewayItem gatewayItem = new FolderAdapter.GatewayItem(gatewaysInFolder, folderId, true, gwExpanded);
                            displayItems.add(gatewayItem);

                            if (gwExpanded) {
                                try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                                    while (sensors.moveToNext()) {
                                        displayItems.add(new FolderAdapter.SensorItem(sensors, null));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Sekcja BEZ FOLDERU ---
        displayItems.add(new FolderAdapter.SectionHeader(getString(R.string.section_uncategorized)));
        try (Cursor uncategorized = dbHelper.getUncategorizedGatewaysCursor()) {
            while (uncategorized.moveToNext()) {
                long gatewayId = uncategorized.getLong(uncategorized.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));

                // NOWY KLUCZ ZŁOŻONY (Bez kategorii)
                String stateKey = "uncat_gw_" + gatewayId;
                boolean gwExpanded = gatewayExpansionState.getOrDefault(stateKey, false);

                // Przekazujemy naszą stałą jako parentId
                FolderAdapter.GatewayItem gatewayItem = new FolderAdapter.GatewayItem(uncategorized, UNCATEGORIZED_PARENT_ID, true, gwExpanded);
                displayItems.add(gatewayItem);

                if (gwExpanded) {
                    try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                        while (sensors.moveToNext()) {
                            displayItems.add(new FolderAdapter.SensorItem(sensors, null));
                        }
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    // ⭐️ ZMIANA: Logika dla implementacji interfejsu ⭐️
    @Override
    public void onFolderClicked(FolderAdapter.FolderItem folder) {
        boolean isExpanded = folderExpansionState.getOrDefault(folder.id, false);
        folderExpansionState.put(folder.id, !isExpanded);
        loadDisplayListFromDb(); // Przebuduj listę
    }

    // ⭐️ ZMIANA 4: Zaktualizowana metoda obsługi kliknięcia ⭐️
    @Override
    public void onGatewayClicked(FolderAdapter.GatewayItem gateway) {
        if (!gateway.isSensorParent) return; // Bramki w ulubionych nic nie robią po kliknięciu

        // Tworzymy ten sam unikalny klucz, co w loadDisplayListFromDb()
        String stateKey;
        if (gateway.parentFolderId == UNCATEGORIZED_PARENT_ID) {
            stateKey = "uncat_gw_" + gateway.id;
        } else {
            stateKey = "folder_" + gateway.parentFolderId + "_gw_" + gateway.id;
        }

        // Zapisujemy stan do mapy używając nowego klucza
        boolean isExpanded = gatewayExpansionState.getOrDefault(stateKey, false);
        gatewayExpansionState.put(stateKey, !isExpanded);

        loadDisplayListFromDb(); // Przebuduj listę
    }

    @Override
    public void onSensorClicked(FolderAdapter.SensorItem sensor) {
        Intent intent = new Intent(DataActivity.this, SensorDetailActivity.class);
        intent.putExtra("SENSOR_ID_LONG", sensor.id);
        intent.putExtra("GATEWAY_ID_LONG", sensor.gatewayId);
        startActivity(intent);
    }

    @Override public void onFolderLongClicked(FolderAdapter.FolderItem folder, View view) {
        currentContextMenuItem = folder;
        openContextMenu(view);
    }
    @Override public void onGatewayLongClicked(FolderAdapter.GatewayItem gateway, View view) {
        currentContextMenuItem = gateway;
        openContextMenu(view);
    }
    @Override public void onSensorLongClicked(FolderAdapter.SensorItem sensor, View view) {
        currentContextMenuItem = sensor;
        openContextMenu(view);
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (currentContextMenuItem == null) return;

        MenuInflater inflater = getMenuInflater();

        // ⭐️ ZMIANA: Wczytujemy only odpowiednie menu ⭐️

        if (currentContextMenuItem instanceof FolderAdapter.FolderItem) {
            inflater.inflate(R.menu.folder_context_menu, menu);
            // Nie ma tu dynamicznych opcji, więc gotowe.

        } else if (currentContextMenuItem instanceof FolderAdapter.GatewayItem) {
            inflater.inflate(R.menu.gateway_context_menu, menu);

            // Zachowujemy logikę pokazywania/ukrywania opcji ulubionych
            long gwId = ((FolderAdapter.GatewayItem) currentContextMenuItem).id;
            boolean isFav = dbHelper.isFavoriteGateway(gwId);
            menu.findItem(R.id.menu_add_gateway_to_favorites).setVisible(!isFav);
            menu.findItem(R.id.menu_remove_gateway_from_favorites).setVisible(isFav);

        } else if (currentContextMenuItem instanceof FolderAdapter.SensorItem) {
            inflater.inflate(R.menu.sensor_context_menu, menu);
            // TODO: W przyszłości tutaj trzeba będzie dodać:
            // menu.findItem(R.id.menu_add_to_folder).setVisible(true);

            // Zachowujemy logikę pokazywania/ukrywania opcji ulubionych
            long sId = ((FolderAdapter.SensorItem) currentContextMenuItem).id;
            boolean isFav = dbHelper.isFavoriteSensor(sId);
            menu.findItem(R.id.menu_add_sensor_to_favorites).setVisible(!isFav);
            menu.findItem(R.id.menu_remove_sensor_from_favorites).setVisible(isFav);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (currentContextMenuItem == null) return false;

        // Logika dla FOLDERU
        if (currentContextMenuItem instanceof FolderAdapter.FolderItem) {
            FolderAdapter.FolderItem folder = (FolderAdapter.FolderItem) currentContextMenuItem;
            int itemId = item.getItemId();
            if (itemId == R.id.menu_edit_folder) {
                showCreateFolderDialog(folder);
                return true;
            } else if (itemId == R.id.menu_delete_folder) {
                showDeleteFolderDialog(folder);
                return true;
            }
        }
        // Logika dla BRAMKI
        else if (currentContextMenuItem instanceof FolderAdapter.GatewayItem) {
            FolderAdapter.GatewayItem gateway = (FolderAdapter.GatewayItem) currentContextMenuItem;
            int itemId = item.getItemId();
            if (itemId == R.id.menu_rename_gateway) {
                showRenameDialog(gateway.id, gateway.name, gateway.description, true);
                return true;
            } else if (itemId == R.id.menu_add_to_folder) {
                showSelectFolderDialog(gateway);
                return true;
            } else if (itemId == R.id.menu_add_gateway_to_favorites) {
                toggleFavoriteGateway(gateway.id, true);
                return true;
            } else if (itemId == R.id.menu_remove_gateway_from_favorites) {
                toggleFavoriteGateway(gateway.id, false);
                return true;
            } else if (itemId == R.id.menu_delete_gateway) {
                showDeleteGatewayDialog(gateway.id, gateway.name);
                return true;
            }
        }
        // Logika dla CZUJNIKA
        else if (currentContextMenuItem instanceof FolderAdapter.SensorItem) {
            FolderAdapter.SensorItem sensor = (FolderAdapter.SensorItem) currentContextMenuItem;
            int itemId = item.getItemId();
            if (itemId == R.id.menu_rename_sensor) {
                showRenameDialog(sensor.id, sensor.name, "Brak opisu", false); // TODO: Dodaj opis do SensorItem
                return true;
            } else if (itemId == R.id.menu_add_sensor_to_favorites) {
                toggleFavoriteSensor(sensor.id, true);
                return true;
            } else if (itemId == R.id.menu_remove_sensor_from_favorites) {
                toggleFavoriteSensor(sensor.id, false);
                return true;
            }
            // TODO: Dodać logikę dla R.id.menu_add_to_folder (gdy backend będzie gotowy)
        }

        currentContextMenuItem = null;
        return super.onContextItemSelected(item);
    }

    // --- NOWE DIALOGI I METODY SIECIOWE ---

    private void showCreateFolderDialog(FolderAdapter.FolderItem folder) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_folder, null);
        final EditText editName = dialogView.findViewById(R.id.edit_folder_name);
        final EditText editColor = dialogView.findViewById(R.id.edit_folder_color);

        boolean isEditMode = (folder != null);
        String title = isEditMode ? getString(R.string.dialog_edit_folder_title) : getString(R.string.dialog_create_folder_title);

        if (isEditMode) {
            editName.setText(folder.name);
            editColor.setText(folder.color);
        } else {
            editColor.setText("#"); // Domyślny
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                    String newName = editName.getText().toString().trim();
                    String newColor = editColor.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Nazwa nie może być pusta", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long folderId = isEditMode ? folder.id : -1; // -1 dla nowego
                    createOrUpdateFolderOnServer(folderId, newName, newColor);
                })
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void showDeleteFolderDialog(FolderAdapter.FolderItem folder) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_delete_folder)
                .setMessage("Czy na pewno chcesz usunąć folder '" + folder.name + "'? (Bramki nie zostaną usunięte)")
                .setIcon(R.drawable.ic_warning)
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> {
                    deleteFolderOnServer(folder.id);
                })
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void showSelectFolderDialog(FolderAdapter.GatewayItem gateway) {
        // 1. Pobierz listę folderów z bazy
        List<FolderAdapter.FolderItem> folders = new ArrayList<>();
        List<String> folderNames = new ArrayList<>();
        try (Cursor c = dbHelper.getFoldersCursor()) {
            while(c.moveToNext()) {
                FolderAdapter.FolderItem item = new FolderAdapter.FolderItem(c, false);
                folders.add(item);
                folderNames.add(item.name);
            }
        }

        if (folders.isEmpty()) {
            Toast.makeText(this, "Najpierw utwórz folder", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Pokaż dialog z listą
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, folderNames);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_folder_title)
                .setAdapter(adapter, (dialog, which) -> {
                    FolderAdapter.FolderItem selectedFolder = folders.get(which);
                    addGatewayToFolderOnServer(gateway.id, selectedFolder.id, selectedFolder.name);
                })
                .show();
    }

    // --- Metody do zmiany nazwy (stara logika, dostosowana) ---

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
                    if (newName.isEmpty()) return;

                    updateDetailsOnServer(id, newName, newDesc, isGateway); // Wyślij do VPS
                })
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void updateDetailsOnServer(long id, String name, String description, boolean isGateway) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        String url = isGateway ? (Constants.GATEWAYS_ENDPOINT + "/" + id) : (Constants.SENSORS_ENDPOINT + "/" + id);

        JSONObject json = new JSONObject();
        try {
            json.put("name", name);
            json.put("description", description);
            if (isGateway) json.put("folder", ""); // Musi być zgodne z DTO
        } catch (JSONException e) { return; }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).put(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, isGateway ? R.string.toast_gateway_updated : R.string.toast_sensor_updated, Toast.LENGTH_SHORT).show();
                        forceSync(); // Synchronizuj
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
            }
        });
    }

    // --- Metody do usuwania (stara logika, dostosowana) ---

    private void showDeleteGatewayDialog(long gatewayId, String gatewayName) {
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
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

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
                        forceSync();
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
            }
        });
    }

    // --- NOWE METODY SIECIOWE DLA FOLDERÓW/ULUBIONYCH ---

    private void createOrUpdateFolderOnServer(long folderId, String name, String color) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        boolean isEdit = (folderId != -1);
        String url = isEdit ? (Constants.FOLDERS_ENDPOINT + "/" + folderId) : Constants.FOLDERS_ENDPOINT;

        JSONObject json = new JSONObject();
        try {
            json.put("name", name);
            json.put("color", color);
        } catch (JSONException e) { return; }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken);

        if (isEdit) {
            requestBuilder.put(body);
        } else {
            requestBuilder.post(body);
        }

        httpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, isEdit ? R.string.toast_folder_updated : R.string.toast_folder_created, Toast.LENGTH_SHORT).show();
                        forceSync();
                    } else {
                        runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show());
                    }
                });
                response.close();
            }
        });
    }

    private void deleteFolderOnServer(long folderId) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        Request request = new Request.Builder()
                .url(Constants.FOLDERS_ENDPOINT + "/" + folderId)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .delete()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { /* ... */ }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, R.string.toast_folder_deleted, Toast.LENGTH_SHORT).show();
                        forceSync();
                    });
                }
                response.close();
            }
        });
    }

    // ⭐️⭐️⭐️ JEDYNA ZMIANA JEST TUTAJ ⭐️⭐️⭐️
    private void addGatewayToFolderOnServer(long gatewayId, long folderId, String folderName) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        String url = Constants.FOLDERS_ENDPOINT + "/" + folderId + "/gateways";
        JSONObject json = new JSONObject();
        try {
            json.put("gatewayId", gatewayId);
        } catch (JSONException e) { return; }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "addGatewayToFolderOnServer FAILURE", e);
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, getString(R.string.toast_added_to_folder, folderName), Toast.LENGTH_SHORT).show();
                        dbHelper.addGatewayToFolder(gatewayId, folderId);
                        loadDisplayListFromDb();
                    });
                } else {
                    // ⭐️ NOWA LOGIKA DEBUGOWANIA ⭐️
                    // Musimy odczytać ciało odpowiedzi, aby wiedzieć, co serwer powiedział
                    String errorBody = "Brak treści błędu";
                    try {
                        if (response.body() != null) {
                            errorBody = response.body().string();
                        }
                    } catch (IOException e) {
                        errorBody = "Nie można odczytać błędu: " + e.getMessage();
                    }

                    final String finalErrorBody = errorBody;
                    final int responseCode = response.code(); // Pobierz kod HTTP (np. 404, 500)

                    // Loguj błąd do Logcat
                    Log.e(TAG, "Błąd dodawania bramki. Kod: " + responseCode + ", Treść: " + finalErrorBody);

                    runOnUiThread(() -> {
                        // Pokaż deweloperowi (Tobie) co się stało
                        Toast.makeText(DataActivity.this,
                                "Błąd (Kod: " + responseCode + ")",
                                Toast.LENGTH_LONG).show(); // Pokaż sam kod na Toaście (treść może być za długa)
                    });
                }
                response.close();
            }
        });
    }

    private void toggleFavoriteGateway(long gatewayId, boolean add) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        String url = Constants.FAVORITE_GATEWAYS_ENDPOINT;
        Request request;

        if (add) {
            JSONObject json = new JSONObject();
            try { json.put("id", gatewayId); } catch (Exception e) {}
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        } else {
            request = new Request.Builder().url(url + "/" + gatewayId).addHeader("Authorization", "Bearer " + jwtToken).delete().build();
        }

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { /* ... */ }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, add ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites, Toast.LENGTH_SHORT).show();
                        forceSync();
                    });
                }
                response.close();
            }
        });
    }

    private void toggleFavoriteSensor(long sensorId, boolean add) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        String url = Constants.FAVORITE_SENSORS_ENDPOINT;
        Request request;

        if (add) {
            JSONObject json = new JSONObject();
            try { json.put("id", sensorId); } catch (Exception e) {}
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        } else {
            request = new Request.Builder().url(url + "/" + sensorId).addHeader("Authorization", "Bearer " + jwtToken).delete().build();
        }

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { /* ... */ }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, add ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites, Toast.LENGTH_SHORT).show();
                        forceSync();
                    });
                }
                response.close();
            }
        });
    }


    // --- Metody cyklu życia (dostosowane) ---

    private void setupBroadcastReceiver() {
        dataUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadDisplayListFromDb(); // ⭐️ ZMIANA
            }
        };
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
        loadDisplayListFromDb(); // ⭐️ ZMIANA
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        super.onPause();
    }
}