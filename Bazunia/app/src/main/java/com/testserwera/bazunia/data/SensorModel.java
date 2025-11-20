package com.testserwera.bazunia.data;


import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Model danych przechowujący komplet informacji o ODCZYCIE czujnika.
 * Używany przez MainActivity i SensorDetailActivity.
 */
public class SensorModel implements Serializable {
    public final String gatewayId;
    public final String sensorId;
    public final String type;
    public final String value;
    public final long timestamp;

    public SensorModel(String gatewayId, String sensorId, String type, String value, long timestamp) {
        this.gatewayId = gatewayId;
        this.sensorId = sensorId;
        this.type = type;
        this.value = value;
        this.timestamp = timestamp;
    }

    /**
     * Zwraca sformatowany czas w standardzie polskim.
     */
    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // <<< USUNIĘTO: Metoda getIconResourceId() została przeniesiona do GatewaySensorCursorAdapter >>>
}