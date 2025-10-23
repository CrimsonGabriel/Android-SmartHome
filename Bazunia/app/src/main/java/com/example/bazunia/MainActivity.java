package com.example.bazunia;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import androidx.appcompat.app.AlertDialog;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private AppearanceManager appearanceManager;
    private ThresholdManager thresholdManager; // Potrzebny do sprawdzania progów

    private MaterialCardView cardAlerts;
    private TextView textAlertSummary;
    private MaterialCardView cardAllSensors;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        appearanceManager = new AppearanceManager(this);
        appearanceManager.applyAppearance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicjalizacja managerów
        dbHelper = new DatabaseHelper(this);
        thresholdManager = new ThresholdManager(this);

        // Znajdź widoki
        cardAlerts = findViewById(R.id.cardAlerts);
        textAlertSummary = findViewById(R.id.textAlertSummary);
        cardAllSensors = findViewById(R.id.cardAllSensors);

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // PODŁĄCZENIE IKONY WYLOGOWANIA
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Wylogowanie")
                    .setMessage("Czy na pewno chcesz się wylogować?")
                    .setIcon(R.drawable.ic_logout)
                    .setPositiveButton("Tak, wyloguj", (dialog, which) -> {
                        // Logika wylogowania uruchomi się tylko po kliknięciu "Tak"
                        Intent serviceIntent = new Intent(this, VpsClientService.class);
                        stopService(serviceIntent);

                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        });

        // Listener dla karty nawigacyjnej (zastępuje stary przycisk btnView)
        cardAllSensors.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, DataActivity.class);
            startActivity(intent);
        });


    }

    @Override
    protected void onResume() {
        super.onResume();
        // Załaduj dane do dashboardu za każdym razem, gdy wracamy na ten ekran
        loadDashboardData();
    }

    /**
     * Sprawdza bazę danych pod kątem aktywnych alertów i aktualizuje UI dashboardu.
     */
    private void loadDashboardData() {
        List<SensorModel> latestData = dbHelper.getLatestUniqueSensorData();
        int alertCount = 0;

        for (SensorModel sensor : latestData) {
            if (isSensorValueInAlertState(sensor)) {
                alertCount++;
            }
        }

        if (alertCount > 0) {
            textAlertSummary.setText(String.format(Locale.getDefault(),
                    "Uwaga! %d %s jest w stanie alarmu.",
                    alertCount,
                    getPolishSensorSuffix(alertCount)));
            cardAlerts.setVisibility(View.VISIBLE);
        } else {
            cardAlerts.setVisibility(View.GONE);
        }
    }

    /**
     * Logika pomocnicza (skopiowana z ExpandableListAdapter) do sprawdzania stanu alertu.
     */
    private boolean isSensorValueInAlertState(SensorModel sensor) {
        if ("door_contact".equalsIgnoreCase(sensor.type)) {
            return "1".equals(sensor.value);
        }

        boolean isHumidity = "humidity".equalsIgnoreCase(sensor.type);
        float defaultMin = isHumidity ? 5.0f : 18.0f;
        float defaultMax = isHumidity ? 30.0f : 22.0f;

        float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
        float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);

        try {
            float currentValue = Float.parseFloat(sensor.value);
            return currentValue < min || currentValue > max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Zwraca poprawną polską odmianę słowa "czujnik".
     */
    private String getPolishSensorSuffix(int count) {
        if (count == 1) return "czujnik";
        if (count >= 2 && count <= 4) return "czujniki";
        return "czujników";
    }
}