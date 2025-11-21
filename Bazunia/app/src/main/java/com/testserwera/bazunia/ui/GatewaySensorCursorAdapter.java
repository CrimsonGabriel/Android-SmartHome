package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorTreeAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.DatabaseHelper;

public class GatewaySensorCursorAdapter extends CursorTreeAdapter {

    private final LayoutInflater inflater;
    private final DatabaseHelper dbHelper;
    private final Context context;
    private final SharedPreferences mutePrefs;

    // <<< ⭐️⭐️⭐️ POPRAWKA KONSTRUKTORA ⭐️⭐️⭐️ >>>
    // Zmieniamy konstruktor, aby pasował do wywołania z DataActivity (3 argumenty)
    // i używał przekazanego dbHelper.
    public GatewaySensorCursorAdapter(Cursor cursor, Context context, DatabaseHelper dbHelper) {
        super(cursor, context);
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.dbHelper = dbHelper;
        this.mutePrefs = context.getSharedPreferences("NotificationMutePrefs", Context.MODE_PRIVATE);
    }

    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {
        // Ta logika jest już poprawna i teraz użyje dbHelper z pola klasy
        long gatewayId = groupCursor.getLong(groupCursor.getColumnIndexOrThrow("_id"));
        return dbHelper.getSensorsForGateway(gatewayId);
    }

