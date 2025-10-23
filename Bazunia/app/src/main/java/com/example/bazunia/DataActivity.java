package com.example.bazunia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button; // Zostaje
import android.widget.EditText;
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

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Aktywność odpowiedzialna za wyświetlanie danych czujników w formie listy rozwijanej
 * oraz nasłuchiwanie na aktualizacje danych z VpsClientService.
 */
public class DataActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private ExpandableListView expandableListView;
    private List<String> listBramek;
    private HashMap<String, List<SensorModel>> czujnikiMap;
    private DatabaseHelper dbHelper;
    private ExpandableListAdapter adapter;
    private BroadcastReceiver dataUpdateReceiver;

    // NOWE POLA STANU FILTRA
    private String currentFilterQuery = "";
    private String currentFilterMode = "SEARCH"; // Domyślny tryb to wyszukiwanie

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        new AppearanceManager(this).applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data); // Zakładam, że masz taki layout

        // Inicjalizacja komponentów
        expandableListView = findViewById(R.id.expandableListView);
        dbHelper = new DatabaseHelper(this);
        listBramek = new ArrayList<>();
        czujnikiMap = new HashMap<>();

        // NOWE PRZYCISKI (z pliku activity_data.xml)
// Używamy MaterialButton, aby poprawnie je znaleźć
        MaterialButton btnClearRefresh = findViewById(R.id.btnClearRefresh);
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnFilter = findViewById(R.id.btnFilter);
        MaterialButton btnSettings = findViewById(R.id.btnSettings); // Znajdź też ten

