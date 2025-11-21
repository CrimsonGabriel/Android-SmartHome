package com.testserwera.bazunia.ui;

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
import android.widget.Toast;
import com.testserwera.bazunia.data.SensorStatusErrorDto;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.DatabaseHelper;
import com.testserwera.bazunia.data.VpsClientService;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.LocaleManager;
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

public class DataActivity extends AppCompatActivity implements FolderAdapter.FolderCallback {

    private static final String TAG = "DataActivity";

    private RecyclerView recyclerView;
    private FolderAdapter adapter;
    private List<Object> displayItems = new ArrayList<>();
    private Map<Long, Boolean> folderExpansionState = new HashMap<>();

    private static final long UNCATEGORIZED_PARENT_ID = -99L;
    private static final long PARENT_ID_FAVORITE = -1L;
    private static final long PARENT_ID_GATEWAY = -2L;

    private Map<String, Boolean> gatewayExpansionState = new HashMap<>();
    private Object currentContextMenuItem;

    private DatabaseHelper dbHelper;
    private BroadcastReceiver dataUpdateReceiver;
    private AppearanceManager appearanceManager;
    private String currentTextScale, currentButtonScale;
    private OkHttpClient httpClient;
    private SharedPreferences authPrefs;
    private BroadcastReceiver syncStatusReceiver;

    private long lastSyncToastTime = 0;
    private SharedPreferences mutePrefs;
    private MaterialButton btnTestConnection;
    private Gson gson;
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
        mutePrefs = getSharedPreferences("NotificationMutePrefs", Context.MODE_PRIVATE);
        gson = new Gson();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderAdapter(this, displayItems, this);
        recyclerView.setAdapter(adapter);

