package com.testserwera.bazunia.data;

// Ta klasa musi pasować do DTO zwracanego przez backend
// Używamy adnotacji, aby pasowały do pól JSON (np. readableMessage)
// jeśli backend używa camelCase (np. readableMessage), adnotacje nie są potrzebne.
// Załóżmy, że backend wysyła pola pasujące do tych nazw.

public class SensorStatusErrorDto {

    public Long entityId;
    public String entityType;
    public String entityName;
    public String readableMessage;
    public SensorStatusErrorDto() {
    }
}