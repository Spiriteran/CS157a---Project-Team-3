package com.cityrecreation.model;

import java.util.ArrayList;
import java.util.List;

public class Facility {
    public int id;
    public String name;
    public String address;
    public double lat;
    public double lng;
    public String status;
    public String label; // For map marker numbering
    public List<Space> spaces = new ArrayList<>();

    public Facility(int id, String name, String address, double lat, double lng, String status) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.status = status;
        this.label = String.valueOf(id);
    }
}