// PODŁĄCZENIE IKONY USTAWIEŃ
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // Adapter
        adapter = new ExpandableListAdapter(this, listBramek, czujnikiMap);
        expandableListView.setAdapter(adapter);

        // Ustawienie kliknięcia elementu dziecka (czujnika)
        expandableListView.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            String gatewayId = listBramek.get(groupPosition);
            SensorModel sensor = czujnikiMap.get(gatewayId).get(childPosition);

            Intent intent = new Intent(DataActivity.this, SensorDetailActivity.class);
            // Przekazanie danych czujnika do aktywności szczegółów
            intent.putExtra("GATEWAY_ID", sensor.gatewayId);
            intent.putExtra("SENSOR_ID", sensor.sensorId);
            intent.putExtra("SENSOR_TYPE", sensor.type);
            startActivity(intent);
            return true;
        });

        // USUNIĘTO: Listener dla editTextFilter.addTextChangedListener

        // NOWE LISTENERY PRZYCISKÓW
        btnBack.setOnClickListener(v -> {
            finish(); // Zamyka aktualną aktywność (wraca do MainActivity)
        });

        btnRefresh.setOnClickListener(v -> {
            // Wymuś ręczne odświeżenie danych
            loadSensorData(currentFilterQuery, currentFilterMode);
            Toast.makeText(this, "Ręcznie odświeżono dane", Toast.LENGTH_SHORT).show();
        });

        btnClearRefresh.setOnClickListener(v -> {
            // NOWY DIALOG POTWIERDZAJĄCY
            new AlertDialog.Builder(DataActivity.this)
                    .setTitle("Potwierdzenie")
                    .setMessage("Czy na pewno chcesz trwale usunąć całą historię czujników? Tej operacji nie można cofnąć.")
                    .setIcon(R.drawable.ic_delete) // Ikona kosza w tytule okna
                    .setPositiveButton("Tak, usuń", (dialog, which) -> {
                        // Logika, która była tu wcześniej, uruchomi się tylko po kliknięciu "Tak"
                        dbHelper.clearAllSensorData();
                        currentFilterQuery = ""; // Zresetuj filtry po wyczyszczeniu
                        currentFilterMode = "SEARCH";
                        loadSensorData(currentFilterQuery, currentFilterMode); // Załaduj ponownie (lista będzie pusta)
                        Toast.makeText(this, "Baza danych wyczyszczona", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Anuluj", null) // Nic nie rób, zamknij okno
                    .show();
        });

        btnFilter.setOnClickListener(v -> showFilterBottomSheet());


        // Inicjalizacja BroadcastReceivera
        setupBroadcastReceiver();

        // Pierwsze ładowanie danych
        loadSensorData(currentFilterQuery, currentFilterMode);

        // Żądanie uprawnień do powiadomień
        requestNotificationPermission();
    }

    /**
     * Konfiguruje odbiornik do odbierania sygnałów z serwisu o nowych danych.
     */
    private void setupBroadcastReceiver() {
        dataUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Załaduj dane ponownie, używając aktualnego filtra
                loadSensorData(currentFilterQuery, currentFilterMode);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rejestracja odbiornika wznawiając aktywność
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        // Ponowne załadowanie danych na wypadek, gdyby zostały zaktualizowane w innej aktywności
        loadSensorData(currentFilterQuery, currentFilterMode);
    }

    @Override
    protected void onPause() {
        // Wyrejestrowanie odbiornika pauzując aktywność
        LocalBroadcastManager.getInstance(this).unregisterReceiver(dataUpdateReceiver);
        super.onPause();
    }

    /**
     * Ładuje najnowsze dane czujników z bazy i aktualizuje widok ExpandableListView.
     * Wersja bez filtra (domyślnie ładuje wszystko).
     */
    private void loadSensorData() {
        loadSensorData(currentFilterQuery, currentFilterMode);
    }

    /**
     * Ładuje najnowsze dane czujników z bazy, filtrując je, i aktualizuje widok ExpandableListView.
     * @param filterQuery Tekst użyty do filtrowania
     * @param filterMode Tryb filtrowania ("SEARCH", "TYPE", "GATEWAY")
     */
    private void loadSensorData(String filterQuery, String filterMode) {
        // Użyj nowo dodanej metody, która obsługuje filtrowanie w DatabaseHelper.
        List<SensorModel> latestData = dbHelper.getLatestSensorDataByFilter(filterQuery, filterMode);

        // Czyszczenie i ponowne budowanie struktury danych dla adaptera
        listBramek.clear();
        czujnikiMap.clear();

        for (SensorModel sensor : latestData) {
            String gateway = sensor.gatewayId;
            if (!listBramek.contains(gateway)) {
                listBramek.add(gateway);
                czujnikiMap.put(gateway, new ArrayList<>());
            }
            czujnikiMap.get(gateway).add(sensor);
        }

        // Sortowanie (opcjonalnie)
        Collections.sort(listBramek);

        // Aktualizacja widoku
        adapter.notifyDataSetChanged();
    }

    /**
     * Wyświetla panel (Bottom Sheet) z zaawansowanymi opcjami filtrowania.
     */
    private void showFilterBottomSheet() {
        // Utwórz dialog na bazie naszego nowego layoutu
        final BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_filter, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Znajdź kontrolki wewnątrz panelu
        EditText editSearch = bottomSheetView.findViewById(R.id.editSearchFilter);
        Spinner spinnerType = bottomSheetView.findViewById(R.id.spinnerTypeFilter);
        Spinner spinnerGateway = bottomSheetView.findViewById(R.id.spinnerGatewayFilter);
        Button btnApply = bottomSheetView.findViewById(R.id.btnApplyFilter);
        Button btnClear = bottomSheetView.findViewById(R.id.btnClearFilter);

        // --- Logika wypełniania Spinnerów (Picklist) ---

        // Wypełnij Spinner typów
        List<String> sensorTypes = dbHelper.getUniqueSensorTypes();
        sensorTypes.add(0, "Wszystkie typy"); // Dodaj opcję "Wszystkie"
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sensorTypes);
        spinnerType.setAdapter(typeAdapter);

        // Wypełnij Spinner bramek
        List<String> gatewayIds = dbHelper.getUniqueGatewayIds();
        gatewayIds.add(0, "Wszystkie bramki"); // Dodaj opcję "Wszystkie"
        ArrayAdapter<String> gatewayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, gatewayIds);
        spinnerGateway.setAdapter(gatewayAdapter);

        // --- Ustawienie przycisku Wyczyść ---
        btnClear.setOnClickListener(v -> {
            currentFilterQuery = "";
            currentFilterMode = "SEARCH"; // Resetuj do domyślnego trybu
            loadSensorData(currentFilterQuery, currentFilterMode);
            bottomSheetDialog.dismiss();
        });

        // --- Ustawienie przycisku Zastosuj ---
        btnApply.setOnClickListener(v -> {
            // Logika wyboru filtra (złożona, ale daje priorytet)
            // 1. Sprawdź wyszukiwanie tekstowe
            String searchQuery = editSearch.getText().toString();
            // 2. Sprawdź spinner typów
            String typeQuery = (spinnerType.getSelectedItemPosition() > 0) ? spinnerType.getSelectedItem().toString() : "";
            // 3. Sprawdź spinner bramek
            String gatewayQuery = (spinnerGateway.getSelectedItemPosition() > 0) ? spinnerGateway.getSelectedItem().toString() : "";

            // Ustal priorytet: Wyszukiwarka > Typ > Bramka
            if (!searchQuery.isEmpty()) {
                currentFilterQuery = searchQuery;
                currentFilterMode = "SEARCH";
            } else if (!typeQuery.isEmpty()) {
                currentFilterQuery = typeQuery;
                currentFilterMode = "TYPE";
            } else if (!gatewayQuery.isEmpty()) {
                currentFilterQuery = gatewayQuery;
                currentFilterMode = "GATEWAY";
            } else {
                // Jeśli nic nie wybrano, ale kliknięto "Zastosuj" (traktuj jak wyczyszczenie)
                currentFilterQuery = "";
                currentFilterMode = "SEARCH";
            }

            // Załaduj dane z nowymi filtrami
            loadSensorData(currentFilterQuery, currentFilterMode);
            bottomSheetDialog.dismiss();
        });

        // Pokaż panel
        bottomSheetDialog.show();
    }


    // --- OBSŁUGA UPRAWNIEŃ (Bez zmian) ---

    /**
     * Wymagane na Android 13+ (API 33) do wyświetlania powiadomień.
     */
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

    /**
     * Obsługuje wynik żądania uprawnień.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Powiadomienia włączone.
                Toast.makeText(this, "Uprawnienia do powiadomień przyznane.", Toast.LENGTH_SHORT).show();
            } else {
                // Powiadomienia wyłączone.
                Toast.makeText(this, "Ostrzeżenie: Powiadomienia systemowe są wyłączone.", Toast.LENGTH_LONG).show();
            }
        }
    }
}