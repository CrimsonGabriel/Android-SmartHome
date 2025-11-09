package com.example.bazunia.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.example.bazunia.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sensor_data.db";
    private static final int DATABASE_VERSION = 2;

    // Tabela 1: Odczyty
    public static final String TABLE_READINGS = "readings";
    private static final String COLUMN_ID = "id";
    public static final String COLUMN_GATE_ID = "gate_id"; // ID Bramki (np. "100")
    public static final String COLUMN_SENSOR_ID = "sensor_id"; // ID Czujnika (np. "101")
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    // Tabela 2: Bramki (Metadane)
    public static final String TABLE_GATEWAYS = "gateways";
    public static final String G_COLUMN_ID = "id"; // Klucz główny (z serwera, np. 100)
    public static final String G_COLUMN_NAME = "name";
    public static final String G_COLUMN_STATUS = "status";
    public static final String G_COLUMN_FOLDER = "folder";
    public static final String G_COLUMN_DESCRIPTION = "description";
    public static final String G_COLUMN_LAST_SEEN = "last_seen";

    // Tabela 3: Czujniki (Metadane)
    public static final String TABLE_SENSORS = "sensors_metadata";
    public static final String S_COLUMN_ID = "id"; // Klucz główny (z serwera, np. 101)
    public static final String S_COLUMN_GATEWAY_ID = "gateway_id"; // Klucz obcy
    public static final String S_COLUMN_NAME = "name";
    public static final String S_COLUMN_TYPE = "type";
    public static final String S_COLUMN_DESCRIPTION = "description";
    public static final String S_COLUMN_BATTERY = "battery_level";

    private final Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE_READINGS = "CREATE TABLE " + TABLE_READINGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_GATE_ID + " TEXT, " +
                COLUMN_SENSOR_ID + " TEXT, " +
                COLUMN_TYPE + " TEXT, " +
                COLUMN_VALUE + " TEXT, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ")";
        db.execSQL(CREATE_TABLE_READINGS);
        createGatewayAndSensorTables(db);
    }

    private void createGatewayAndSensorTables(SQLiteDatabase db) {
        String CREATE_TABLE_GATEWAYS = "CREATE TABLE " + TABLE_GATEWAYS + " (" +
                G_COLUMN_ID + " INTEGER PRIMARY KEY, " +
                G_COLUMN_NAME + " TEXT, " +
                G_COLUMN_STATUS + " TEXT, " +
                G_COLUMN_FOLDER + " TEXT, " +
                G_COLUMN_DESCRIPTION + " TEXT, " +
                G_COLUMN_LAST_SEEN + " TEXT)";
        db.execSQL(CREATE_TABLE_GATEWAYS);

        String CREATE_TABLE_SENSORS = "CREATE TABLE " + TABLE_SENSORS + " (" +
                S_COLUMN_ID + " INTEGER PRIMARY KEY, " +
                S_COLUMN_GATEWAY_ID + " INTEGER, " +
                S_COLUMN_NAME + " TEXT, " +
                S_COLUMN_TYPE + " TEXT, " +
                S_COLUMN_DESCRIPTION + " TEXT, " +
                S_COLUMN_BATTERY + " INTEGER, " +
                "FOREIGN KEY(" + S_COLUMN_GATEWAY_ID + ") REFERENCES " + TABLE_GATEWAYS + "(" + G_COLUMN_ID + ") ON DELETE CASCADE)";
        db.execSQL(CREATE_TABLE_SENSORS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE sensors RENAME TO " + TABLE_READINGS);
            } catch (SQLException e) {
                onCreate(db);
                return;
            }
            createGatewayAndSensorTables(db);
        }
    }

    // --- METODY DLA ODCZYTÓW (READINGS) ---

    // Używane przez VpsClientService
    public void addSensorData(SensorModel sensor) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_GATE_ID, sensor.gatewayId);
        values.put(COLUMN_SENSOR_ID, sensor.sensorId);
        values.put(COLUMN_TYPE, sensor.type);
        values.put(COLUMN_VALUE, sensor.value);
        values.put(COLUMN_TIMESTAMP, sensor.timestamp);
        try {
            db.insertOrThrow(TABLE_READINGS, null, values);
        } catch (SQLException e) {
            Log.e("DB_INSERT", "SQLException podczas dodawania odczytu: " + e.getMessage());
        }
    }

    // Używane przez SensorDetailActivity
    public SensorModel getLatestSensorData(String gateId, String sensorId) {
        SQLiteDatabase db = this.getReadableDatabase();
        SensorModel latestModel = null;
        String query = "SELECT * FROM " + TABLE_READINGS +
                " WHERE " + COLUMN_GATE_ID + " = ? AND " + COLUMN_SENSOR_ID + " = ? " +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                " LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, new String[]{gateId, sensorId})) {
            if (cursor.moveToFirst()) {
                String gatewayId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GATE_ID));
                String sId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE));
                String value = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VALUE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                latestModel = new SensorModel(gatewayId, sId, type, value, timestamp);
            }
        } catch (Exception e) {
            Log.e("DB_QUERY_ERROR", String.format(Locale.getDefault(),
                    context.getString(R.string.log_error_db_query), e.getMessage()));
        }
        return latestModel;
    }

    // Używane przez MainActivity
    public List<SensorModel> getLatestUniqueSensorData() {
        List<SensorModel> latestDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String distinctQuery = "SELECT DISTINCT " + COLUMN_GATE_ID + ", " + COLUMN_SENSOR_ID + " FROM " + TABLE_READINGS;

        try (Cursor uniqueSensorsCursor = db.rawQuery(distinctQuery, null)) {
            if (uniqueSensorsCursor.moveToFirst()) {
                do {
                    String gateId = uniqueSensorsCursor.getString(uniqueSensorsCursor.getColumnIndexOrThrow(COLUMN_GATE_ID));
                    String sensorId = uniqueSensorsCursor.getString(uniqueSensorsCursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID));
                    SensorModel latestModel = getLatestSensorData(gateId, sensorId);
                    if (latestModel != null) {
                        latestDataList.add(latestModel);
                    }
                } while (uniqueSensorsCursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("DB_FILTER_ERROR", String.format(Locale.getDefault(),
                    context.getString(R.string.log_error_db_filter), e.getMessage()));
        }
        return latestDataList;
    }

    // Używane przez SensorDetailActivity
    public Cursor getSensorHistory(String gateId, String sensorId, int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_READINGS +
                " WHERE " + COLUMN_GATE_ID + " = ? AND " + COLUMN_SENSOR_ID + " = ? " +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                " LIMIT " + limit;
        return db.rawQuery(query, new String[]{gateId, sensorId});
    }

    // Używane przez VpsClientService
    public int cleanOldSensorData(int days) {
        if (days <= 0) return 0;
        SQLiteDatabase db = this.getWritableDatabase();
        long cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
        int deletedRows = 0;
        try {
            deletedRows = db.delete(TABLE_READINGS, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTime)});
            Log.d("DB_CLEAN", String.format(Locale.getDefault(),
                    context.getString(R.string.log_info_data_cleaned), deletedRows, days));
        } catch (SQLException e) {
            Log.e("DB_CLEAN", "Błąd automatycznego czyszczenia: " + e.getMessage());
        }
        return deletedRows;
    }

    // --- NOWE METODY DO ZARZĄDZANIA BRAMKAMI I CZUJNIKAMI (METADANE) ---

    // Używane przez VpsClientService
    public void syncGatewaysAndSensors(List<Gateway> gateways) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.beginTransaction();
            db.delete(TABLE_SENSORS, null, null);
            db.delete(TABLE_GATEWAYS, null, null);

            for (Gateway gateway : gateways) {
                ContentValues gwValues = new ContentValues();
                gwValues.put(G_COLUMN_ID, gateway.getId());
                gwValues.put(G_COLUMN_NAME, gateway.getName());
                gwValues.put(G_COLUMN_STATUS, gateway.getStatus());
                gwValues.put(G_COLUMN_FOLDER, gateway.getFolder());
                gwValues.put(G_COLUMN_DESCRIPTION, gateway.getDescription());
                gwValues.put(G_COLUMN_LAST_SEEN, gateway.getLastSeen());
                db.insert(TABLE_GATEWAYS, null, gwValues);

                if (gateway.getSensors() != null) {
                    for (Sensor sensor : gateway.getSensors()) {
                        ContentValues sValues = new ContentValues();
                        sValues.put(S_COLUMN_ID, sensor.getId());
                        sValues.put(S_COLUMN_GATEWAY_ID, gateway.getId());
                        sValues.put(S_COLUMN_NAME, sensor.getName());
                        sValues.put(S_COLUMN_TYPE, sensor.getType());
                        sValues.put(S_COLUMN_DESCRIPTION, sensor.getDescription());
                        sValues.put(S_COLUMN_BATTERY, sensor.getBatteryLevel());
                        db.insert(TABLE_SENSORS, null, sValues);
                    }
                }
            }
            db.setTransactionSuccessful();
            Log.d("DB_SYNC", "Pomyślnie zsynchronizowano " + gateways.size() + " bramek.");
        } catch (Exception e) {
            Log.e("DB_SYNC", "Błąd synchronizacji bramek: " + e.getMessage());
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    // Używane przez DataActivity
    public Cursor getAllGateways() {
        SQLiteDatabase db = this.getReadableDatabase();

        // <<< POPRAWKA: Musimy dodać alias "id AS _id" dla CursorAdaptera >>>
        String query = "SELECT " +
                G_COLUMN_ID + " AS _id, " + // Kluczowy alias
                G_COLUMN_NAME + ", " +
                G_COLUMN_STATUS + ", " +
                G_COLUMN_FOLDER + ", " +
                G_COLUMN_DESCRIPTION + ", " +
                G_COLUMN_LAST_SEEN +
                " FROM " + TABLE_GATEWAYS +
                " ORDER BY " + G_COLUMN_NAME + " ASC";

        return db.rawQuery(query, null);
    }

    // Używane przez DataActivity (GatewaySensorCursorAdapter)
    public Cursor getSensorsForGateway(long gatewayId) {
        SQLiteDatabase db = this.getReadableDatabase();

        // <<< POPRAWKA: Musimy dodać alias "id AS _id" dla CursorAdaptera >>>
        String query = "SELECT " +
                S_COLUMN_ID + " AS _id, " + // Kluczowy alias
                S_COLUMN_GATEWAY_ID + ", " +
                S_COLUMN_NAME + ", " +
                S_COLUMN_TYPE + ", " +
                S_COLUMN_DESCRIPTION + ", " +
                S_COLUMN_BATTERY +
                " FROM " + TABLE_SENSORS +
                " WHERE " + S_COLUMN_GATEWAY_ID + " = ? " +
                " ORDER BY " + S_COLUMN_NAME + " ASC";

        return db.rawQuery(query, new String[]{String.valueOf(gatewayId)});
    }
}