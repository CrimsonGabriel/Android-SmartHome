package com.testserwera.bazunia.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.DatabaseHelper;
import com.testserwera.bazunia.data.Gateway;
import com.testserwera.bazunia.data.SensorStatusErrorDto;
import com.testserwera.bazunia.data.VpsClientService;
import com.testserwera.bazunia.utils.AppearanceManager;
import com.testserwera.bazunia.utils.Constants;
import com.testserwera.bazunia.utils.LocaleManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
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

// ZMIANA: BaseActivity
public class DataActivity extends BaseActivity implements FolderAdapter.FolderCallback {

    private static final String TAG = "DataActivity";

    private final List<Object> displayItems = new ArrayList<>();
    private final Map<Long, Boolean> folderExpansionState = new HashMap<>();
    private final Map<String, Boolean> gatewayExpansionState = new HashMap<>();

    private FolderAdapter adapter;

    private static final long UNCATEGORIZED_PARENT_ID = -99L;
    private static final long PARENT_ID_FAVORITE = -1L;
    private static final long PARENT_ID_GATEWAY = -2L;

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
    private Gson gson;
    private boolean showSyncToast = false;

    private String filterQuery = "";
    private String filterType = null;
    private Long filterGatewayId = null;
    private FloatingActionButton fabFilter;

    // ZMIANA: Usunięto attachBaseContext

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appearanceManager = new AppearanceManager(this);
        currentTextScale = appearanceManager.getTextScale();
        currentButtonScale = appearanceManager.getButtonScale();
        // ZMIANA: Usunięto applyAppearance

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        dbHelper = new DatabaseHelper(this);
        httpClient = new OkHttpClient();
        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        mutePrefs = getSharedPreferences("NotificationMutePrefs", Context.MODE_PRIVATE);
        gson = new Gson();

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FolderAdapter(this, displayItems, this);
        recyclerView.setAdapter(adapter);

