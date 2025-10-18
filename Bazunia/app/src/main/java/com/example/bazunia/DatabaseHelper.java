package com.example.bazunia;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sensor_data.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "sensors";
    private static final String COLUMN_ID = "id";
    public static final String COLUMN_GATE_ID = "gate_id";
    public static final String COLUMN_SENSOR_ID = "sensor_id";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_TIMESTAMP = "timestamp"; // Przechowujemy jako INTEGER (long w Java)

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_GATE_ID + " TEXT, " +
                COLUMN_SENSOR_ID + " TEXT, " +
                COLUMN_TYPE + " TEXT, " +
                COLUMN_VALUE + " TEXT, " +
                COLUMN_TIMESTAMP + " INTEGER" + // INTEGER przechowuje long w SQLite
                ")";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    /**
     * ZAPIS DANYCH: Wstawia nowy wiersz danych z czujnika.
     */
    public void addSensorData(SensorModel sensor) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_GATE_ID, sensor.gatewayId);
        values.put(COLUMN_SENSOR_ID, sensor.sensorId);
        values.put(COLUMN_TYPE, sensor.type);
        values.put(COLUMN_VALUE, sensor.value);
        values.put(COLUMN_TIMESTAMP, sensor.timestamp); // long jest bezpiecznie przechowywany jako INTEGER

        try {
            long result = db.insertOrThrow(TABLE_NAME, null, values);
            if (result == -1) {
                Log.e("DB_INSERT", "Błąd dodawania danych czujnika: " + sensor.sensorId);
            } else {
                Log.d("DB_INSERT", "Dodano dane czujnika ID: " + result);
            }
        } catch (SQLException e) {
            Log.e("DB_INSERT", "SQLException podczas dodawania danych: " + e.getMessage());
        } finally {
            // Nie zamykamy bazy tutaj, bo jest ona zarządzana przez SQLiteOpenHelper.
        }
    }


    /**
     * Pobiera najnowszy pojedynczy wpis dla konkretnego czujnika.
     * @return SensorModel lub null.
     */
    public SensorModel getLatestSensorData(String gateId, String sensorId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        SensorModel latestModel = null;

        try {
            String query = "SELECT * FROM " + TABLE_NAME +
                    " WHERE " + COLUMN_GATE_ID + " = ? AND " + COLUMN_SENSOR_ID + " = ? " +
                    " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                    " LIMIT 1";

            cursor = db.rawQuery(query, new String[]{gateId, sensorId});

            if (cursor != null && cursor.moveToFirst()) {
                String gatewayId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GATE_ID));
                String sId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE));
                String value = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VALUE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));

                latestModel = new SensorModel(gatewayId, sId, type, value, timestamp);
            }

        } catch (Exception e) {
            Log.e("DB_QUERY_ERROR", "Błąd pobierania najnowszych danych: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return latestModel;
    }

    /**
     * Pobiera unikalne, najnowsze dane ze wszystkich czujników.
     */
    public List<SensorModel> getLatestUniqueSensorData() {
        List<SensorModel> latestDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor uniqueSensorsCursor = null;

        try {
            // 1. Znajdź wszystkie unikalne pary gate_id i sensor_id
            String distinctQuery = "SELECT DISTINCT " + COLUMN_GATE_ID + ", " + COLUMN_SENSOR_ID + " FROM " + TABLE_NAME;
            uniqueSensorsCursor = db.rawQuery(distinctQuery, null);

            if (uniqueSensorsCursor != null && uniqueSensorsCursor.moveToFirst()) {
                do {
                    String gateId = uniqueSensorsCursor.getString(uniqueSensorsCursor.getColumnIndexOrThrow(COLUMN_GATE_ID));
                    String sensorId = uniqueSensorsCursor.getString(uniqueSensorsCursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID));

                    // 2. Dla każdej unikalnej pary, pobierz najnowszy wpis
                    SensorModel latestModel = getLatestSensorData(gateId, sensorId);
                    if (latestModel != null) {
                        latestDataList.add(latestModel);
                    }
                } while (uniqueSensorsCursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e("DB_FILTER_ERROR", "Błąd filtrowania danych: " + e.getMessage());
        } finally {
            if (uniqueSensorsCursor != null) {
                uniqueSensorsCursor.close();
            }
        }

        return latestDataList;
    }

    /**
     * NOWA METODA: Pobiera unikalne, najnowsze dane ze wszystkich czujników, filtrując je.
     * * @param filter Tekst użyty do filtrowania (np. po ID bramki lub ID czujnika).
     * @return Przefiltrowana lista najnowszych obiektów SensorModel.
     */
    public List<SensorModel> getLatestSensorDataByFilter(String filter) {
        // Pobierz wszystkie najnowsze dane (nie da się tego zrobić efektywnie w jednym zapytaniu SQLite)
        List<SensorModel> latestDataList = getLatestUniqueSensorData();
        if (filter == null || filter.trim().isEmpty()) {
            return latestDataList; // Jeśli filtr jest pusty, zwróć wszystko
        }

        List<SensorModel> filteredList = new ArrayList<>();
        String lowerCaseFilter = filter.toLowerCase();

        for (SensorModel sensor : latestDataList) {
            // Filtruj po ID bramki lub ID czujnika
            if (sensor.gatewayId.toLowerCase().contains(lowerCaseFilter) ||
                    sensor.sensorId.toLowerCase().contains(lowerCaseFilter)) {
                filteredList.add(sensor);
            }
        }
        return filteredList;
    }


    /**
     * Pobiera historię pomiarów dla konkretnego czujnika.
     */
    public Cursor getSensorHistory(String gateId, String sensorId, int limit) {
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COLUMN_GATE_ID + " = ? AND " + COLUMN_SENSOR_ID + " = ? " +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                " LIMIT " + limit;

        String[] selectionArgs = new String[]{gateId, sensorId};
        return db.rawQuery(query, selectionArgs);
    }
}