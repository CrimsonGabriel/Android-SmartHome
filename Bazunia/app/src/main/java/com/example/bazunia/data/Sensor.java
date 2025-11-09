package com.example.bazunia.data;

/**
 * Model POJO dla obiektu Czujnika (zagnieżdżony w Bramce).
 * Używa Gson do deserializacji.
 */
public class Sensor {
    // Nazwy pól muszą pasować do JSON-a z serwera (z backendu Sensor.java)
    long id;
    String name;
    String type;
    String description;
    Integer batteryLevel;

    // Gettery
    public long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public Integer getBatteryLevel() { return batteryLevel; }
}