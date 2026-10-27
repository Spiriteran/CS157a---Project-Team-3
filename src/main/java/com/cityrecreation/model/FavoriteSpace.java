package com.cityrecreation.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Represents a saved favorite space for a user.
 * Corresponds to USER_FAVORITE joined with SPACE, FACILITY, and CITY.
 */
public class FavoriteSpace implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private int spaceId;
    private String spaceName;
    private String spaceType;
    private String facilityName;
    private String cityName;
    private String accessMode;
    private BigDecimal price;

    public FavoriteSpace() {
    }

    public FavoriteSpace(int userId, int spaceId, String spaceName, String spaceType,
                         String facilityName, String cityName, String accessMode, BigDecimal price) {
        this.userId = userId;
        this.spaceId = spaceId;
        this.spaceName = spaceName;
        this.spaceType = spaceType;
        this.facilityName = facilityName;
        this.cityName = cityName;
        this.accessMode = accessMode;
        this.price = price;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(int spaceId) {
        this.spaceId = spaceId;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getSpaceType() {
        return spaceType;
    }

    public void setSpaceType(String spaceType) {
        this.spaceType = spaceType;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
