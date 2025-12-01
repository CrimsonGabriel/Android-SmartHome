package com.testserwera.bazunia.data;

import com.google.gson.annotations.SerializedName;
import java.util.Set;

public class Folder {

    @SerializedName("id")
    Long id;

    @SerializedName("name")
    String name;

    @SerializedName("color")
    String color;

    @SerializedName("gatewayIds")
    Set<Long> gatewayIds;

    @SerializedName("sensorIds")
    Set<Long> sensorIds;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public Set<Long> getGatewayIds() { return gatewayIds; }
    public Set<Long> getSensorIds() { return sensorIds; }
}