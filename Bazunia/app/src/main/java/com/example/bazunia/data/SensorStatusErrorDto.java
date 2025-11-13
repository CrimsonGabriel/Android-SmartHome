package com.example.bazunia.data;

// Ta klasa musi pasować do DTO zwracanego przez backend
// Używamy adnotacji, aby pasowały do pól JSON (np. readableMessage)
// jeśli backend używa camelCase (np. readableMessage), adnotacje nie są potrzebne.
// Załóżmy, że backend wysyła pola pasujące do tych nazw.

public class SensorStatusErrorDto {

    public Long entityId;
    public String entityType; // np. "GATEWAY" lub "SENSOR"
    public String entityName; // np. "Bramka w Kuchni"
    public String errorType; // np. "OFFLINE"
    public String readableMessage; // np. "Bramka 'Kuchnia' jest offline od 30 minut."

    // Konstruktor bezargumentowy jest potrzebny dla Gson
    public SensorStatusErrorDto() {
    }
}