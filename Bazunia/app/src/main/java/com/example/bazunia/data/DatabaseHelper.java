package com.example.bazunia.data;

import android.content.ContentValues;
import android.content.Context; // DODANY IMPORT
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import com.example.bazunia.R; // DODANY IMPORT
import java.util.ArrayList;
import java.util.List;
import java.util.Locale; // DODANY IMPORT

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sensor_data.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "sensors";
    private static final String COLUMN_ID = "id";
    public static final String COLUMN_GATE_ID = "gate_id";
    public static final String COLUMN_SENSOR_ID = "sensor_id";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    private final Context context; // ⭐️ ZMIENNA PRZECHOWUJĄCA KONTEKST

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context; // ⭐️ INICJALIZACJA KONTEKSTU
    }



    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_GATE_ID + " TEXT, " +
                COLUMN_SENSOR_ID + " TEXT, " +
                COLUMN_TYPE + " TEXT, " +
                COLUMN_VALUE + " TEXT, " +
                COLUMN_TIMESTAMP + " INTEGER" +
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
        values.put(COLUMN_TIMESTAMP, sensor.timestamp);

        try {
            long result = db.insertOrThrow(TABLE_NAME, null, values);
            if (result == -1) {
                Log.e("DB_INSERT", "Błąd dodawania danych czujnika: " + sensor.sensorId);
            } else {
                Log.d("DB_INSERT", "Dodano dane czujnika ID: " + result);
            }
        } catch (SQLException e) {
            Log.e("DB_INSERT", "SQLException podczas dodawania danych: " + e.getMessage());
        }
    }


    /**
     * Pobiera najnowszy pojedynczy wpis dla konkretnego czujnika.
     * @return SensorModel lub null.
     */
    public SensorModel getLatestSensorData(String gateId, String sensorId) {
        SQLiteDatabase db = this.getReadableDatabase();
        SensorModel latestModel = null;
        String query = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COLUMN_GATE_ID + " = ? AND " + COLUMN_SENSOR_ID + " = ? " +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                " LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, new String[]{gateId, sensorId})) {
            if (cursor.moveToFirst()) {
                // POPRAWKA: Użycie getColumnIndexOrThrow
                String gatewayId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GATE_ID));
                String sId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID));
                String type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE));
                String value = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VALUE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));

                latestModel = new SensorModel(gatewayId, sId, type, value, timestamp);
            }
        } catch (Exception e) {
            // ⭐️ POPRAWKA: Użycie zasobu string z kontekstu
            Log.e("DB_QUERY_ERROR", String.format(Locale.getDefault(),
                    context.getString(R.string.log_error_db_query), e.getMessage()));
        }
        return latestModel;
    }

    /**
     * Pobiera unikalne, najnowsze dane ze wszystkich czujników.
     */
    public List<SensorModel> getLatestUniqueSensorData() {
        List<SensorModel> latestDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String distinctQuery = "SELECT DISTINCT " + COLUMN_GATE_ID + ", " + COLUMN_SENSOR_ID + " FROM " + TABLE_NAME;

        try (Cursor uniqueSensorsCursor = db.rawQuery(distinctQuery, null)) {
            if (uniqueSensorsCursor.moveToFirst()) {
                do {
                    // POPRAWKA: Użycie getColumnIndexOrThrow
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
            // ⭐️ POPRAWKA: Użycie zasobu string z kontekstu
            Log.e("DB_FILTER_ERROR", String.format(Locale.getDefault(),
                    context.getString(R.string.log_error_db_filter), e.getMessage()));
        }

        return latestDataList;
    }

    /**
     * NOWA METODA: Pobiera unikalne, najnowsze dane, filtrując je według różnych kryteriów.
     * @param filterQuery Wartość do filtrowania (np. "GW-01" lub "temp" lub "salon")
     * @param filterMode Tryb filtrowania ("TYPE", "GATEWAY", "SEARCH")
     * @return Przefiltrowana lista najnowszych obiektów SensorModel.
     */
    public List<SensorModel> getLatestSensorDataByFilter(String filterQuery, String filterMode) {
        // Pobierz wszystkie najnowsze dane (tak jak wcześniej)
        List<SensorModel> latestDataList = getLatestUniqueSensorData();

        if (filterQuery == null || filterQuery.trim().isEmpty()) {
            return latestDataList; // Jeśli filtr jest pusty, zwróć wszystko
        }

        List<SensorModel> filteredList = new ArrayList<>();
        String lowerCaseQuery = filterQuery.toLowerCase();

        for (SensorModel sensor : latestDataList) {
            boolean matches = false;

            // Użyj "SEARCH" jako domyślnego trybu, jeśli tryb jest nieznany
            String mode = (filterMode != null) ? filterMode : "SEARCH";

            switch (mode) {
                case "TYPE":
                    // Dopasowanie ścisłe (z listy)
                    if (sensor.type.equalsIgnoreCase(filterQuery)) {
                        matches = true;
                    }
                    break;
                case "GATEWAY":
                    // Dopasowanie ścisłe (z listy)
                    if (sensor.gatewayId.equalsIgnoreCase(filterQuery)) {
                        matches = true;
                    }
                    break;
                case "SEARCH":
                default:
                    // Dopasowanie luźne (wyszukiwanie tekstowe)
                    if (sensor.gatewayId.toLowerCase().contains(lowerCaseQuery) ||
                            sensor.sensorId.toLowerCase().contains(lowerCaseQuery)) {
                        matches = true;
                    }
                    break;
            }

            if (matches) {
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

    public void clearAllSensorData() {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.delete(TABLE_NAME, null, null);
            Log.d("DB_CLEAR", "Wyczyszczono tabele: " + TABLE_NAME);
        } catch (SQLException e) {
            Log.e("DB_CLEAR", "Błąd czyszczenia tabeli: " + e.getMessage());
        }
    }

    /**
     * Pobiera listę unikalnych typów czujników z bazy.
     */
    public List<String> getUniqueSensorTypes() {
        List<String> types = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT DISTINCT " + COLUMN_TYPE + " FROM " + TABLE_NAME + " ORDER BY " + COLUMN_TYPE;
        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    types.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // ⭐️ POPRAWKA: Użycie zasobu string z kontekstu
            Log.e("DB_QUERY", String.format(Locale.getDefault(),
                    context.getString(R.string.log_error_db_types), e.getMessage()));
        }
        return types;
    }

    /**
     * Pobiera listę unikalnych ID bramek z bazy.
     */
    public List<String> getUniqueGatewayIds() {
        List<String> gateways = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT DISTINCT " + COLUMN_GATE_ID + " FROM " + TABLE_NAME + " ORDER BY " + COLUMN_GATE_ID;
        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    gateways.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // ⭐️ POPRAWKA: Użycie zasobu string z kontekstu
            Log.e("DB_QUERY", String.format(Locale.getDefault(),
                    context.getString(R.string.log_error_db_gateways), e.getMessage()));
        }
        return gateways;
    }

}