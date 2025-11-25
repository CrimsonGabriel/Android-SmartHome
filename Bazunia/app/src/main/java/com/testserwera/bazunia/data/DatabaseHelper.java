package com.testserwera.bazunia.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sensor_data.db";
    private static final int DATABASE_VERSION = 2;

    // Tabela 1: Odczyty
    public static final String TABLE_READINGS = "readings";
    private static final String COLUMN_ID = "id";
    public static final String COLUMN_GATE_ID = "gate_id";
    public static final String COLUMN_SENSOR_ID = "sensor_id"; // Nazwa kolumny w readings
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_VALUE = "value";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    // Tabela 2: Bramki (Metadane)
    public static final String TABLE_GATEWAYS = "gateways";
    public static final String G_COLUMN_ID = "id";
    public static final String G_COLUMN_NAME = "name";
    public static final String G_COLUMN_STATUS = "status";
    public static final String G_COLUMN_FOLDER = "folder";
    public static final String G_COLUMN_DESCRIPTION = "description";
    public static final String G_COLUMN_LAST_SEEN = "last_seen";

    // Tabela 3: Czujniki (Metadane)
    public static final String TABLE_SENSORS = "sensors_metadata";
    public static final String S_COLUMN_ID = "id";
    public static final String S_COLUMN_GATEWAY_ID = "gateway_id";
    public static final String S_COLUMN_NAME = "name";
    public static final String S_COLUMN_TYPE = "type";
    public static final String S_COLUMN_DESCRIPTION = "description";
    public static final String S_COLUMN_BATTERY = "battery_level";
    public static final String S_COLUMN_KEYWORD = "keyword";
    public static final String S_COLUMN_INTERVAL = "interval_seconds";
    public static final String S_COLUMN_REPORTING_ENABLED = "reporting_enabled";

    public static final String S_COLUMN_VALUE = "value"; // Alias do wynikow zlaczen

    // Tabele v3 (Foldery i Ulubione)
    public static final String TABLE_FOLDERS = "folders";
    public static final String F_COLUMN_ID = "id";
    public static final String F_COLUMN_NAME = "name";
    public static final String F_COLUMN_COLOR = "color";
    public static final String TABLE_FOLDER_GATEWAYS = "folder_gateways";
    public static final String FG_COLUMN_FOLDER_ID = "folder_id";
    public static final String FG_COLUMN_GATEWAY_ID = "gateway_id";
    public static final String TABLE_FAVORITE_GATEWAYS = "favorite_gateways";
    public static final String FAV_G_GATEWAY_ID = "gateway_id";
    public static final String TABLE_FAVORITE_SENSORS = "favorite_sensors";
    public static final String FAV_S_SENSOR_ID = "sensor_id";
    public static final String TABLE_FOLDER_SENSORS = "folder_sensors";
    public static final String FS_COLUMN_FOLDER_ID = "folder_id";
    public static final String FS_COLUMN_SENSOR_ID = "sensor_id";

    // USUNIĘTO: private final Context context; - nie jest już potrzebne jako pole klasy

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        // USUNIĘTO: this.context = context;
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
        createFolderAndFavoriteTables(db);
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
                S_COLUMN_KEYWORD + " TEXT, " +
                S_COLUMN_INTERVAL + " INTEGER, " +
                S_COLUMN_REPORTING_ENABLED + " INTEGER NOT NULL DEFAULT 1, " +
                "FOREIGN KEY(" + S_COLUMN_GATEWAY_ID + ") REFERENCES " + TABLE_GATEWAYS + "(" + G_COLUMN_ID + ") ON DELETE CASCADE)";
        db.execSQL(CREATE_TABLE_SENSORS);
    }

    private void createFolderAndFavoriteTables(SQLiteDatabase db) {
        String CREATE_TABLE_FOLDERS = "CREATE TABLE " + TABLE_FOLDERS + " (" +
                F_COLUMN_ID + " INTEGER PRIMARY KEY, " +
                F_COLUMN_NAME + " TEXT, " +
                F_COLUMN_COLOR + " TEXT)";
        db.execSQL(CREATE_TABLE_FOLDERS);

        String CREATE_TABLE_FOLDER_GATEWAYS = "CREATE TABLE " + TABLE_FOLDER_GATEWAYS + " (" +
                FG_COLUMN_FOLDER_ID + " INTEGER, " +
                FG_COLUMN_GATEWAY_ID + " INTEGER, " +
                "PRIMARY KEY(" + FG_COLUMN_FOLDER_ID + ", " + FG_COLUMN_GATEWAY_ID + "))";
        db.execSQL(CREATE_TABLE_FOLDER_GATEWAYS);

        String CREATE_TABLE_FAVORITE_GATEWAYS = "CREATE TABLE " + TABLE_FAVORITE_GATEWAYS + " (" +
                FAV_G_GATEWAY_ID + " INTEGER PRIMARY KEY)";
        db.execSQL(CREATE_TABLE_FAVORITE_GATEWAYS);

        String CREATE_TABLE_FAVORITE_SENSORS = "CREATE TABLE " + TABLE_FAVORITE_SENSORS + " (" +
                FAV_S_SENSOR_ID + " INTEGER PRIMARY KEY)";
        db.execSQL(CREATE_TABLE_FAVORITE_SENSORS);

        String CREATE_TABLE_FOLDER_SENSORS = "CREATE TABLE " + TABLE_FOLDER_SENSORS + " (" +
                FS_COLUMN_FOLDER_ID + " INTEGER, " +
                FS_COLUMN_SENSOR_ID + " INTEGER, " +
                "PRIMARY KEY(" + FS_COLUMN_FOLDER_ID + ", " + FS_COLUMN_SENSOR_ID + "))";
        db.execSQL(CREATE_TABLE_FOLDER_SENSORS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SENSORS + " ADD COLUMN " + S_COLUMN_REPORTING_ENABLED + " INTEGER NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                // POPRAWKA: Przekazanie wyjątku do Log.e
                Log.e("DB_UPGRADE", "Nie udało się dodać kolumny reporting_enabled", e);
            }
        }
        // Migracje (bez zmian)
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE sensors RENAME TO " + TABLE_READINGS);
            } catch (SQLException e) {
                // Fallback
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_READINGS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_GATEWAYS);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_SENSORS);
                onCreate(db);
                return;
            }
            createGatewayAndSensorTables(db);
        }
        if (oldVersion < 3) {
            createFolderAndFavoriteTables(db);
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SENSORS + " ADD COLUMN " + S_COLUMN_INTERVAL + " INTEGER");
            } catch (SQLException e) {
                // POPRAWKA: Przekazanie wyjątku do Log.e
                Log.e("DB_UPGRADE", "Nie udało się dodać kolumny interval_seconds", e);
            }
        }
    }

    // --- METODY ODCZYTÓW (READINGS) ---

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
            // POPRAWKA: Usunięcie e.getMessage() i przekazanie wyjątku
            Log.e("DB_INSERT", "SQLException while adding data", e);
        }
    }

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

                latestModel = new SensorModel(gatewayId, sId, null, type, value, timestamp, 0);
            }
        } catch (Exception e) {
            // POPRAWKA: Usunięcie zależności od context i e.getMessage()
            // Zamiast pobierać string z zasobów, logujemy techniczny błąd bezpośrednio.
            Log.e("DB_QUERY_ERROR", "Error fetching latest data", e);
        }
        return latestModel;
    }

    public List<SensorModel> getLatestUniqueSensorData() {
        List<SensorModel> latestDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " +
                "r." + COLUMN_GATE_ID + ", " +
                "r." + COLUMN_SENSOR_ID + ", " +
                "r." + COLUMN_TYPE + ", " +
                "r." + COLUMN_VALUE + ", " +
                "MAX(r." + COLUMN_TIMESTAMP + ") as " + COLUMN_TIMESTAMP + ", " +
                "s." + S_COLUMN_BATTERY + ", " +
                "s." + S_COLUMN_NAME +
                " FROM " + TABLE_READINGS + " r " +
                " LEFT JOIN " + TABLE_SENSORS + " s " +
                " ON r." + COLUMN_SENSOR_ID + " = CAST(s." + S_COLUMN_ID + " AS TEXT) " +
                " GROUP BY r." + COLUMN_GATE_ID + ", r." + COLUMN_SENSOR_ID;

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor.moveToFirst()) {
                do {
                    String gateId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_GATE_ID));
                    String sensorId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE));
                    String value = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VALUE));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));

                    int batteryIndex = cursor.getColumnIndex(S_COLUMN_BATTERY);
                    int battery = (batteryIndex != -1 && !cursor.isNull(batteryIndex)) ? cursor.getInt(batteryIndex) : 0;

                    int nameIndex = cursor.getColumnIndex(S_COLUMN_NAME);
                    String name = (nameIndex != -1) ? cursor.getString(nameIndex) : null;

                    latestDataList.add(new SensorModel(gateId, sensorId, name, type, value, timestamp, battery));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DB_FILTER_ERROR", "Error filtering unique data", e);
        }
        return latestDataList;
    }

    public Cursor getSensorHistory(String gateId, String sensorId, int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_READINGS +
                " WHERE " + COLUMN_GATE_ID + " = ? AND " + COLUMN_SENSOR_ID + " = ? " +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                " LIMIT " + limit;
        return db.rawQuery(query, new String[]{gateId, sensorId});
    }

    public void cleanOldSensorData(int days) {
        if (days <= 0) return;
        SQLiteDatabase db = this.getWritableDatabase();
        long cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L);
        try {
            db.delete(TABLE_READINGS, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTime)});
        } catch (SQLException e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DB_CLEAN", "Error cleaning old data", e);
        }
    }

    public void cleanSensorDataBySize(int maxRecords) {
        if (maxRecords <= 0) return;
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            String whereClause = COLUMN_ID + " NOT IN (" +
                    "SELECT " + COLUMN_ID + " FROM " + TABLE_READINGS +
                    " ORDER BY " + COLUMN_TIMESTAMP + " DESC " +
                    " LIMIT " + maxRecords + ")";
            db.delete(TABLE_READINGS, whereClause, null);
        } catch (SQLException e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DB_CLEAN", "Error cleaning by size", e);
        }
    }

    // --- METODY ZARZĄDZANIA METADANYMI ---

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
                        ContentValues sValues = getContentValues(gateway, sensor);
                        db.insert(TABLE_SENSORS, null, sValues);
                    }
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DB_SYNC", "Error syncing gateways", e);
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    @NonNull
    private static ContentValues getContentValues(Gateway gateway, Sensor sensor) {
        ContentValues sValues = new ContentValues();
        sValues.put(S_COLUMN_ID, sensor.getId());
        sValues.put(S_COLUMN_GATEWAY_ID, gateway.getId());
        sValues.put(S_COLUMN_NAME, sensor.getName());
        sValues.put(S_COLUMN_TYPE, sensor.getType());
        sValues.put(S_COLUMN_DESCRIPTION, sensor.getDescription());
        sValues.put(S_COLUMN_BATTERY, sensor.getBatteryLevel());
        sValues.put(S_COLUMN_KEYWORD, sensor.getKeyword());
        sValues.put(S_COLUMN_INTERVAL, sensor.getIntervalSeconds());
        sValues.put(S_COLUMN_REPORTING_ENABLED, sensor.isReportingEnabled() ? 1 : 0);
        return sValues;
    }

    public void syncFoldersAndFavorites(List<Folder> folders, List<Gateway> favoriteGateways, List<Sensor> favoriteSensors) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.beginTransaction();
            db.delete(TABLE_FOLDERS, null, null);
            db.delete(TABLE_FOLDER_GATEWAYS, null, null);
            db.delete(TABLE_FOLDER_SENSORS, null, null);

            if (folders != null) {
                for (Folder folder : folders) {
                    ContentValues fValues = new ContentValues();
                    fValues.put(F_COLUMN_ID, folder.getId());
                    fValues.put(F_COLUMN_NAME, folder.getName());
                    fValues.put(F_COLUMN_COLOR, folder.getColor());
                    db.insert(TABLE_FOLDERS, null, fValues);

                    if (folder.getGatewayIds() != null) {
                        for (Long gatewayId : folder.getGatewayIds()) {
                            ContentValues fgValues = new ContentValues();
                            fgValues.put(FG_COLUMN_FOLDER_ID, folder.getId());
                            fgValues.put(FG_COLUMN_GATEWAY_ID, gatewayId);
                            db.insert(TABLE_FOLDER_GATEWAYS, null, fgValues);
                        }
                    }
                    if (folder.getSensorIds() != null) {
                        for (Long sensorId : folder.getSensorIds()) {
                            ContentValues fsValues = new ContentValues();
                            fsValues.put(FS_COLUMN_FOLDER_ID, folder.getId());
                            fsValues.put(FS_COLUMN_SENSOR_ID, sensorId);
                            db.insert(TABLE_FOLDER_SENSORS, null, fsValues);
                        }
                    }
                }
            }

            db.delete(TABLE_FAVORITE_GATEWAYS, null, null);
            db.delete(TABLE_FAVORITE_SENSORS, null, null);

            if (favoriteGateways != null) {
                for (Gateway gateway : favoriteGateways) {
                    ContentValues favGValues = new ContentValues();
                    favGValues.put(FAV_G_GATEWAY_ID, gateway.getId());
                    db.insert(TABLE_FAVORITE_GATEWAYS, null, favGValues);
                }
            }
            if (favoriteSensors != null) {
                for (Sensor sensor : favoriteSensors) {
                    ContentValues favSValues = new ContentValues();
                    favSValues.put(FAV_S_SENSOR_ID, sensor.getId());
                    db.insert(TABLE_FAVORITE_SENSORS, null, favSValues);
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DB_SYNC", "Error syncing folders", e);
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
        }
    }

    // --- METODY ZARZĄDZANIA FOLDERAMI (LOKALNE) ---
    public void addGatewayToFolder(long gatewayId, long folderId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(FG_COLUMN_FOLDER_ID, folderId);
        values.put(FG_COLUMN_GATEWAY_ID, gatewayId);
        try {
            db.insertWithOnConflict(TABLE_FOLDER_GATEWAYS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            Log.e("DB_UPDATE", "Error addGatewayToFolder", e);
        }
    }
    public void removeGatewayFromFolder(long gatewayId, long folderId) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.delete(TABLE_FOLDER_GATEWAYS, FG_COLUMN_GATEWAY_ID + " = ? AND " + FG_COLUMN_FOLDER_ID + " = ?",
                    new String[]{String.valueOf(gatewayId), String.valueOf(folderId)});
        } catch (Exception e) {
            Log.e("DB_UPDATE", "Error removeGatewayFromFolder", e);
        }
    }
    public void addSensorToFolder(long sensorId, long folderId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(FS_COLUMN_FOLDER_ID, folderId);
        values.put(FS_COLUMN_SENSOR_ID, sensorId);
        try {
            db.insertWithOnConflict(TABLE_FOLDER_SENSORS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            Log.e("DB_UPDATE", "Error addSensorToFolder", e);
        }
    }
    public void removeSensorFromFolder(long sensorId, long folderId) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.delete(TABLE_FOLDER_SENSORS, FS_COLUMN_SENSOR_ID + " = ? AND " + FS_COLUMN_FOLDER_ID + " = ?",
                    new String[]{String.valueOf(sensorId), String.valueOf(folderId)});
        } catch (Exception e) {
            Log.e("DB_UPDATE", "Error removeSensorFromFolder", e);
        }
    }

    // --- KURSORY DO WYŚWIETLANIA DANYCH ---

    public Cursor getFoldersCursor() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + F_COLUMN_ID + " AS _id, " + F_COLUMN_NAME + ", " + F_COLUMN_COLOR + " FROM " + TABLE_FOLDERS + " ORDER BY " + F_COLUMN_NAME + " ASC";
        return db.rawQuery(query, null);
    }

    public Cursor getGatewaysForFolderCursor(long folderId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT g." + G_COLUMN_ID + " AS _id, g.* FROM " + TABLE_GATEWAYS + " g " +
                "JOIN " + TABLE_FOLDER_GATEWAYS + " fg ON g." + G_COLUMN_ID + " = fg." + FG_COLUMN_GATEWAY_ID + " " +
                "WHERE fg." + FG_COLUMN_FOLDER_ID + " = ? " +
                "ORDER BY g." + G_COLUMN_NAME + " ASC";
        return db.rawQuery(query, new String[]{String.valueOf(folderId)});
    }

    public Cursor getSensorsForGateway(long gatewayId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " +
                "s." + S_COLUMN_ID + " AS _id, " +
                "s." + S_COLUMN_ID + " AS " + COLUMN_SENSOR_ID + ", " +
                "s." + S_COLUMN_GATEWAY_ID + ", " +
                "s." + S_COLUMN_NAME + ", " +
                "s." + S_COLUMN_TYPE + ", " +
                "s." + S_COLUMN_DESCRIPTION + ", " +
                "s." + S_COLUMN_BATTERY + ", " +
                "s." + S_COLUMN_KEYWORD + ", " +
                "(SELECT " + COLUMN_VALUE + " FROM " + TABLE_READINGS + " r " +
                " WHERE r." + COLUMN_SENSOR_ID + " = CAST(s." + S_COLUMN_ID + " AS TEXT) " +
                " ORDER BY r." + COLUMN_TIMESTAMP + " DESC LIMIT 1) AS " + S_COLUMN_VALUE +
                " FROM " + TABLE_SENSORS + " s " +
                " WHERE s." + S_COLUMN_GATEWAY_ID + " = ? " +
                " ORDER BY s." + S_COLUMN_NAME + " ASC";
        return db.rawQuery(query, new String[]{String.valueOf(gatewayId)});
    }

    public Cursor getFavoriteGatewaysCursor() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT g." + G_COLUMN_ID + " AS _id, g.* FROM " + TABLE_GATEWAYS + " g " +
                "JOIN " + TABLE_FAVORITE_GATEWAYS + " fg ON g." + G_COLUMN_ID + " = fg." + FAV_G_GATEWAY_ID + " " +
                "ORDER BY g." + G_COLUMN_NAME + " ASC";
        return db.rawQuery(query, null);
    }

    public Cursor getFavoriteSensorsCursor() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT s." + S_COLUMN_ID + " AS _id, " +
                "s." + S_COLUMN_ID + " AS " + COLUMN_SENSOR_ID + ", " +
                "s.*, " +
                "g." + G_COLUMN_NAME + " AS gateway_name, " +
                "(SELECT " + COLUMN_VALUE + " FROM " + TABLE_READINGS + " r " +
                " WHERE r." + COLUMN_SENSOR_ID + " = CAST(s." + S_COLUMN_ID + " AS TEXT) " +
                " ORDER BY r." + COLUMN_TIMESTAMP + " DESC LIMIT 1) AS " + S_COLUMN_VALUE +
                " FROM " + TABLE_SENSORS + " s " +
                " JOIN " + TABLE_FAVORITE_SENSORS + " fs ON s." + S_COLUMN_ID + " = fs." + FAV_S_SENSOR_ID + " " +
                " LEFT JOIN " + TABLE_GATEWAYS + " g ON s." + S_COLUMN_GATEWAY_ID + " = g." + G_COLUMN_ID + " " +
                " ORDER BY s." + S_COLUMN_NAME + " ASC";
        return db.rawQuery(query, null);
    }

    public Cursor getSensorsForFolderCursor(long folderId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT s." + S_COLUMN_ID + " AS _id, " +
                "s." + S_COLUMN_ID + " AS " + COLUMN_SENSOR_ID + ", " +
                "s.*, " +
                "g." + G_COLUMN_NAME + " AS gateway_name, " +
                "(SELECT " + COLUMN_VALUE + " FROM " + TABLE_READINGS + " r " +
                " WHERE r." + COLUMN_SENSOR_ID + " = CAST(s." + S_COLUMN_ID + " AS TEXT) " +
                " ORDER BY r." + COLUMN_TIMESTAMP + " DESC LIMIT 1) AS " + S_COLUMN_VALUE +
                " FROM " + TABLE_SENSORS + " s " +
                " JOIN " + TABLE_FOLDER_SENSORS + " fs ON s." + S_COLUMN_ID + " = fs." + FS_COLUMN_SENSOR_ID + " " +
                " LEFT JOIN " + TABLE_GATEWAYS + " g ON s." + S_COLUMN_GATEWAY_ID + " = g." + G_COLUMN_ID + " " +
                " WHERE fs." + FS_COLUMN_FOLDER_ID + " = ? " +
                " ORDER BY s." + S_COLUMN_NAME + " ASC";
        return db.rawQuery(query, new String[]{String.valueOf(folderId)});
    }

    public Cursor getUncategorizedGatewaysCursor() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT g." + G_COLUMN_ID + " AS _id, g.* FROM " + TABLE_GATEWAYS + " g " +
                "WHERE g." + G_COLUMN_ID + " NOT IN (SELECT " + FG_COLUMN_GATEWAY_ID + " FROM " + TABLE_FOLDER_GATEWAYS + ")" +
                " ORDER BY g." + G_COLUMN_NAME + " ASC";
        return db.rawQuery(query, null);
    }

    // --- INNE POMOCNICZE ---

    public boolean isFavoriteSensor(long sensorId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor c = db.query(TABLE_FAVORITE_SENSORS, new String[]{FAV_S_SENSOR_ID}, FAV_S_SENSOR_ID + " = ?", new String[]{String.valueOf(sensorId)}, null, null, null, "1")) {
            return c.getCount() > 0;
        }
    }
    public boolean isFavoriteGateway(long gatewayId) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor c = db.query(TABLE_FAVORITE_GATEWAYS, new String[]{FAV_G_GATEWAY_ID}, FAV_G_GATEWAY_ID + " = ?", new String[]{String.valueOf(gatewayId)}, null, null, null, "1")) {
            return c.getCount() > 0;
        }
    }

    public Sensor getSensorMetadata(long sensorId) {
        Sensor sensorData = null;
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_SENSORS, new String[]{S_COLUMN_INTERVAL, S_COLUMN_REPORTING_ENABLED}, S_COLUMN_ID + " = ?", new String[]{String.valueOf(sensorId)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                sensorData = new Sensor();
                int intervalIndex = cursor.getColumnIndex(S_COLUMN_INTERVAL);
                if (!cursor.isNull(intervalIndex)) sensorData.intervalSeconds = cursor.getInt(intervalIndex);
                int reportingIndex = cursor.getColumnIndex(S_COLUMN_REPORTING_ENABLED);
                sensorData.reportingEnabled = cursor.getInt(reportingIndex) != 0;
            }
        } catch (Exception e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DatabaseHelper", "Error getting metadata", e);
        }
        return sensorData;
    }

    public void updateSensorReportingStatus(long sensorId, boolean isEnabled) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(S_COLUMN_REPORTING_ENABLED, isEnabled ? 1 : 0);
        try {
            db.update(TABLE_SENSORS, values, S_COLUMN_ID + " = ?", new String[]{String.valueOf(sensorId)});
        } catch (Exception e) {
            // POPRAWKA: Przekazanie wyjątku
            Log.e("DatabaseHelper", "Error updating status", e);
        }
    }
    // --- METODY DO FILTROWANIA (NOWE) ---

    // Pobiera unikalne typy czujników (do listy rozwijanej)
    public List<String> getAllSensorTypes() {
        List<String> types = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT DISTINCT " + S_COLUMN_TYPE + " FROM " + TABLE_SENSORS + " WHERE " + S_COLUMN_TYPE + " IS NOT NULL ORDER BY " + S_COLUMN_TYPE;
        try (Cursor cursor = db.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                types.add(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e("DB_FILTER", "Error fetching types", e);
        }
        return types;
    }

    // Pobiera listę bramek (ID i Nazwa) do listy rozwijanej
    public List<Gateway> getAllGatewaysList() {
        List<Gateway> gateways = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + G_COLUMN_ID + ", " + G_COLUMN_NAME + " FROM " + TABLE_GATEWAYS + " ORDER BY " + G_COLUMN_NAME;
        try (Cursor cursor = db.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                Gateway g = new Gateway();
                g.id = cursor.getLong(0);
                g.name = cursor.getString(1);
                gateways.add(g);
            }
        } catch (Exception e) {
            Log.e("DB_FILTER", "Error fetching gateways list", e);
        }
        return gateways;
    }
}