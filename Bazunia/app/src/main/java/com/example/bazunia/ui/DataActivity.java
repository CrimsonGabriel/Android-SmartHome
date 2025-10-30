package com.example.bazunia.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button; // Zostaje
import android.widget.EditText;
import android.content.Context;
import com.example.bazunia.utils.AppearanceManager;
import com.example.bazunia.utils.Constants;
import com.example.bazunia.data.DatabaseHelper;
import com.example.bazunia.R;
import com.example.bazunia.data.SensorModel;
import com.google.android.material.button.MaterialButton; // DODAJ TEN IMPORT
import android.widget.ExpandableListView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.example.bazunia.utils.LocaleManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class DataActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private ExpandableListView expandableListView;
    private List<String> listBramek;
    private HashMap<String, List<SensorModel>> czujnikiMap;
    private DatabaseHelper dbHelper;
    private ExpandableListAdapter adapter;
    private BroadcastReceiver dataUpdateReceiver;

    private AppearanceManager appearanceManager;
    private String currentTextScale;
    private String currentButtonScale;
    private String currentFilterQuery = "";
    private String currentFilterMode = "SEARCH";

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

        expandableListView = findViewById(R.id.expandableListView);
        dbHelper = new DatabaseHelper(this);
        listBramek = new ArrayList<>();
        czujnikiMap = new HashMap<>();

        MaterialButton btnClearRefresh = findViewById(R.id.btnClearRefresh);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnFilter = findViewById(R.id.btnFilter);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);

        appearanceManager.applyIconScale(btnClearRefresh);
        appearanceManager.applyIconScale(btnRefresh);
        appearanceManager.applyIconScale(btnBack);
        appearanceManager.applyIconScale(btnFilter);
        appearanceManager.applyIconScale(btnSettings);

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        adapter = new ExpandableListAdapter(this, listBramek, czujnikiMap);
        expandableListView.setAdapter(adapter);

        expandableListView.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            String gatewayId = listBramek.get(groupPosition);
            List<SensorModel> sensors = czujnikiMap.get(gatewayId);
            if (sensors != null) {
                SensorModel sensor = sensors.get(childPosition);

                Intent intent = new Intent(DataActivity.this, SensorDetailActivity.class);
                intent.putExtra(getString(R.string.intent_extra_gateway_id), sensor.gatewayId);
                intent.putExtra(getString(R.string.intent_extra_sensor_id), sensor.sensorId);
                intent.putExtra(getString(R.string.intent_extra_sensor_type), sensor.type);
                startActivity(intent);
            }
            return true;
        });

        btnBack.setOnClickListener(v -> finish());

        btnRefresh.setOnClickListener(v -> {
            loadSensorData(currentFilterQuery, currentFilterMode);
            Toast.makeText(this, getString(R.string.data_refreshed_manually), Toast.LENGTH_SHORT).show();
        });

        btnClearRefresh.setOnClickListener(v -> new AlertDialog.Builder(DataActivity.this)
                .setTitle(DataActivity.this.getString(R.string.clear_data_confirmation_title))
                .setMessage(DataActivity.this.getString(R.string.clear_data_confirmation_message))
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton(DataActivity.this.getString(R.string.clear_data_positive_button), (dialog, which) -> {
                    dbHelper.clearAllSensorData();
                    currentFilterQuery = "";
                    currentFilterMode = DataActivity.this.getString(R.string.filter_mode_search);
                    DataActivity.this.loadSensorData(currentFilterQuery, currentFilterMode);
                    Toast.makeText(DataActivity.this, DataActivity.this.getString(R.string.database_cleared), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(DataActivity.this.getString(R.string.dialog_cancel_button), null)
                .show());

        btnFilter.setOnClickListener(v -> showFilterBottomSheet());

        setupBroadcastReceiver();

        loadSensorData(currentFilterQuery, currentFilterMode);

        requestNotificationPermission();
    }

    private void setupBroadcastReceiver() {
        dataUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadSensorData(currentFilterQuery, currentFilterMode);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (LocaleManager.languageChanged) {
            LocaleManager.languageChanged = false; // Resetowanie flagi
            recreate(); // Wymuszenie ponownego stworzenia Aktywności
            return; // Ważne, aby zakończyć, jeśli wymuszono recreate
        }

        if (appearanceManager != null && (!currentTextScale.equals(appearanceManager.getTextScale()) ||
                !currentButtonScale.equals(appearanceManager.getButtonScale()))) {
            recreate();
            return;
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        loadSensorData(currentFilterQuery, currentFilterMode);
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        super.onPause();
    }

    private void loadSensorData(String filterQuery, String filterMode) {
        List<SensorModel> latestData = dbHelper.getLatestSensorDataByFilter(filterQuery, filterMode);

        listBramek.clear();
        czujnikiMap.clear();

        for (SensorModel sensor : latestData) {
            String gateway = sensor.gatewayId;
            if (!czujnikiMap.containsKey(gateway)) {
                listBramek.add(gateway);
                czujnikiMap.put(gateway, new ArrayList<>());
            }
            List<SensorModel> sensors = czujnikiMap.get(gateway);
            if (sensors != null) {
                sensors.add(sensor);
            }
        }

        Collections.sort(listBramek);

        adapter.notifyDataSetChanged();
    }

    private void showFilterBottomSheet() {
        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_filter, (ViewGroup) expandableListView.getParent(), false);
        bottomSheetDialog.setContentView(bottomSheetView);

        EditText editSearch = bottomSheetView.findViewById(R.id.editSearchFilter);
        Spinner spinnerType = bottomSheetView.findViewById(R.id.spinnerTypeFilter);
        Spinner spinnerGateway = bottomSheetView.findViewById(R.id.spinnerGatewayFilter);
        Button btnApply = bottomSheetView.findViewById(R.id.btnApplyFilter);
        Button btnClear = bottomSheetView.findViewById(R.id.btnClearFilter);

        List<String> sensorTypes = dbHelper.getUniqueSensorTypes();
        sensorTypes.add(0, getString(R.string.all_types));
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sensorTypes);
        spinnerType.setAdapter(typeAdapter);

        List<String> gatewayIds = dbHelper.getUniqueGatewayIds();
        gatewayIds.add(0, getString(R.string.all_gateways));
        ArrayAdapter<String> gatewayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, gatewayIds);
        spinnerGateway.setAdapter(gatewayAdapter);

        btnClear.setOnClickListener(v -> {
            currentFilterQuery = "";
            currentFilterMode = getString(R.string.filter_mode_search);
            loadSensorData(currentFilterQuery, currentFilterMode);
            bottomSheetDialog.dismiss();
        });

        btnApply.setOnClickListener(v -> {
            String searchQuery = editSearch.getText().toString();
            String typeQuery = (spinnerType.getSelectedItemPosition() > 0) ? spinnerType.getSelectedItem().toString() : "";
            String gatewayQuery = (spinnerGateway.getSelectedItemPosition() > 0) ? spinnerGateway.getSelectedItem().toString() : "";

            if (!searchQuery.isEmpty()) {
                currentFilterQuery = searchQuery;
                currentFilterMode = getString(R.string.filter_mode_search);
            } else if (!typeQuery.isEmpty()) {
                currentFilterQuery = typeQuery;
                currentFilterMode = getString(R.string.filter_mode_type);
            } else if (!gatewayQuery.isEmpty()) {
                currentFilterQuery = gatewayQuery;
                currentFilterMode = getString(R.string.filter_mode_gateway);
            } else {
                currentFilterQuery = "";
                currentFilterMode = getString(R.string.filter_mode_search);
            }

            loadSensorData(currentFilterQuery, currentFilterMode);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show();
            }
        }
    }
}
