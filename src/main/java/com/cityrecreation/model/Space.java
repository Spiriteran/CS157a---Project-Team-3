package com.cityrecreation.model;

public class Space {
    public int id;
    public String name;
    public String accessType;
    public String status; // 'available', 'reserved'

    public Space(int id, String name, String accessType, String status) {
        this.id = id;
        this.name = name;
        this.accessType = accessType;
        this.status = status;
    }
}
