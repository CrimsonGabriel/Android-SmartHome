package com.example.bazunia;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Model danych przechowujący komplet informacji o czujniku,
 * co zwiększa czytelność kodu w DataActivity i Adapterze.
 */
public class SensorModel implements Serializable {
    public final String gatewayId;
    public final String sensorId;
    public final String type;
    public final String value;
    public final long timestamp; // Zmieniono na long (milisekundy) dla lepszej precyzji

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


    public int getIconResourceId() {
        if ("temperature".equalsIgnoreCase(type)) {
            return R.drawable.ic_temp;
        } else if ("humidity".equalsIgnoreCase(type)) {
            return R.drawable.ic_humidity;
        } else if ("door_contact".equalsIgnoreCase(type)) {
            // Wartość "1" to OTWARTY/ALERT - ikona otwarta
            if ("1".equals(value)) {
                return R.drawable.ic_door_open;
            }
            // Wartość "0" to ZAMKNIĘTY/OK - ikona zamknięta
            else {
                return R.drawable.ic_door_closed;
            }
        }
        // Domyślna ikona
        return R.drawable.ic_sensor;
    }
}