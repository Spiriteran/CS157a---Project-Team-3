package com.cityrecreation.model;

import java.io.Serializable;

/**
 * Represents a user's navigation and notification preferences.
 * Corresponds to the PREFERENCE table in schema.sql.
 */
public class Preference implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private Integer defaultCityId;
    private String defaultCityName;
    private String defaultSport;
    private int mapRadius = 10;
    private String notifyPref = "EMAIL";
    private String calendarView = "MONTH";

    public Preference() {
    }

    public Preference(int userId, Integer defaultCityId, String defaultCityName,
                      String defaultSport, int mapRadius, String notifyPref, String calendarView) {
        this.userId = userId;
        this.defaultCityId = defaultCityId;
        this.defaultCityName = defaultCityName;
        this.defaultSport = defaultSport;
        this.mapRadius = mapRadius;
        this.notifyPref = notifyPref != null ? notifyPref : "EMAIL";
        this.calendarView = calendarView != null ? calendarView : "MONTH";
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Integer getDefaultCityId() {
        return defaultCityId;
    }

    public void setDefaultCityId(Integer defaultCityId) {
        this.defaultCityId = defaultCityId;
    }

    public String getDefaultCityName() {
        return defaultCityName;
    }

    public void setDefaultCityName(String defaultCityName) {
        this.defaultCityName = defaultCityName;
    }

    public String getDefaultSport() {
        return defaultSport;
    }

    public void setDefaultSport(String defaultSport) {
        this.defaultSport = defaultSport;
    }

    public int getMapRadius() {
        return mapRadius;
    }

    public void setMapRadius(int mapRadius) {
        this.mapRadius = mapRadius;
    }

    public String getNotifyPref() {
        return notifyPref;
    }

    public void setNotifyPref(String notifyPref) {
        this.notifyPref = notifyPref;
    }

    public String getCalendarView() {
        return calendarView;
    }

    public void setCalendarView(String calendarView) {
        this.calendarView = calendarView;
    }
}