        registerForContextMenu(recyclerView);

        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);
        MaterialButton btnTestConnection = findViewById(R.id.btnTestConnection);

        FloatingActionButton fabAddFolder = findViewById(R.id.fab_add_folder);
        fabFilter = findViewById(R.id.fab_filter);

        fabAddFolder.setOnClickListener(v -> showCreateFolderDialog(null));
        fabFilter.setOnClickListener(v -> showFilterBottomSheet());

        btnBack.setOnClickListener(v -> finish());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnRefresh.setOnClickListener(v -> forceSync());
        btnTestConnection.setOnClickListener(v -> testConnection());

        setupBroadcastReceiver();
        loadDisplayListFromDb();
        updateFilterFabState();
    }

    // --- LOGIKA FILTROWANIA ---

    private boolean isFilterActive() {
        return (filterQuery != null && !filterQuery.isEmpty()) ||
                filterType != null ||
                filterGatewayId != null;
    }

    private void updateFilterFabState() {
        if (isFilterActive()) {
            // Jeśli filtr aktywny - na czerwono
            fabFilter.setImageTintList(getColorStateList(android.R.color.holo_red_light));
        } else {
            // Jeśli filtr nieaktywny - pobierz kolor z motywu (?attr/colorOnSurface)
            android.util.TypedValue typedValue = new android.util.TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
            int colorOnSurface = typedValue.data;

            fabFilter.setImageTintList(android.content.res.ColorStateList.valueOf(colorOnSurface));
        }
    }

    private void showFilterBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_filter, findViewById(android.R.id.content), false);
        bottomSheetDialog.setContentView(sheetView);

        TextInputEditText editSearch = sheetView.findViewById(R.id.editSearchFilter);
        AutoCompleteTextView autoType = sheetView.findViewById(R.id.autoCompleteType);
        AutoCompleteTextView autoGateway = sheetView.findViewById(R.id.autoCompleteGateway);
        MaterialButton btnClear = sheetView.findViewById(R.id.btnClearFilter);
        MaterialButton btnApply = sheetView.findViewById(R.id.btnApplyFilter);

        editSearch.setText(filterQuery);

        List<String> types = dbHelper.getAllSensorTypes();
        types.add(0, getString(R.string.filter_all_types));
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        autoType.setAdapter(typeAdapter);
        if (filterType != null) {
            autoType.setText(filterType, false);
        } else {
            autoType.setText(types.get(0), false);
        }

        List<Gateway> gateways = dbHelper.getAllGatewaysList();
        List<String> gatewayNames = new ArrayList<>();
        gatewayNames.add(getString(R.string.filter_all_gateways));
        int selectedGatewayIndex = 0;
        for (int i = 0; i < gateways.size(); i++) {
            gatewayNames.add(gateways.get(i).getName());
            if (filterGatewayId != null && filterGatewayId == gateways.get(i).getId()) {
                selectedGatewayIndex = i + 1;
            }
        }
        ArrayAdapter<String> gatewayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, gatewayNames);
        autoGateway.setAdapter(gatewayAdapter);
        autoGateway.setText(gatewayNames.get(selectedGatewayIndex), false);

        btnClear.setOnClickListener(v -> {
            filterQuery = "";
            filterType = null;
            filterGatewayId = null;
            loadDisplayListFromDb();
            updateFilterFabState();
            bottomSheetDialog.dismiss();
            Toast.makeText(this, R.string.filters_cleared_toast, Toast.LENGTH_SHORT).show();
        });

        btnApply.setOnClickListener(v -> {
            filterQuery = editSearch.getText() != null ? editSearch.getText().toString().trim() : "";

            String selectedType = autoType.getText().toString();
            if (selectedType.equals(getString(R.string.filter_all_types)) || selectedType.isEmpty()) {
                filterType = null;
            } else {
                filterType = selectedType;
            }

            String selectedGatewayName = autoGateway.getText().toString();
            filterGatewayId = null;
            if (!selectedGatewayName.equals(getString(R.string.filter_all_gateways))) {
                for (Gateway g : gateways) {
                    if (g.getName().equals(selectedGatewayName)) {
                        filterGatewayId = g.getId();
                        break;
                    }
                }
            }

            loadDisplayListFromDb();
            updateFilterFabState();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private boolean sensorMatchesFilter(Cursor cursor, String gwName) {
        if (!isFilterActive()) return true;

        boolean match = true;

        if (!filterQuery.isEmpty()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
            String desc = cursor.getString(cursor.getColumnIndexOrThrow("description"));
            String q = filterQuery.toLowerCase();

            boolean textMatch = (name != null && name.toLowerCase().contains(q)) ||
                    (type != null && type.toLowerCase().contains(q)) ||
                    (desc != null && desc.toLowerCase().contains(q)) ||
                    (gwName != null && gwName.toLowerCase().contains(q));
            if (!textMatch) match = false;
        }

        if (match && filterType != null) {
            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
            if (type == null || !type.equalsIgnoreCase(filterType)) {
                match = false;
            }
        }

        if (match && filterGatewayId != null) {
            long gwId = cursor.getLong(cursor.getColumnIndexOrThrow("gateway_id"));
            if (gwId != filterGatewayId) {
                match = false;
            }
        }

        return match;
    }

    private boolean gatewayMatchesFilter(Cursor cursor) {
        if (!isFilterActive()) return true;

        boolean match = true;

        if (!filterQuery.isEmpty()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
            String desc = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_DESCRIPTION));
            String q = filterQuery.toLowerCase();
            if ((name == null || !name.toLowerCase().contains(q)) &&
                    (desc == null || !desc.toLowerCase().contains(q))) {
                match = false;
            }
        }

        if (match && filterGatewayId != null) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
            if (id != filterGatewayId) {
                match = false;
            }
        }

        if (filterType != null) {
            match = false;
        }

        return match;
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadDisplayListFromDb() {
        new Thread(() -> {
            List<Object> tempItems = new ArrayList<>();
            boolean filtering = isFilterActive();

            // --- 1. Sekcja ULUBIONE ---
            List<Object> favoriteChildren = new ArrayList<>();

            try (Cursor favGateways = dbHelper.getFavoriteGatewaysCursor()) {
                while (favGateways.moveToNext()) {
                    long gatewayId = favGateways.getLong(favGateways.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
                    String gwName = favGateways.getString(favGateways.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));

                    boolean gwMatches = gatewayMatchesFilter(favGateways);

                    List<FolderAdapter.SensorItem> matchingSensors = new ArrayList<>();
                    try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                        while (sensors.moveToNext()) {
                            if (sensorMatchesFilter(sensors, gwName)) {
                                matchingSensors.add(new FolderAdapter.SensorItem(sensors, null, PARENT_ID_GATEWAY));
                            }
                        }
                    }

                    if (gwMatches || !matchingSensors.isEmpty()) {
                        String stateKey = "fav_gw_" + gatewayId;
                        boolean gwExpanded = filtering ? !matchingSensors.isEmpty() : Boolean.TRUE.equals(gatewayExpansionState.get(stateKey));

                        favoriteChildren.add(new FolderAdapter.GatewayItem(favGateways, PARENT_ID_FAVORITE, true, gwExpanded));

                        if (gwExpanded) {
                            favoriteChildren.addAll(matchingSensors);
                        }
                    }
                }
            }
            try (Cursor favSensors = dbHelper.getFavoriteSensorsCursor()) {
                while (favSensors.moveToNext()) {
                    String gwName = favSensors.getString(favSensors.getColumnIndexOrThrow("gateway_name"));
                    if (sensorMatchesFilter(favSensors, gwName)) {
                        favoriteChildren.add(new FolderAdapter.SensorItem(favSensors, gwName, PARENT_ID_FAVORITE));
                    }
                }
            }

            if (!favoriteChildren.isEmpty() || !filtering) {
                boolean expanded = filtering || Boolean.TRUE.equals(folderExpansionState.get(PARENT_ID_FAVORITE));
                tempItems.add(new FolderAdapter.FolderItem(
                        PARENT_ID_FAVORITE,
                        getString(R.string.section_favorites),
                        expanded
                ));
                if (expanded) {
                    tempItems.addAll(favoriteChildren);
                }
            }

            // --- 2. Sekcje FOLDERÓW ---
            try (Cursor folders = dbHelper.getFoldersCursor()) {
                while (folders.moveToNext()) {
                    long folderId = folders.getLong(folders.getColumnIndexOrThrow("_id"));
                    String folderColorHex = folders.getString(folders.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_COLOR));

                    List<Object> folderChildren = new ArrayList<>();

                    try (Cursor gatewaysInFolder = dbHelper.getGatewaysForFolderCursor(folderId)) {
                        while (gatewaysInFolder.moveToNext()) {
                            long gatewayId = gatewaysInFolder.getLong(gatewaysInFolder.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
                            String gwName = gatewaysInFolder.getString(gatewaysInFolder.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));

                            boolean gwMatches = gatewayMatchesFilter(gatewaysInFolder);

                            List<FolderAdapter.SensorItem> matchingSensors = new ArrayList<>();
                            try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                                while (sensors.moveToNext()) {
                                    if (sensorMatchesFilter(sensors, gwName)) {
                                        matchingSensors.add(new FolderAdapter.SensorItem(sensors, null, PARENT_ID_GATEWAY, folderColorHex));
                                    }
                                }
                            }

                            if (gwMatches || !matchingSensors.isEmpty()) {
                                String stateKey = "folder_" + folderId + "_gw_" + gatewayId;
                                boolean gwExpanded = filtering ? !matchingSensors.isEmpty() : Boolean.TRUE.equals(gatewayExpansionState.get(stateKey));

                                folderChildren.add(new FolderAdapter.GatewayItem(gatewaysInFolder, folderId, true, gwExpanded, folderColorHex));

                                if (gwExpanded) {
                                    folderChildren.addAll(matchingSensors);
                                }
                            }
                        }
                    }

                    try (Cursor sensorsInFolder = dbHelper.getSensorsForFolderCursor(folderId)) {
                        while (sensorsInFolder.moveToNext()) {
                            String gwName = sensorsInFolder.getString(sensorsInFolder.getColumnIndexOrThrow("gateway_name"));
                            if (sensorMatchesFilter(sensorsInFolder, gwName)) {
                                folderChildren.add(new FolderAdapter.SensorItem(sensorsInFolder, gwName, folderId, folderColorHex));
                            }
                        }
                    }

                    if (!folderChildren.isEmpty() || !filtering) {
                        boolean expanded = filtering || Boolean.TRUE.equals(folderExpansionState.get(folderId));
                        FolderAdapter.FolderItem folderItem = new FolderAdapter.FolderItem(folders, expanded);
                        tempItems.add(folderItem);

                        if (expanded) {
                            tempItems.addAll(folderChildren);
                        }
                    }
                }
            }

            // --- 3. Sekcja "NIEZGRUPOWANE" ---
            List<Object> uncatChildren = new ArrayList<>();
            try (Cursor uncategorized = dbHelper.getUncategorizedGatewaysCursor()) {
                while (uncategorized.moveToNext()) {
                    long gatewayId = uncategorized.getLong(uncategorized.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
                    String gwName = uncategorized.getString(uncategorized.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));

                    boolean gwMatches = gatewayMatchesFilter(uncategorized);

                    List<FolderAdapter.SensorItem> matchingSensors = new ArrayList<>();
                    try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                        while (sensors.moveToNext()) {
                            if (sensorMatchesFilter(sensors, gwName)) {
                                matchingSensors.add(new FolderAdapter.SensorItem(sensors, null, PARENT_ID_GATEWAY));
                            }
                        }
                    }

                    if (gwMatches || !matchingSensors.isEmpty()) {
                        String stateKey = "uncat_gw_" + gatewayId;
                        boolean gwExpanded = filtering ? !matchingSensors.isEmpty() : Boolean.TRUE.equals(gatewayExpansionState.get(stateKey));

                        uncatChildren.add(new FolderAdapter.GatewayItem(uncategorized, UNCATEGORIZED_PARENT_ID, true, gwExpanded));
                        if (gwExpanded) {
                            uncatChildren.addAll(matchingSensors);
                        }
                    }
                }
            }

            if (!uncatChildren.isEmpty() || !filtering) {
                boolean expanded = filtering || Boolean.TRUE.equals(folderExpansionState.get(UNCATEGORIZED_PARENT_ID));
                tempItems.add(new FolderAdapter.FolderItem(
                        UNCATEGORIZED_PARENT_ID,
                        getString(R.string.section_uncategorized),
                        expanded
                ));
                if (expanded) {
                    tempItems.addAll(uncatChildren);
                }
            }

            runOnUiThread(() -> {
                displayItems.clear();
                displayItems.addAll(tempItems);
                adapter.notifyDataSetChanged();
            });

        }).start();
    }

    // --- POZOSTAŁE METODY (LOGIKA BIZNESOWA) ---

    private void forceSync(boolean notifyUser) {
        this.showSyncToast = notifyUser;
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        serviceIntent.putExtra("FORCE_SYNC_NOW", true);
        startService(serviceIntent);
    }

    private void forceSync() {
        forceSync(true);
    }

    private void forceBatteryCheck() {
        Intent serviceIntent = new Intent(this, VpsClientService.class);
        serviceIntent.putExtra("FORCE_BATTERY_CHECK_NOW", true);
        startService(serviceIntent);
        Toast.makeText(this, R.string.toast_battery_check_started, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFolderClicked(FolderAdapter.FolderItem folder) {
        boolean isExpanded = Boolean.TRUE.equals(folderExpansionState.get(folder.id));
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
        boolean isExpanded = Boolean.TRUE.equals(gatewayExpansionState.get(stateKey));
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

    @Override
    public void onFolderLongClicked(FolderAdapter.FolderItem folder, View view) {
        currentContextMenuItem = folder;
        openContextMenu(view);
    }

    @Override
    public void onGatewayLongClicked(FolderAdapter.GatewayItem gateway, View view) {
        currentContextMenuItem = gateway;
        openContextMenu(view);
    }

    @Override
    public void onSensorLongClicked(FolderAdapter.SensorItem sensor, View view) {
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

            menu.findItem(R.id.menu_edit_folder).setVisible(!isSpecialFolder);
            menu.findItem(R.id.menu_delete_folder).setVisible(!isSpecialFolder);

            MenuItem threshItem = menu.findItem(R.id.menu_toggle_folder_threshold_notifications);
            MenuItem battItem = menu.findItem(R.id.menu_toggle_folder_battery_notifications);

            if (isSpecialFolder) {
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

            boolean isFav = dbHelper.isFavoriteGateway(gateway.id);
            menu.findItem(R.id.menu_add_gateway_to_favorites).setVisible(!isFav);
            menu.findItem(R.id.menu_remove_gateway_from_favorites).setVisible(isFav);
            boolean showRemoveFromFolder = (gateway.parentFolderId >= 0);
            menu.findItem(R.id.menu_remove_gateway_from_folder).setVisible(showRemoveFromFolder);
            boolean showAddToFolder = (gateway.parentFolderId != PARENT_ID_FAVORITE);
            menu.findItem(R.id.menu_add_to_folder).setVisible(showAddToFolder);

            boolean isThreshFullyMuted = isGatewayFullyMuted(gateway.id, "thresh_sensor_");
            MenuItem threshItem = menu.findItem(R.id.menu_toggle_gateway_threshold_notifications);
            if (isThreshFullyMuted) {
                threshItem.setTitle(R.string.action_enable_thresholds);
            } else {
                threshItem.setTitle(R.string.action_disable_thresholds);
            }

            boolean isBattFullyMuted = isGatewayFullyMuted(gateway.id, "batt_sensor_");
            MenuItem battItem = menu.findItem(R.id.menu_toggle_gateway_battery_notifications);
            if (isBattFullyMuted) {
                battItem.setTitle(R.string.action_enable_battery);
            } else {
                battItem.setTitle(R.string.action_disable_battery);
            }

        } else if (currentContextMenuItem instanceof FolderAdapter.SensorItem) {
            inflater.inflate(R.menu.sensor_context_menu, menu);
            FolderAdapter.SensorItem sensor = (FolderAdapter.SensorItem) currentContextMenuItem;

            boolean isFav = dbHelper.isFavoriteSensor(sensor.id);
            menu.findItem(R.id.menu_add_sensor_to_favorites).setVisible(!isFav);
            menu.findItem(R.id.menu_remove_sensor_from_favorites).setVisible(isFav);
            boolean showRemoveFromFolder = (sensor.parentFolderId >= 0);
            menu.findItem(R.id.menu_remove_sensor_from_folder).setVisible(showRemoveFromFolder);
            boolean showAddToFolder = (sensor.parentFolderId != PARENT_ID_FAVORITE);
            menu.findItem(R.id.menu_add_sensor_to_folder).setVisible(showAddToFolder);

            boolean isThreshMuted = mutePrefs.getBoolean("thresh_sensor_" + sensor.id, false);
            menu.findItem(R.id.menu_disable_threshold_notifications).setVisible(!isThreshMuted);
            menu.findItem(R.id.menu_enable_threshold_notifications).setVisible(isThreshMuted);
            boolean isBattMuted = mutePrefs.getBoolean("batt_sensor_" + sensor.id, false);
            menu.findItem(R.id.menu_disable_battery_notifications).setVisible(!isBattMuted);
            menu.findItem(R.id.menu_enable_battery_notifications).setVisible(isBattMuted);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (currentContextMenuItem == null) return false;

        SharedPreferences.Editor editor = mutePrefs.edit();

        if (currentContextMenuItem instanceof FolderAdapter.FolderItem) {
            FolderAdapter.FolderItem folder = (FolderAdapter.FolderItem) currentContextMenuItem;
            int itemId = item.getItemId();

            if (itemId == R.id.menu_edit_folder) {
                showCreateFolderDialog(folder);
                return true;
            } else if (itemId == R.id.menu_delete_folder) {
                showDeleteFolderDialog(folder);
                return true;
            } else if (itemId == R.id.action_force_sync) {
                forceSync();
                return true;
            } else if (itemId == R.id.action_check_battery) {
                forceBatteryCheck();
                return true;
            } else if (itemId == R.id.menu_toggle_folder_threshold_notifications) {
                boolean isCurrentlyMuted = isFolderFullyMuted(folder.id, "thresh_sensor_");
                boolean newMuteState = !isCurrentlyMuted;
                setFolderMuteState(folder, "thresh_sensor_", newMuteState);
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
        } else if (currentContextMenuItem instanceof FolderAdapter.GatewayItem) {
            FolderAdapter.GatewayItem gateway = (FolderAdapter.GatewayItem) currentContextMenuItem;
            int itemId = item.getItemId();

            if (itemId == R.id.menu_rename_gateway) {
                showRenameDialog(gateway.id, gateway.name, gateway.description, true);
                return true;
            } else if (itemId == R.id.menu_add_to_folder) {
                showSelectFolderDialog(gateway);
                return true;
            } else if (itemId == R.id.menu_remove_gateway_from_folder) {
                removeGatewayFromFolderOnServer(gateway.id, gateway.parentFolderId);
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
            } else if (itemId == R.id.action_force_sync) {
                forceSync();
                return true;
            } else if (itemId == R.id.action_check_battery) {
                forceBatteryCheck();
                return true;
            } else if (itemId == R.id.menu_toggle_gateway_threshold_notifications) {
                boolean isCurrentlyMuted = isGatewayFullyMuted(gateway.id, "thresh_sensor_");
                boolean newMuteState = !isCurrentlyMuted;
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
        } else if (currentContextMenuItem instanceof FolderAdapter.SensorItem) {
            FolderAdapter.SensorItem sensor = (FolderAdapter.SensorItem) currentContextMenuItem;
            int itemId = item.getItemId();

            if (itemId == R.id.menu_rename_sensor) {
                showRenameDialog(sensor.id, sensor.name, getString(R.string.default_no_description), false);
                return true;
            } else if (itemId == R.id.menu_add_sensor_to_folder) {
                showSelectFolderDialogForSensor(sensor);
                return true;
            } else if (itemId == R.id.menu_remove_sensor_from_folder) {
                removeSensorFromFolderOnServer(sensor.id, sensor.parentFolderId);
                return true;
            } else if (itemId == R.id.menu_add_sensor_to_favorites) {
                toggleFavoriteSensor(sensor.id, true);
                return true;
            } else if (itemId == R.id.menu_remove_sensor_from_favorites) {
                toggleFavoriteSensor(sensor.id, false);
                return true;
            } else if (itemId == R.id.action_check_battery) {
                forceBatteryCheck();
                return true;
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

    private void showCreateFolderDialog(FolderAdapter.FolderItem folder) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_folder, null);

        final EditText editName = dialogView.findViewById(R.id.edit_folder_name);
        final EditText editColor = dialogView.findViewById(R.id.edit_folder_color);
        final View colorPreview = dialogView.findViewById(R.id.view_color_preview);

        boolean isEditMode = (folder != null);
        String title = isEditMode ? getString(R.string.dialog_edit_folder_title) : getString(R.string.dialog_create_folder_title);

        int initialColor = Color.GRAY;

        if (isEditMode) {
            editName.setText(folder.name);
            editColor.setText(folder.color);
            try {
                if (folder.color != null && !folder.color.isEmpty()) {
                    initialColor = Color.parseColor(folder.color);
                }
            } catch (IllegalArgumentException e) { Log.w(TAG, "Błąd koloru", e); }
        } else {
            editColor.setText("#");
        }

        colorPreview.setBackgroundColor(initialColor);
        final int[] currentColor = {initialColor};

        editColor.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String input = s.toString().trim();
                if (!input.startsWith("#")) input = "#" + input;
                try {
                    int parsedColor = Color.parseColor(input);
                    colorPreview.setBackgroundColor(parsedColor);
                    currentColor[0] = parsedColor;
                } catch (IllegalArgumentException e) { Log.w(TAG, "Błąd koloru", e); }
            }
        });

        colorPreview.setOnClickListener(v -> ColorPickerDialogBuilder
                .with(this)
                .setTitle(getString(R.string.dialog_folder_color_hint))
                .initialColor(currentColor[0])
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(12)
                .setPositiveButton(getString(R.string.dialog_save), (dialog, selectedColor, allColors) -> {
                    currentColor[0] = selectedColor;
                    String hexColor = String.format("#%06X", (0xFFFFFF & selectedColor));
                    editColor.setText(hexColor);
                    colorPreview.setBackgroundColor(selectedColor);
                })
                .setNegativeButton(getString(R.string.dialog_cancel_button), (dialog, which) -> {})
                .build()
                .show());

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                    String newName = editName.getText().toString().trim();
                    String newColor = editColor.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, R.string.error_folder_name_empty, Toast.LENGTH_SHORT).show();
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
                .setMessage(getString(R.string.dialog_delete_folder_msg, folder.name))
                .setIcon(R.drawable.ic_warning)
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> deleteFolderOnServer(folder.id))
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show();
    }

    private void showSelectFolderDialog(FolderAdapter.GatewayItem gateway) {
        List<FolderAdapter.FolderItem> folders = new ArrayList<>();
        List<String> folderNames = new ArrayList<>();
        try (Cursor c = dbHelper.getFoldersCursor()) {
            while (c.moveToNext()) {
                FolderAdapter.FolderItem item = new FolderAdapter.FolderItem(c, false);
                folders.add(item);
                folderNames.add(item.name);
            }
        }
        if (folders.isEmpty()) {
            Toast.makeText(this, R.string.toast_create_folder_first, Toast.LENGTH_SHORT).show();
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
        } catch (JSONException e) {
            // ignored
        }
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).put(body).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_update_failed, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, isGateway ? R.string.toast_gateway_updated : R.string.toast_sensor_updated, Toast.LENGTH_SHORT).show();
                        forceSync(false);
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
                .setPositiveButton(R.string.dialog_delete_confirm, (dialog, which) -> deleteGatewayOnServer(gatewayId))
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
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, R.string.toast_gateway_deleted, Toast.LENGTH_SHORT).show();
                        forceSync(false);
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
        } catch (JSONException e) {
            // ignored
        }
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
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(DataActivity.this, isEdit ? R.string.toast_folder_updated : R.string.toast_folder_created, Toast.LENGTH_SHORT).show();
                        forceSync(false);
                    } else {
                        Toast.makeText(DataActivity.this, R.string.toast_api_error, Toast.LENGTH_SHORT).show();
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
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { /* ... */ }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, R.string.toast_folder_deleted, Toast.LENGTH_SHORT).show();
                        forceSync(false);
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
        } catch (JSONException e) {
            // ignored
        }
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "addGatewayToFolderOnServer FAILURE", e);
                runOnUiThread(() -> Toast.makeText(DataActivity.this,
                        getString(R.string.error_network_prefix, e.getMessage()), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, getString(R.string.toast_added_to_folder, folderName), Toast.LENGTH_SHORT).show();
                        dbHelper.addGatewayToFolder(gatewayId, folderId);
                        loadDisplayListFromDb();
                        forceSync(false);
                    });
                } else {
                    String errorBody = getString(R.string.error_no_content);
                    try {
                        if (response.body() != null) {
                            errorBody = response.body().string();
                        }
                    } catch (IOException e) {
                        errorBody = getString(R.string.error_cannot_read, e.getMessage());
                    }
                    final String finalErrorBody = errorBody;
                    final int responseCode = response.code();
                    Log.e(TAG, "Błąd dodawania bramki. Kod: " + responseCode + ", Treść: " + finalErrorBody);

                    runOnUiThread(() -> Toast.makeText(DataActivity.this,
                            getString(R.string.error_code_format, responseCode), Toast.LENGTH_LONG).show());
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
            try {
                json.put("id", gatewayId);
            } catch (Exception e) {
                Log.e(TAG, "JSON Error", e);
            }
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        } else {
            request = new Request.Builder().url(url + "/" + gatewayId).addHeader("Authorization", "Bearer " + jwtToken).delete().build();
        }
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { Log.e(TAG, "Fav error", e); }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, add ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites, Toast.LENGTH_SHORT).show();
                        forceSync(false);
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
            try {
                json.put("id", sensorId);
            } catch (Exception e) {
                Log.e(TAG, "JSON Error", e);
            }
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        } else {
            request = new Request.Builder().url(url + "/" + sensorId).addHeader("Authorization", "Bearer " + jwtToken).delete().build();
        }
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { Log.e(TAG, "Fav error", e); }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, add ? R.string.toast_added_to_favorites : R.string.toast_removed_from_favorites, Toast.LENGTH_SHORT).show();
                        forceSync(false);
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
            while (c.moveToNext()) {
                FolderAdapter.FolderItem item = new FolderAdapter.FolderItem(c, false);
                folders.add(item);
                folderNames.add(item.name);
            }
        }
        if (folders.isEmpty()) {
            Toast.makeText(this, R.string.toast_create_folder_first, Toast.LENGTH_SHORT).show();
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
        } catch (JSONException e) {
            // ignored
        }
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + jwtToken).post(body).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
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
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, R.string.toast_removed_from_folder, Toast.LENGTH_SHORT).show();

                        dbHelper.removeGatewayFromFolder(gatewayId, folderId);
                        loadDisplayListFromDb();
                        forceSync(false);
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
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this, "Błąd sieci: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(DataActivity.this, R.string.toast_removed_from_folder, Toast.LENGTH_SHORT).show();
                        dbHelper.removeSensorFromFolder(sensorId, folderId);
                        loadDisplayListFromDb();
                        forceSync(false);
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

    private long lastUpdateTimestamp = 0;

    private void setupBroadcastReceiver() {
        dataUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long now = System.currentTimeMillis();

                if (now - lastUpdateTimestamp > 500) {
                    lastUpdateTimestamp = now;
                    loadDisplayListFromDb();
                }
            }
        };

        syncStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra("SYNC_SUCCESS", false);
                long now = System.currentTimeMillis();

                if (!showSyncToast) {
                    return;
                }
                showSyncToast = false;

                if (now - lastSyncToastTime < 3000) {
                    return;
                }

                if (success) {
                    Toast.makeText(DataActivity.this, R.string.sync_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(DataActivity.this, R.string.sync_error, Toast.LENGTH_SHORT).show();
                }

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

    private void setGatewayMuteState(FolderAdapter.GatewayItem gateway, String prefKeyPrefix, boolean mute) {
        Log.d(TAG, "Zmieniam stan wyciszenia dla bramki " + gateway.id + " na " + mute);
        SharedPreferences.Editor editor = mutePrefs.edit();
        try (Cursor c = dbHelper.getSensorsForGateway(gateway.id)) {
            while (c.moveToNext()) {
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                editor.putBoolean(prefKeyPrefix + sensorId, mute);
            }
        }
        editor.apply();
    }

    private boolean isGatewayFullyMuted(long gatewayId, String prefKeyPrefix) {
        boolean allMuted = true;
        boolean hasSensors = false;

        try (Cursor c = dbHelper.getSensorsForGateway(gatewayId)) {
            while (c.moveToNext()) {
                hasSensors = true;
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                boolean isMuted = mutePrefs.getBoolean(prefKeyPrefix + sensorId, false);

                if (!isMuted) {
                    allMuted = false;
                    break;
                }
            }
        }
        return !hasSensors || allMuted;
    }

    private boolean isFolderFullyMuted(long folderId, String prefKeyPrefix) {
        boolean allMuted = true;
        boolean hasSensors = false;

        try (Cursor c = dbHelper.getSensorsForFolderCursor(folderId)) {
            while (c.moveToNext()) {
                hasSensors = true;
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                if (!mutePrefs.getBoolean(prefKeyPrefix + sensorId, false)) {
                    allMuted = false;
                    break;
                }
            }
        }

        if (allMuted) {
            try (Cursor gateways = dbHelper.getGatewaysForFolderCursor(folderId)) {
                while (gateways.moveToNext()) {
                    long gatewayId = gateways.getLong(gateways.getColumnIndexOrThrow("_id"));
                    if (!isGatewayFullyMuted(gatewayId, prefKeyPrefix)) {
                        allMuted = false;
                        hasSensors = true;
                        break;
                    }
                }
            }
        }

        return !hasSensors || allMuted;
    }

    private void setFolderMuteState(FolderAdapter.FolderItem folder, String prefKeyPrefix, boolean mute) {
        Log.d(TAG, "Zmieniam stan wyciszenia dla folderu " + folder.id + " na " + mute);
        SharedPreferences.Editor editor = mutePrefs.edit();

        try (Cursor c = dbHelper.getSensorsForFolderCursor(folder.id)) {
            while (c.moveToNext()) {
                long sensorId = c.getLong(c.getColumnIndexOrThrow("_id"));
                editor.putBoolean(prefKeyPrefix + sensorId, mute);
            }
        }

        try (Cursor gateways = dbHelper.getGatewaysForFolderCursor(folder.id)) {
            while (gateways.moveToNext()) {
                long gatewayId = gateways.getLong(gateways.getColumnIndexOrThrow("_id"));
                try (Cursor sensors = dbHelper.getSensorsForGateway(gatewayId)) {
                    while (sensors.moveToNext()) {
                        long sensorId = sensors.getLong(sensors.getColumnIndexOrThrow("_id"));
                        editor.putBoolean(prefKeyPrefix + sensorId, mute);
                    }
                }
            }
        }
        editor.apply();
    }

    private void testConnection() {
        Toast.makeText(this, R.string.msg_testing_connection, Toast.LENGTH_SHORT).show();

        String jwtToken = authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
        if (jwtToken == null) {
            Toast.makeText(this, R.string.error_missing_token, Toast.LENGTH_SHORT).show();
            return;
        }

        Request request = new Request.Builder()
                .url(Constants.SENSOR_STATUS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DataActivity.this,
                        getString(R.string.error_network_prefix, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    Type listType = new TypeToken<List<SensorStatusErrorDto>>() {}.getType();

                    try {
                        List<SensorStatusErrorDto> errors = gson.fromJson(responseBody, listType);

                        if (errors == null || errors.isEmpty()) {
                            runOnUiThread(() -> Toast.makeText(DataActivity.this,
                                    R.string.msg_connection_ok,
                                    Toast.LENGTH_LONG).show());
                        } else {
                            String firstErrorMessage = errors.get(0).readableMessage;
                            runOnUiThread(() -> Toast.makeText(DataActivity.this,
                                    getString(R.string.msg_error_detected_prefix, firstErrorMessage),
                                    Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "testConnection: Błąd parsowania JSON: " + e.getMessage() + ", Odpowiedź: " + responseBody);
                        runOnUiThread(() -> Toast.makeText(DataActivity.this,
                                R.string.error_parsing_response,
                                Toast.LENGTH_LONG).show());
                    }

                } else {
                    Log.e(TAG, "testConnection: Błąd serwera. Kod: " + response.code() + ", Odpowiedź: " + responseBody);
                    runOnUiThread(() -> Toast.makeText(DataActivity.this,
                            getString(R.string.error_server_response_prefix, response.code()),
                            Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }
}