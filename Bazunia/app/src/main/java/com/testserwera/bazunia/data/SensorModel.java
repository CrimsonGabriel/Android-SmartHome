package com.testserwera.bazunia.data;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SensorModel implements Serializable {
    public final String gatewayId;
    public final String sensorId;
    public final String name;
    public final String type;
    public final String value;
    public final long timestamp;
    public final int batteryLevel;

    public SensorModel(String gatewayId, String sensorId, String name, String type, String value, long timestamp, int batteryLevel) {
        this.gatewayId = gatewayId;
        this.sensorId = sensorId;
        this.name = name;
        this.type = type;
        this.value = value;
        this.timestamp = timestamp;
        this.batteryLevel = batteryLevel;
    }

    public String getFormattedTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}