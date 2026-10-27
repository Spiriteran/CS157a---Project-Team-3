package com.cityrecreation.service;

import com.cityrecreation.model.Comment;
import com.cityrecreation.model.EventParticipant;
import com.cityrecreation.model.FavoriteSpace;
import com.cityrecreation.model.Preference;
import com.cityrecreation.model.User;

import java.util.List;

/**
 * Service contract for General User tier operations:
 * - Authentication & registration
 * - Preferences (default city, sport, radius, notifications, calendar view)
 * - Saved favorite spaces
 * - Event RSVPs
 * - Event comments / discussion
 */
public interface GeneralUserService {

    User authenticate(String email, String password);

    User register(String email, String password, String phoneNum, String roleName);

    User findById(int userId);

    Preference getPreferences(int userId);

    boolean updatePreferences(int userId, Preference pref);

    List<FavoriteSpace> getFavorites(int userId);

    boolean addFavorite(int userId, int spaceId);

    boolean removeFavorite(int userId, int spaceId);

    boolean isFavorite(int userId, int spaceId);

    List<EventParticipant> getEventParticipants(int eventId);

    String getUserRsvp(int userId, int eventId);

    boolean setRsvp(int userId, int eventId, String status);

    List<Comment> getEventComments(int eventId);

    Comment addComment(int userId, int eventId, String body);

    boolean deleteComment(int userId, int commentId);
}
