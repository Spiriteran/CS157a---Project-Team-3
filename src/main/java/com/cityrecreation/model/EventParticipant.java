package com.cityrecreation.model;

import java.io.Serializable;

/**
 * Represents an RSVP by a user for a public event.
 * Corresponds to EVENT_PARTICIPANT in schema.sql.
 */
public class EventParticipant implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String userEmail;
    private int eventId;
    private String status; // GOING, MAYBE, DECLINED, INVITED
    private String joinedTime;

    public EventParticipant() {
    }

    public EventParticipant(int userId, String userEmail, int eventId, String status, String joinedTime) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.eventId = eventId;
        this.status = status;
        this.joinedTime = joinedTime;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJoinedTime() {
        return joinedTime;
    }

    public void setJoinedTime(String joinedTime) {
        this.joinedTime = joinedTime;
    }
}
