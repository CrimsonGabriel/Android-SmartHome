package com.testserwera.bazunia.data;

/**
 * Model POJO dla obiektu Czujnika (zagnieżdżony w Bramce).
 * Używa Gson do deserializacji.
 */
public class Sensor {
    long id;
    String name;
    String type;
    String description;
    Integer batteryLevel;
    String keyword;
    Integer intervalSeconds;

    boolean reportingEnabled;

    public long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public String getKeyword() { return keyword; }
    public Integer getIntervalSeconds() { return intervalSeconds; }
    public boolean isReportingEnabled() { return reportingEnabled; }
}