package com.example.bazunia.data;

import com.google.gson.annotations.SerializedName;
import java.util.Set;

// Ten model pasuje do FolderDto z backendu
public class Folder {

    @SerializedName("id")
    private Long id;

    @SerializedName("name")
    private String name;

    @SerializedName("color")
    private String color;

    @SerializedName("gatewayIds")
    private Set<Long> gatewayIds;

    // Gettery
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public Set<Long> getGatewayIds() { return gatewayIds; }
}