    @Override
    protected View newGroupView(Context context, Cursor cursor, boolean isExpanded, ViewGroup parent) {
        return inflater.inflate(R.layout.list_group, parent, false);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        // Ta logika jest w porządku, zostawiamy bez zmian
        TextView textGroupName = view.findViewById(R.id.textGroup);
        TextView textGroupStatus = view.findViewById(R.id.textGroupStatus);
        ImageView iconGroup = view.findViewById(R.id.iconGroup);
        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
        String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_STATUS));
        textGroupName.setText(name);
        iconGroup.setImageResource(R.drawable.ic_gateway);
        if ("online".equalsIgnoreCase(status)) {
            textGroupStatus.setText(R.string.gateway_status_online);
            textGroupStatus.setTextColor(Color.parseColor("#FF009900")); // Zielony
        } else {
            textGroupStatus.setText(R.string.gateway_status_offline);
            textGroupStatus.setTextColor(Color.parseColor("#FF990000")); // Czerwony
        }
    }

    @Override
    protected View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
        return inflater.inflate(R.layout.list_item, parent, false);
    }

    // W pliku GatewaySensorCursorAdapter.java
    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {

        // 1. Znajdź nowe widoki
        TextView textSensorName = view.findViewById(R.id.sensor_name_text);
        TextView textBatteryLevel = view.findViewById(R.id.battery_text);
        ImageView iconBattery = view.findViewById(R.id.battery_icon);
        ImageView iconMuteThresh = view.findViewById(R.id.icon_mute_threshold);
        ImageView iconMuteBatt = view.findViewById(R.id.icon_mute_battery);

        // 2. Pobierz dane z kursora
        long sensorId = cursor.getLong(cursor.getColumnIndexOrThrow("_id")); // Potrzebne ID do sprawdzenia prefs
        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_NAME));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_TYPE));
        int batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_BATTERY));
        String keyword = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_KEYWORD));

        // 3. Ustaw nazwę sensora i ikonę (logika z getIcon jest OK)
        textSensorName.setText(name);
        textSensorName.setCompoundDrawablesWithIntrinsicBounds(getIcon(type, keyword, null), 0, 0, 0);

        // 4. OBSŁUGA IKON WYCISZENIA (Logic Check)
        // Sprawdzamy w prefs czy dany sensor jest wyciszony
        boolean isThreshMuted = mutePrefs.getBoolean("thresh_sensor_" + sensorId, false);
        boolean isBattMuted = mutePrefs.getBoolean("batt_sensor_" + sensorId, false);

        // Ustawiamy widoczność
        iconMuteThresh.setVisibility(isThreshMuted ? View.VISIBLE : View.GONE);
        iconMuteBatt.setVisibility(isBattMuted ? View.VISIBLE : View.GONE);

        // 5. Ustaw stan baterii (ikona + tekst)
        if (batteryLevel > 0) {
            textBatteryLevel.setText(batteryLevel + "%");
            textBatteryLevel.setVisibility(View.VISIBLE);
            iconBattery.setVisibility(View.VISIBLE);

            // Ustaw odpowiednią ikonę (musisz dodać te drawable)
            // NOWA LOGIKA
            if (batteryLevel > 75) {
                iconBattery.setImageResource(R.drawable.ic_battery_full);
            } else if (batteryLevel > 50) {
                iconBattery.setImageResource(R.drawable.ic_battery_good);
            } else if (batteryLevel > 20) { // Zmieniony próg z 25 na 20
                iconBattery.setImageResource(R.drawable.ic_battery_medium);
            } else if (batteryLevel > 1) { // Nowy próg dla "low"
                iconBattery.setImageResource(R.drawable.ic_battery_low);
            } else {
                // Poziom 1% lub 0
                iconBattery.setImageResource(R.drawable.ic_battery_empty); // <-- NOWA IKONA
            }

            // Zmiana koloru tekstu NAZWY (tak jak miałeś)
            if (batteryLevel <= 20) {
                textSensorName.setTextColor(Color.parseColor("#FF990000")); // Czerwony
            } else {
                textSensorName.setTextColor(getDefaultTextColor()); // Użyj metody pomocniczej
            }

        } else {
            // Jeśli bateria = 0 lub null, ukryj elementy baterii
            textBatteryLevel.setVisibility(View.GONE);
            iconBattery.setVisibility(View.GONE);
            textSensorName.setTextColor(getDefaultTextColor()); // Domyślny kolor
        }
    }

    // Metoda pomocnicza, którą miałeś (trochę ją uprościłem)
    private int getDefaultTextColor() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return this.context.getColor(android.R.color.tab_indicator_text);
        } else {
            //noinspection deprecation
            return this.context.getResources().getColor(android.R.color.tab_indicator_text);
        }
    }

    private int getIcon(String type, String keyword, String value) {

        // 1. Sprawdzanie po KEYWORD (najwyższy priorytet)
        if (keyword != null) {
            switch (keyword.toLowerCase()) {
                case "tv":
                    return R.drawable.ic_tv;
                case "washer":
                    return R.drawable.ic_washer;
                case "fridge":
                    return R.drawable.ic_fridge;
                case "oven":
                    return R.drawable.ic_oven;
                case "socket":
                    return R.drawable.ic_socket;
                case "dishwasher":
                    return R.drawable.ic_dishwasher;
                case "hood":
                    return R.drawable.ic_hood;
                // Możesz tu dodać więcej
            }
        }

        // 2. Sprawdzanie po TYPE (jeśli nie ma keywordu)
        if (type == null) return R.drawable.ic_sensor;
        switch (type.toLowerCase()) {
            case "button_press":
                return R.drawable.ic_button;
            case "temperature":
                return R.drawable.ic_temp;
            case "humidity":
                return R.drawable.ic_humidity;
            case "power":
                return R.drawable.ic_power; // Ogólna ikona dla 'power', jeśli nie 'tv' itd.
            case "motion":
                return R.drawable.ic_motion;
            case "light":
                return R.drawable.ic_light;
            case "smoke":
                return R.drawable.ic_smoke;
            case "flow":
                return R.drawable.ic_flow;
            case "sunlight":
                return R.drawable.ic_sunlight;
            case "level":
                return R.drawable.ic_level;
            case "valve":
                return R.drawable.ic_valve;

            // 3. Logika dla stanu OTWARTE/ZAMKNIĘTE
            case "contact":
                if ("1".equals(value)) {
                    return R.drawable.ic_open;
                } else {
                    return R.drawable.ic_closed;
                }

            default:
                return R.drawable.ic_sensor;
        }
    }
}