        registerForContextMenu(recyclerView);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        FloatingActionButton fabAddFolder = findViewById(R.id.fab_add_folder);
        fabAddFolder.setOnClickListener(v -> showCreateFolderDialog(null));

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnRefresh.setOnClickListener(v -> {
            forceSync();
        });
        btnTestConnection.setOnClickListener(v -> testConnection());
        setupBroadcastReceiver();
        loadDisplayListFromDb();
    }

    private void forceSync() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        serviceIntent.putExtra("FORCE_SYNC_NOW", true);
        startService(serviceIntent);
    }

    /**
     * Wymusza synchronizację tylko metadanych (w tym baterii),
     * wysyłając specjalny intent do VpsClientService.
     */
    private void forceBatteryCheck() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        // Używamy nowego "extra", który dodaliśmy w VpsClientService
        serviceIntent.putExtra("FORCE_BATTERY_CHECK_NOW", true);
        startService(serviceIntent);

        // Pokaż natychmiastowy Toast (używa stringu z Kroku 1)
        Toast.makeText(this, R.string.toast_battery_check_started, Toast.LENGTH_SHORT).show();
    }

    // ⭐️ POPRAWKA: Ulubione są teraz zwijanym folderem ⭐️
    private void loadDisplayListFromDb() {
        displayItems.clear();

        // --- 1. Sekcja ULUBIONE ---

        // 1. Pobierz stan rozwinięcia dla "Ulubionych"
        boolean isFavoritesExpanded = folderExpansionState.getOrDefault(PARENT_ID_FAVORITE, false);

        // 2. Dodaj "fałszywy" FolderItem (zamiast SectionHeader)
        displayItems.add(new FolderAdapter.FolderItem(
                PARENT_ID_FAVORITE,
                getString(R.string.section_favorites), // Używa stringu "Ulubione"
                isFavoritesExpanded
        ));

        // 3. Dodaj zawartość tylko jeśli sekcja jest rozwinięta
        if (isFavoritesExpanded) {
            try (Cursor favGateways = dbHelper.getFavoriteGatewaysCursor()) {
                while (favGateways.moveToNext()) {
                    long gatewayId = favGateways.getLong(favGateways.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
                    String stateKey = "fav_gw_" + gatewayId;
                    boolean gwExpanded = gatewayExpansionState.getOrDefault(stateKey, false);
                    displayItems.add(new FolderAdapter.GatewayItem(favGateways, PARENT_ID_FAVORITE, true, gwExpanded));

                    if (gwExpanded) {
                        try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                            while (sensors.moveToNext()) {
                                displayItems.add(new FolderAdapter.SensorItem(sensors, null, PARENT_ID_GATEWAY));
                            }
                        }
                    }
                }
            }
            try (Cursor favSensors = dbHelper.getFavoriteSensorsCursor()) {
                while (favSensors.moveToNext()) {
                    String gwName = favSensors.getString(favSensors.getColumnIndexOrThrow("gateway_name"));
                    displayItems.add(new FolderAdapter.SensorItem(favSensors, gwName, PARENT_ID_FAVORITE));
                }
            }
        }
        // ⭐️ KONIEC ZMIANY DLA ULUBIONYCH ⭐️

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
                            String stateKey = "folder_" + folderId + "_gw_" + gatewayId;
                            boolean gwExpanded = gatewayExpansionState.getOrDefault(stateKey, false);
                            FolderAdapter.GatewayItem gatewayItem = new FolderAdapter.GatewayItem(gatewaysInFolder, folderId, true, gwExpanded);
                            displayItems.add(gatewayItem);

                            if (gwExpanded) {
                                try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                                    while (sensors.moveToNext()) {
                                        displayItems.add(new FolderAdapter.SensorItem(sensors, null, PARENT_ID_GATEWAY));
                                    }
                                }
                            }
                        }
                    }
                    try (Cursor sensorsInFolder = dbHelper.getSensorsForFolderCursor(folderId)) {
                        while (sensorsInFolder.moveToNext()) {
                            String gwName = sensorsInFolder.getString(sensorsInFolder.getColumnIndexOrThrow("gateway_name"));
                            displayItems.add(new FolderAdapter.SensorItem(sensorsInFolder, gwName, folderId));
                        }
                    }
                }
            }
        }

        // --- 3. Sekcja "NIEZGRUPOWANE" (teraz zwijana) ---

        boolean isUncategorizedExpanded = folderExpansionState.getOrDefault(UNCATEGORIZED_PARENT_ID, false);

        displayItems.add(new FolderAdapter.FolderItem(
                UNCATEGORIZED_PARENT_ID,
                getString(R.string.section_uncategorized),
                isUncategorizedExpanded
        ));

        if (isUncategorizedExpanded) {
            try (Cursor uncategorized = dbHelper.getUncategorizedGatewaysCursor()) {
                while (uncategorized.moveToNext()) {
                    long gatewayId = uncategorized.getLong(uncategorized.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
                    String stateKey = "uncat_gw_" + gatewayId;
                    boolean gwExpanded = gatewayExpansionState.getOrDefault(stateKey, false);
                    FolderAdapter.GatewayItem gatewayItem = new FolderAdapter.GatewayItem(uncategorized, UNCATEGORIZED_PARENT_ID, true, gwExpanded);
                    displayItems.add(gatewayItem);

                    if (gwExpanded) {
                        try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                            while (sensors.moveToNext()) {
                                displayItems.add(new FolderAdapter.SensorItem(sensors, null, PARENT_ID_GATEWAY));
                            }
                        }
                    }
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onFolderClicked(FolderAdapter.FolderItem folder) {
        // Ta metoda automatycznie obsługuje "Niezgrupowane" (id -99) i "Ulubione" (id -1)
        boolean isExpanded = folderExpansionState.getOrDefault(folder.id, false);
        folderExpansionState.put(folder.id, !isExpanded);
        loadDisplayListFromDb();
    }

    @Override
    public void onGatewayClicked(FolderAdapter.GatewayItem gateway) {
        String stateKey;
        if (gateway.parentFolderId == UNCATEGORIZED_PARENT_ID) {
            stateKey = "uncat_gw_" + gateway.id;
        } else if (gateway.parentFolderId == PARENT_ID_FAVORITE) {
            stateKey = "fav_gw_" + gateway.id;
        } else {
            stateKey = "folder_" + gateway.parentFolderId + "_gw_" + gateway.id;
        }
        boolean isExpanded = gatewayExpansionState.getOrDefault(stateKey, false);
        gatewayExpansionState.put(stateKey, !isExpanded);
        loadDisplayListFromDb();
    }

    @Override
    public void onSensorClicked(FolderAdapter.SensorItem sensor) {
        Intent intent = new Intent(DataActivity.this, SensorDetailActivity.class);
        intent.putExtra("SENSOR_ID_LONG", sensor.id);
        intent.putExtra("GATEWAY_ID_LONG", sensor.gatewayId);
        startActivity(intent);
    }


    @Override public void onFolderLongClicked(FolderAdapter.FolderItem folder, View view) {
        // ⭐️ ZMIANA: Zezwalamy na menu dla wszystkich folderów ⭐️
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

        if (currentContextMenuItem instanceof FolderAdapter.FolderItem) {
            FolderAdapter.FolderItem folder = (FolderAdapter.FolderItem) currentContextMenuItem;
            inflater.inflate(R.menu.folder_context_menu, menu);

            long folderId = folder.id;
            boolean isSpecialFolder = (folderId == PARENT_ID_FAVORITE || folderId == UNCATEGORIZED_PARENT_ID);

            // Ukryj Edytuj/Usuń dla folderów specjalnych
            menu.findItem(R.id.menu_edit_folder).setVisible(!isSpecialFolder);
            menu.findItem(R.id.menu_delete_folder).setVisible(!isSpecialFolder);

            // 🔽🔽🔽 NOWA LOGIKA INTELIGENTNA DLA FOLDERÓW 🔽🔽🔽
            MenuItem threshItem = menu.findItem(R.id.menu_toggle_folder_threshold_notifications);
            MenuItem battItem = menu.findItem(R.id.menu_toggle_folder_battery_notifications);

            if (isSpecialFolder) {
                // Ukrywamy opcje grupowe dla specjalnych folderów (chyba że chcesz inaczej)
                if (threshItem != null) threshItem.setVisible(false);
                if (battItem != null) battItem.setVisible(false);
            } else {
                if (threshItem != null) {
                    boolean isThreshMuted = isFolderFullyMuted(folderId, "thresh_sensor_");
                    threshItem.setTitle(isThreshMuted ? R.string.action_enable_thresholds : R.string.action_disable_thresholds);
                }
                if (battItem != null) {
                    boolean isBattMuted = isFolderFullyMuted(folderId, "batt_sensor_");
                    battItem.setTitle(isBattMuted ? R.string.action_enable_battery : R.string.action_disable_battery);
                }
            }
        } else if (currentContextMenuItem instanceof FolderAdapter.GatewayItem) {
            inflater.inflate(R.menu.gateway_context_menu, menu);
            FolderAdapter.GatewayItem gateway = (FolderAdapter.GatewayItem) currentContextMenuItem;

            // --- Logika Ulubionych i Folderów (bez zmian) ---
            boolean isFav = dbHelper.isFavoriteGateway(gateway.id);
            menu.findItem(R.id.menu_add_gateway_to_favorites).setVisible(!isFav);
            menu.findItem(R.id.menu_remove_gateway_from_favorites).setVisible(isFav);
            boolean showRemoveFromFolder = (gateway.parentFolderId >= 0);
            menu.findItem(R.id.menu_remove_gateway_from_folder).setVisible(showRemoveFromFolder);
            boolean showAddToFolder = (gateway.parentFolderId != PARENT_ID_FAVORITE);
            menu.findItem(R.id.menu_add_to_folder).setVisible(showAddToFolder);

            // 🔽🔽🔽 NOWA LOGIKA DYNAMICZNYCH NAZW DLA BRAMEK 🔽🔽🔽

            // 1. Sprawdź stan dla PROGOWYCH
            // Jeśli jest w pełni wyciszona -> proponujemy "Włącz" (enable)
            // Jeśli działa (choć jeden czujnik) -> proponujemy "Wyłącz" (disable)
            boolean isThreshFullyMuted = isGatewayFullyMuted(gateway.id, "thresh_sensor_");
            MenuItem threshItem = menu.findItem(R.id.menu_toggle_gateway_threshold_notifications);
            if (isThreshFullyMuted) {
                threshItem.setTitle(R.string.action_enable_thresholds); // "Włącz..."
            } else {
                threshItem.setTitle(R.string.action_disable_thresholds); // "Wyłącz..."
            }

            // 2. Sprawdź stan dla BATERII
            boolean isBattFullyMuted = isGatewayFullyMuted(gateway.id, "batt_sensor_");
            MenuItem battItem = menu.findItem(R.id.menu_toggle_gateway_battery_notifications);
            if (isBattFullyMuted) {
                battItem.setTitle(R.string.action_enable_battery); // "Włącz..."
            } else {
                battItem.setTitle(R.string.action_disable_battery); // "Wyłącz..."
            }



        } else if (currentContextMenuItem instanceof FolderAdapter.SensorItem) {
            inflater.inflate(R.menu.sensor_context_menu, menu);
            FolderAdapter.SensorItem sensor = (FolderAdapter.SensorItem) currentContextMenuItem;

            // --- Logika Ulubionych i Folderów (bez zmian) ---
            boolean isFav = dbHelper.isFavoriteSensor(sensor.id);
            menu.findItem(R.id.menu_add_sensor_to_favorites).setVisible(!isFav);
            menu.findItem(R.id.menu_remove_sensor_from_favorites).setVisible(isFav);
            boolean showRemoveFromFolder = (sensor.parentFolderId >= 0);
            menu.findItem(R.id.menu_remove_sensor_from_folder).setVisible(showRemoveFromFolder);
            boolean showAddToFolder = (sensor.parentFolderId != PARENT_ID_FAVORITE);
            menu.findItem(R.id.menu_add_sensor_to_folder).setVisible(showAddToFolder);

            // --- Logika wyciszania dla czujnika (bez zmian) ---
            boolean isThreshMuted = mutePrefs.getBoolean("thresh_sensor_" + sensor.id, false);
            menu.findItem(R.id.menu_disable_threshold_notifications).setVisible(!isThreshMuted);
            menu.findItem(R.id.menu_enable_threshold_notifications).setVisible(isThreshMuted);
            boolean isBattMuted = mutePrefs.getBoolean("batt_sensor_" + sensor.id, false);
            menu.findItem(R.id.menu_disable_battery_notifications).setVisible(!isBattMuted);
            menu.findItem(R.id.menu_enable_battery_notifications).setVisible(isBattMuted);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (currentContextMenuItem == null) return false;

        // Pobierz edytor SharedPreferences (będzie potrzebny w wielu miejscach)
        SharedPreferences.Editor editor = mutePrefs.edit();

        // Logika dla FOLDERU
        if (currentContextMenuItem instanceof FolderAdapter.FolderItem) {
            FolderAdapter.FolderItem folder = (FolderAdapter.FolderItem) currentContextMenuItem;
            int itemId = item.getItemId();

            if (itemId == R.id.menu_edit_folder) {
                showCreateFolderDialog(folder); return true;
            } else if (itemId == R.id.menu_delete_folder) {
                showDeleteFolderDialog(folder); return true;
            } else if (itemId == R.id.action_force_sync) {
                forceSync(); return true;
            } else if (itemId == R.id.action_check_battery) {
                forceBatteryCheck(); return true;

                // 🔽🔽🔽 NOWA OBSŁUGA INTELIGENTNYCH PRZEŁĄCZNIKÓW 🔽🔽🔽

            } else if (itemId == R.id.menu_toggle_folder_threshold_notifications) {
                // 1. Sprawdź obecny stan
                boolean isCurrentlyMuted = isFolderFullyMuted(folder.id, "thresh_sensor_");
                // 2. Odwróć stan (jeśli wyciszone -> włącz, jeśli włączone -> wycisz)
                boolean newMuteState = !isCurrentlyMuted;

                // 3. Zapisz
                setFolderMuteState(folder, "thresh_sensor_", newMuteState);

                // 4. Pokaż komunikat
                int msgId = newMuteState ? R.string.toast_thresholds_disabled : R.string.toast_thresholds_enabled;
                Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.menu_toggle_folder_battery_notifications) {
                boolean isCurrentlyMuted = isFolderFullyMuted(folder.id, "batt_sensor_");
                boolean newMuteState = !isCurrentlyMuted;

                setFolderMuteState(folder, "batt_sensor_", newMuteState);

                int msgId = newMuteState ? R.string.toast_battery_disabled : R.string.toast_battery_enabled;
                Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
                return true;
            }
        }
        // Logika dla BRAMKI
        else if (currentContextMenuItem instanceof FolderAdapter.GatewayItem) {
            FolderAdapter.GatewayItem gateway = (FolderAdapter.GatewayItem) currentContextMenuItem;
            int itemId = item.getItemId();

            if (itemId == R.id.menu_rename_gateway) {
                showRenameDialog(gateway.id, gateway.name, gateway.description, true); return true;
            } else if (itemId == R.id.menu_add_to_folder) {
                showSelectFolderDialog(gateway); return true;
            } else if (itemId == R.id.menu_remove_gateway_from_folder) {
                removeGatewayFromFolderOnServer(gateway.id, gateway.parentFolderId); return true;
            } else if (itemId == R.id.menu_add_gateway_to_favorites) {
                toggleFavoriteGateway(gateway.id, true); return true;
            } else if (itemId == R.id.menu_remove_gateway_from_favorites) {
                toggleFavoriteGateway(gateway.id, false); return true;
            } else if (itemId == R.id.menu_delete_gateway) {
                showDeleteGatewayDialog(gateway.id, gateway.name); return true;
            } else if (itemId == R.id.action_force_sync) {
                forceSync(); return true;
            } else if (itemId == R.id.action_check_battery) {
                forceBatteryCheck(); return true;

                // 🔽🔽🔽 NOWA LOGIKA DLA BRAMKI 🔽🔽🔽
            } else if (itemId == R.id.menu_toggle_gateway_threshold_notifications) {
                boolean isCurrentlyMuted = isGatewayFullyMuted(gateway.id, "thresh_sensor_");
                boolean newMuteState = !isCurrentlyMuted; // Odwracamy stan

                setGatewayMuteState(gateway, "thresh_sensor_", newMuteState);

                int messageResId = newMuteState ? R.string.toast_thresholds_disabled : R.string.toast_thresholds_enabled;
                Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
                return true;

            } else if (itemId == R.id.menu_toggle_gateway_battery_notifications) {
                boolean isCurrentlyMuted = isGatewayFullyMuted(gateway.id, "batt_sensor_");
                boolean newMuteState = !isCurrentlyMuted;

                setGatewayMuteState(gateway, "batt_sensor_", newMuteState);

                int messageResId = newMuteState ? R.string.toast_battery_disabled : R.string.toast_battery_enabled;
                Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
                return true;
            }
        }
        // Logika dla CZUJNIKA
        else if (currentContextMenuItem instanceof FolderAdapter.SensorItem) {
            FolderAdapter.SensorItem sensor = (FolderAdapter.SensorItem) currentContextMenuItem;
            int itemId = item.getItemId();

            if (itemId == R.id.menu_rename_sensor) {
                showRenameDialog(sensor.id, sensor.name, "Brak opisu", false); return true;
            } else if (itemId == R.id.menu_add_sensor_to_folder) {
                showSelectFolderDialogForSensor(sensor); return true;
            } else if (itemId == R.id.menu_remove_sensor_from_folder) {
                removeSensorFromFolderOnServer(sensor.id, sensor.parentFolderId); return true;
            } else if (itemId == R.id.menu_add_sensor_to_favorites) {
                toggleFavoriteSensor(sensor.id, true); return true;
            } else if (itemId == R.id.menu_remove_sensor_from_favorites) {
                toggleFavoriteSensor(sensor.id, false); return true;
            } else if (itemId == R.id.action_check_battery) {
                forceBatteryCheck(); return true;

                // --- Logika wyciszania dla czujnika (bez zmian) ---
            } else if (itemId == R.id.menu_disable_threshold_notifications) {
                editor.putBoolean("thresh_sensor_" + sensor.id, true).apply();
                Toast.makeText(this, R.string.menu_disable_threshold_notifications, Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_enable_threshold_notifications) {
                editor.putBoolean("thresh_sensor_" + sensor.id, false).apply();
                Toast.makeText(this, R.string.menu_enable_threshold_notifications, Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_disable_battery_notifications) {
                editor.putBoolean("batt_sensor_" + sensor.id, true).apply();
                Toast.makeText(this, R.string.menu_disable_battery_notifications, Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.menu_enable_battery_notifications) {
                editor.putBoolean("batt_sensor_" + sensor.id, false).apply();
                Toast.makeText(this, R.string.menu_enable_battery_notifications, Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        currentContextMenuItem = null;
        return super.onContextItemSelected(item);
    }

    // --- DIALOGI I METODY SIECIOWE ---
    // (Reszta pliku pozostaje bez zmian)

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
            editColor.setText("#");
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
                    long folderId = isEditMode ? folder.id : -1;
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, folderNames);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_folder_title)
                .setAdapter(adapter, (dialog, which) -> {
                    FolderAdapter.FolderItem selectedFolder = folders.get(which);
                    addGatewayToFolderOnServer(gateway.id, selectedFolder.id, selectedFolder.name);
                })
                .show();
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
                    if (newName.isEmpty()) return;
                    updateDetailsOnServer(id, newName, newDesc, isGateway);
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
            if (isGateway) json.put("folder", "");
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
                        forceSync();
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                response.close();
            }
        });
    }

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
        if (isEdit) { requestBuilder.put(body); } else { requestBuilder.post(body); }
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
                    String errorBody = "Brak treści błędu";
                    try { if (response.body() != null) { errorBody = response.body().string(); } } catch (IOException e) { errorBody = "Nie można odczytać błędu: " + e.getMessage(); }
                    final String finalErrorBody = errorBody;
                    final int responseCode = response.code();
                    Log.e(TAG, "Błąd dodawania bramki. Kod: " + responseCode + ", Treść: " + finalErrorBody);
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, "Błąd (Kod: " + responseCode + ")", Toast.LENGTH_LONG).show();
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

    private void showSelectFolderDialogForSensor(FolderAdapter.SensorItem sensor) {
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, folderNames);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_select_folder_title)
                .setAdapter(adapter, (dialog, which) -> {
                    FolderAdapter.FolderItem selectedFolder = folders.get(which);
                    addSensorToFolderOnServer(sensor.id, selectedFolder.id, selectedFolder.name);
                })
                .show();
    }

    private void addSensorToFolderOnServer(long sensorId, long folderId, String folderName) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;
        String url = Constants.FOLDERS_ENDPOINT + "/" + folderId + "/sensors";
        JSONObject json = new JSONObject();
        try {
            json.put("id", sensorId);
        } catch (JSONException e) { return; }
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, getString(R.string.toast_added_to_folder, folderName), Toast.LENGTH_SHORT).show();
                        dbHelper.addSensorToFolder(sensorId, folderId);
                        loadDisplayListFromDb();
                    });
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Brak treści błędu";
                    Log.e(TAG, "Błąd dodawania czujnika. Kod: " + response.code() + ", Treść: " + errorBody);
                    runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd (Kod: " + response.code() + ")", Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }

    private void removeGatewayFromFolderOnServer(long gatewayId, long folderId) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;

        String url = Constants.FOLDERS_ENDPOINT + "/" + folderId + "/gateways/" + gatewayId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .delete()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, R.string.toast_removed_from_folder, Toast.LENGTH_SHORT).show();

                        dbHelper.removeGatewayFromFolder(gatewayId, folderId);
                        loadDisplayListFromDb();
                    });
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Brak treści błędu";
                    Log.e(TAG, "Błąd usuwania bramki. Kod: " + response.code() + ", Treść: " + errorBody);
                    runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd (Kod: " + response.code() + ")", Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }

    private void removeSensorFromFolderOnServer(long sensorId, long folderId) {
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) return;
        String url = Constants.FOLDERS_ENDPOINT + "/" + folderId + "/sensors/" + sensorId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .delete()
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, R.string.toast_removed_from_folder, Toast.LENGTH_SHORT).show();
                        dbHelper.removeSensorFromFolder(sensorId, folderId);
                        loadDisplayListFromDb();
                    });
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "Brak treści błędu";
                    Log.e(TAG, "Błąd usuwania czujnika. Kod: " + response.code() + ", Treść: " + errorBody);
                    runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd (Kod: " + response.code() + ")", Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }

    // --- Metody cyklu życia ---

    private void setupBroadcastReceiver() {
        dataUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadDisplayListFromDb();
            }
        };
        syncStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra("SYNC_SUCCESS", false);
                long now = System.currentTimeMillis();

                // Prosty debounce, aby uniknąć podwójnych Toastów (limit 1 na 3 sekundy)
                if (now - lastSyncToastTime < 3000) {
                    Log.d(TAG, "SyncStatusReceiver: Zignorowano zduplikowany broadcast o sukcesie.");
                    return; // Zignoruj ten broadcast, jest za wcześnie
                }

                if (success) {
                    Toast.makeText(context, R.string.sync_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, R.string.sync_error, Toast.LENGTH_SHORT).show();
                }

                // Zapisz czas ostatniego Toasta (tylko jeśli pokazaliśmy błąd lub sukces)
                lastSyncToastTime = now;
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
        LocalBroadcastManager.getInstance(this).registerReceiver(syncStatusReceiver, new IntentFilter(Constants.ACTION_SYNC_STATUS));
        loadDisplayListFromDb();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStatusReceiver);
        super.onPause();
    }

    /**
     * 🔽🔽🔽 NOWA METODA POMOCNICZA 🔽🔽🔽
     * Ustawia stan wyciszenia (mute) dla wszystkich czujników w danej BRAMCE.
     * @param gateway Bramka, której czujniki mają być zmienione
     * @param prefKeyPrefix Klucz SharedPreferences (np. "thresh_sensor_" lub "batt_sensor_")
     * @param mute Wartość do ustawienia (true = wycisz, false = odcisz)
     */
    private void setGatewayMuteState(FolderAdapter.GatewayItem gateway, String prefKeyPrefix, boolean mute) {
        Log.d(TAG, "Zmieniam stan wyciszenia dla bramki " + gateway.id + " na " + mute);
        SharedPreferences.Editor editor = mutePrefs.edit();
        try (Cursor c = dbHelper.getSensorsForGateway(gateway.id)) {
            while (c.moveToNext()) {
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                editor.putBoolean(prefKeyPrefix + sensorId, mute);
                // Log.d(TAG, "  -> Zastosowano dla sensorId: " + sensorId);
            }
        }
        editor.apply();
    }
    /**
     * Sprawdza, czy WSZYSTKIE czujniki w danej bramce są wyciszone dla danego typu powiadomień.
     * @return true jeśli wszystkie są wyciszone (lub brak czujników), false jeśli choć jeden jest aktywny.
     */
    private boolean isGatewayFullyMuted(long gatewayId, String prefKeyPrefix) {
        boolean allMuted = true;
        boolean hasSensors = false;

        try (Cursor c = dbHelper.getSensorsForGateway(gatewayId)) {
            while (c.moveToNext()) {
                hasSensors = true;
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                // Domyślnie (brak wpisu) jest false (niewyciszony)
                boolean isMuted = mutePrefs.getBoolean(prefKeyPrefix + sensorId, false);

                if (!isMuted) {
                    // Znaleźliśmy czujnik, który NIE jest wyciszony.
                    // Zatem bramka jako całość "działa".
                    allMuted = false;
                    break;
                }
            }
        }
        // Jeśli nie ma czujników, traktujemy jako "wyciszoną" (nie ma czego wyłączać)
        return !hasSensors || allMuted;
    }
    /**
     * Sprawdza, czy WSZYSTKIE czujniki w folderze (i jego bramkach) są wyciszone.
     */
    private boolean isFolderFullyMuted(long folderId, String prefKeyPrefix) {
        boolean allMuted = true;
        boolean hasSensors = false;

        // 1. Sprawdź czujniki bezpośrednio w folderze
        try (Cursor c = dbHelper.getSensorsForFolderCursor(folderId)) {
            while (c.moveToNext()) {
                hasSensors = true;
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                if (!mutePrefs.getBoolean(prefKeyPrefix + sensorId, false)) {
                    allMuted = false;
                    break; // Znaleziono niewyciszony
                }
            }
        }

        // 2. Jeśli nadal allMuted == true, sprawdź bramki w folderze
        if (allMuted) {
            try (Cursor gateways = dbHelper.getGatewaysForFolderCursor(folderId)) {
                while (gateways.moveToNext()) {
                    long gatewayId = gateways.getLong(gateways.getColumnIndexOrThrow("_id"));
                    // Używamy istniejącej metody dla bramki
                    if (!isGatewayFullyMuted(gatewayId, prefKeyPrefix)) {
                        allMuted = false;
                        hasSensors = true;
                        break;
                    }
                }
            }
        }

        // Jeśli brak czujników, uznajemy za wyciszone (true)
        return !hasSensors || allMuted;
    }

    /**
     * 🔽🔽🔽 NOWA METODA POMOCNICZA 🔽🔽🔽
     * Ustawia stan wyciszenia (mute) dla wszystkich czujników w danym FOLDERZE.
     * @param folder Folder, którego czujniki mają być zmienione
     * @param prefKeyPrefix Klucz SharedPreferences (np. "thresh_sensor_" lub "batt_sensor_")
     * @param mute Wartość do ustawienia (true = wycisz, false = odcisz)
     */
    private void setFolderMuteState(FolderAdapter.FolderItem folder, String prefKeyPrefix, boolean mute) {
        Log.d(TAG, "Zmieniam stan wyciszenia dla folderu " + folder.id + " na " + mute);
        SharedPreferences.Editor editor = mutePrefs.edit();

        // 1. Zastosuj dla czujników bezpośrednio w folderze
        try (Cursor c = dbHelper.getSensorsForFolderCursor(folder.id)) {
            while (c.moveToNext()) {
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                editor.putBoolean(prefKeyPrefix + sensorId, mute);
                // Log.d(TAG, "  -> Zastosowano dla sensora w folderze: " + sensorId);
            }
        }

        // 2. Zastosuj dla wszystkich czujników we wszystkich bramkach w tym folderze
        try (Cursor gateways = dbHelper.getGatewaysForFolderCursor(folder.id)) {
            while (gateways.moveToNext()) {
                long gatewayId = gateways.getLong(gateways.getColumnIndexOrThrow("_id"));
                // Log.d(TAG, "  -> Przetwarzam bramkę w folderze: " + gatewayId);
                try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                    while (sensors.moveToNext()) {
                        long sensorId = sensors.getLong(sensors.getColumnIndexOrThrow("_id"));
                        editor.putBoolean(prefKeyPrefix + sensorId, mute);
                        // Log.d(TAG, "    -> Zastosowano dla sensora w bramce: " + sensorId);
                    }
                }
            }
        }
        editor.apply();
    }
    /**
     * Wykonuje ręczny test połączenia i statusu urządzeń.
     * Wywoływany przez przycisk btnTestConnection.
     */
    private void testConnection() {
        // Pokaż natychmiastową informację zwrotną
        Toast.makeText(this, "Testowanie połączenia...", Toast.LENGTH_SHORT).show();

        // 1. Zdobądź token
        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, "Błąd: Brak tokena logowania.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Zbuduj zapytanie (zakładam, że masz SENSOR_STATUS_ENDPOINT w Constants)
        // Jeśli nie, użyj pełnego URL
        Request request = new Request.Builder()
                .url(Constants.SENSOR_STATUS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        // 3. Wywołaj asynchronicznie (tak jak reszta metod w tej klasie)
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Błąd sieci (np. brak internetu, serwer nieosiągalny)
                runOnUiThread(() -> Toast.makeText(DataActivity.this,
                        "Błąd sieci: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    // Sukces - parsowanie JSON
                    Type listType = new TypeToken<List<SensorStatusErrorDto>>() {}.getType();

                    try {
                        // Użyj GSON do sparsowania odpowiedzi
                        List<SensorStatusErrorDto> errors = gson.fromJson(responseBody, listType);

                        if (errors == null || errors.isEmpty()) {
                            // Wszystko OK
                            runOnUiThread(() -> Toast.makeText(DataActivity.this,
                                    "Połączenie OK. Wszystkie urządzenia online.",
                                    Toast.LENGTH_LONG).show());
                        } else {
                            // Znaleziono błąd
                            String firstErrorMessage = errors.get(0).readableMessage;
                            runOnUiThread(() -> Toast.makeText(DataActivity.this,
                                    "Wykryto błąd: " + firstErrorMessage,
                                    Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        // Błąd parsowania JSON
                        Log.e(TAG, "testConnection: Błąd parsowania JSON: " + e.getMessage() + ", Odpowiedź: " + responseBody);
                        runOnUiThread(() -> Toast.makeText(DataActivity.this,
                                "Błąd parsowania odpowiedzi serwera.",
                                Toast.LENGTH_LONG).show());
                    }

                } else {
                    // Błąd API (np. 404, 500, 403)
                    Log.e(TAG, "testConnection: Błąd serwera. Kod: " + response.code() + ", Odpowiedź: " + responseBody);
                    runOnUiThread(() -> Toast.makeText(DataActivity.this,
                            "Błąd odpowiedzi serwera (kod: " + response.code() + ")",
                            Toast.LENGTH_LONG).show());
                }
                response.close(); // Zawsze zamykaj response w onResponse
            }
        });
    }

}
