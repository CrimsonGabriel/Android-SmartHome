package com.testserwera.bazunia.data;

import java.util.List;

/**
 * Model POJO dla obiektu Bramki pobieranego z serwera (z /api/gateways).
 * Używa Gson do deserializacji.
 */
public class Gateway {
    // Nazwy pól muszą pasować do JSON-a z serwera (z backendu Gateway.java)
    long id;
    String name;
    String status;
    String folder;
    String description;
    String lastSeen;
    Long ownerId;
    List<Sensor> sensors; // Zagnieżdżona lista czujników

    // Gettery są potrzebne dla adaptera
    public long getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getFolder() { return folder; }
    public String getDescription() { return description; }
    public String getLastSeen() { return lastSeen; }
    public Long getOwnerId() { return ownerId; }
    public List<Sensor> getSensors() { return sensors; }


    @Override
    public String toString() {
        return name + " (ID: " + id + ")";
    }
}