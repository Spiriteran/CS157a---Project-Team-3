package com.cityrecreation.model;

import java.io.Serializable;

/**
 * Represents a comment on an event.
 * Corresponds to COMMENT in schema.sql.
 */
public class Comment implements Serializable {
    private static final long serialVersionUID = 1L;

    private int commentId;
    private int eventId;
    private int userId;
    private String userEmail;
    private String body;
    private String createdTime;

    public Comment() {
    }

    public Comment(int commentId, int eventId, int userId, String userEmail, String body, String createdTime) {
        this.commentId = commentId;
        this.eventId = eventId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.body = body;
        this.createdTime = createdTime;
    }

    public int getCommentId() {
        return commentId;
    }

    public void setCommentId(int commentId) {
        this.commentId = commentId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }
}
