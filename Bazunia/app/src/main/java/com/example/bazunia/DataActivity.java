package com.example.bazunia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
    private EditText editTextFilter; // Deklaracja pola filtra
    private DatabaseHelper dbHelper;
    private ExpandableListAdapter adapter;
    private BroadcastReceiver dataUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data); // Zakładam, że masz taki layout

        // Inicjalizacja komponentów
        expandableListView = findViewById(R.id.expandableListView);
        editTextFilter = findViewById(R.id.editTextFilter); // Inicjalizacja widoku filtra
        dbHelper = new DatabaseHelper(this);
        listBramek = new ArrayList<>();
        czujnikiMap = new HashMap<>();

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

        // Ustawienie listenera dla pola filtra
        editTextFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Ignorujemy
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Ignorujemy
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Wywołaj ładowanie danych z użyciem aktualnego tekstu filtra
                loadSensorData(s.toString());
            }
        });

        // Inicjalizacja BroadcastReceivera
        setupBroadcastReceiver();

        // Pierwsze ładowanie danych
        loadSensorData();

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
                // Załaduj dane ponownie, używając aktualnego filtra z pola tekstowego
                loadSensorData(editTextFilter.getText().toString());
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rejestracja odbiornika wznawiając aktywność
        LocalBroadcastManager.getInstance(this).registerReceiver(dataUpdateReceiver, new IntentFilter(Constants.ACTION_DATA_UPDATED));
        // Ponowne załadowanie danych na wypadek, gdyby zostały zaktualizowane w innej aktywności (np. SensorDetailActivity)
        loadSensorData(editTextFilter.getText().toString());
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
        loadSensorData("");
    }

    /**
     * Ładuje najnowsze dane czujników z bazy, filtrując je, i aktualizuje widok ExpandableListView.
     * * @param filter Tekst użyty do filtrowania
     */
    private void loadSensorData(String filter) {
        // Użyj nowo dodanej metody, która obsługuje filtrowanie w DatabaseHelper.
        List<SensorModel> latestData = dbHelper.getLatestSensorDataByFilter(filter);

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


    // --- OBSŁUGA UPRAWNIEŃ (Dodano w poprzedniej odpowiedzi, aby uniknąć błędów kompilacji) ---

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