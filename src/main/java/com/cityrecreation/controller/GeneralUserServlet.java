package com.cityrecreation.controller;

import com.cityrecreation.model.Comment;
import com.cityrecreation.model.EventParticipant;
import com.cityrecreation.model.FavoriteSpace;
import com.cityrecreation.model.Preference;
import com.cityrecreation.service.GeneralUserService;
import com.cityrecreation.service.ServiceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves General User actions:
 * - /api/user/preferences (GET, POST)
 * - /api/user/favorites   (GET, POST, DELETE)
 * - /api/user/rsvp        (GET, POST)
 * - /api/user/comments    (GET, POST, DELETE)
 */
@WebServlet({"/api/user/preferences", "/api/user/favorites", "/api/user/rsvp", "/api/user/comments"})
public class GeneralUserServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_USER_ID = "SESSION_USER_ID";
    private final Gson gson = new Gson();
    private final GeneralUserService userService = ServiceRegistry.getGeneralUserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getServletPath();
        resp.setContentType("application/json;charset=UTF-8");

        switch (path) {
            case "/api/user/preferences":
                handleGetPreferences(req, resp);
                break;
            case "/api/user/favorites":
                handleGetFavorites(req, resp);
                break;
            case "/api/user/rsvp":
                handleGetRsvp(req, resp);
                break;
            case "/api/user/comments":
                handleGetComments(req, resp);
                break;
            default:
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getServletPath();
        resp.setContentType("application/json;charset=UTF-8");

        switch (path) {
            case "/api/user/preferences":
                handleUpdatePreferences(req, resp);
                break;
            case "/api/user/favorites":
                handleAddFavorite(req, resp);
                break;
            case "/api/user/rsvp":
                handleSetRsvp(req, resp);
                break;
            case "/api/user/comments":
                handleAddComment(req, resp);
                break;
            default:
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
                break;
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getServletPath();
        resp.setContentType("application/json;charset=UTF-8");

        switch (path) {
            case "/api/user/favorites":
                handleRemoveFavorite(req, resp);
                break;
            case "/api/user/comments":
                handleDeleteComment(req, resp);
                break;
            default:
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
                break;
        }
    }

    // ----- Preferences -----
    private void handleGetPreferences(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to view your preferences.");
            return;
        }
        Preference p = userService.getPreferences(userId);
        resp.getWriter().write(gson.toJson(p));
    }

    private void handleUpdatePreferences(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to update your preferences.");
            return;
        }

        JsonObject json = parseBody(req);
        Preference p = userService.getPreferences(userId);

        if (json.has("defaultCityId") && !json.get("defaultCityId").isJsonNull()) {
            p.setDefaultCityId(json.get("defaultCityId").getAsInt());
        }
        if (json.has("defaultCityName") && !json.get("defaultCityName").isJsonNull()) {
            p.setDefaultCityName(json.get("defaultCityName").getAsString());
        }
        if (json.has("defaultSport") && !json.get("defaultSport").isJsonNull()) {
            p.setDefaultSport(json.get("defaultSport").getAsString());
        }
        if (json.has("mapRadius") && !json.get("mapRadius").isJsonNull()) {
            p.setMapRadius(json.get("mapRadius").getAsInt());
        }
        if (json.has("notifyPref") && !json.get("notifyPref").isJsonNull()) {
            p.setNotifyPref(json.get("notifyPref").getAsString());
        }
        if (json.has("calendarView") && !json.get("calendarView").isJsonNull()) {
            p.setCalendarView(json.get("calendarView").getAsString());
        }

        userService.updatePreferences(userId, p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("preference", p);
        resp.getWriter().write(gson.toJson(out));
    }

    // ----- Favorites -----
    private void handleGetFavorites(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to view your favorites.");
            return;
        }

        String spaceIdParam = req.getParameter("space_id");
        if (spaceIdParam != null && !spaceIdParam.trim().isEmpty()) {
            try {
                int sid = Integer.parseInt(spaceIdParam.trim());
                boolean isFav = userService.isFavorite(userId, sid);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("spaceId", sid);
                out.put("isFavorite", isFav);
                resp.getWriter().write(gson.toJson(out));
                return;
            } catch (NumberFormatException ignored) {
            }
        }

        List<FavoriteSpace> favs = userService.getFavorites(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", favs.size());
        out.put("favorites", favs);
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleAddFavorite(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to favorite spaces.");
            return;
        }

        JsonObject json = parseBody(req);
        if (!json.has("spaceId")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "spaceId is required.");
            return;
        }

        int spaceId = json.get("spaceId").getAsInt();
        boolean ok = userService.addFavorite(userId, spaceId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", ok);
        out.put("spaceId", spaceId);
        out.put("isFavorite", true);
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleRemoveFavorite(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to manage favorites.");
            return;
        }

        String spaceIdParam = req.getParameter("space_id");
        int spaceId = -1;
        if (spaceIdParam != null && !spaceIdParam.trim().isEmpty()) {
            try {
                spaceId = Integer.parseInt(spaceIdParam.trim());
            } catch (Exception ignored) {
            }
        }
        if (spaceId <= 0) {
            JsonObject json = parseBody(req);
            if (json.has("spaceId")) {
                spaceId = json.get("spaceId").getAsInt();
            }
        }

        if (spaceId <= 0) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid spaceId is required.");
            return;
        }

        boolean ok = userService.removeFavorite(userId, spaceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", ok);
        out.put("spaceId", spaceId);
        out.put("isFavorite", false);
        resp.getWriter().write(gson.toJson(out));
    }

    // ----- RSVP -----
    private void handleGetRsvp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String eventIdParam = req.getParameter("event_id");
        if (eventIdParam == null || eventIdParam.trim().isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "event_id is required.");
            return;
        }

        int eventId;
        try {
            eventId = Integer.parseInt(eventIdParam.trim());
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid event_id.");
            return;
        }

        List<EventParticipant> participants = userService.getEventParticipants(eventId);
        Integer userId = getSessionUserId(req);
        String userStatus = (userId != null) ? userService.getUserRsvp(userId, eventId) : null;

        long goingCount = participants.stream().filter(p -> "GOING".equalsIgnoreCase(p.getStatus())).count();
        long maybeCount = participants.stream().filter(p -> "MAYBE".equalsIgnoreCase(p.getStatus())).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", eventId);
        out.put("goingCount", goingCount);
        out.put("maybeCount", maybeCount);
        out.put("userStatus", userStatus);
        out.put("participants", participants);
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleSetRsvp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to RSVP to events.");
            return;
        }

        JsonObject json = parseBody(req);
        if (!json.has("eventId") || !json.has("status")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "eventId and status are required.");
            return;
        }

        int eventId = json.get("eventId").getAsInt();
        String status = json.get("status").getAsString();

        boolean ok = userService.setRsvp(userId, eventId, status);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", ok);
        out.put("eventId", eventId);
        out.put("status", status.toUpperCase());
        resp.getWriter().write(gson.toJson(out));
    }

    // ----- Comments -----
    private void handleGetComments(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String eventIdParam = req.getParameter("event_id");
        if (eventIdParam == null || eventIdParam.trim().isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "event_id is required.");
            return;
        }

        int eventId;
        try {
            eventId = Integer.parseInt(eventIdParam.trim());
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid event_id.");
            return;
        }

        List<Comment> comments = userService.getEventComments(eventId);
        Integer userId = getSessionUserId(req);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", eventId);
        out.put("count", comments.size());
        out.put("currentUserId", userId);
        out.put("comments", comments);
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleAddComment(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to post comments.");
            return;
        }

        JsonObject json = parseBody(req);
        if (!json.has("eventId") || !json.has("body")) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "eventId and body are required.");
            return;
        }

        int eventId = json.get("eventId").getAsInt();
        String body = json.get("body").getAsString();

        if (body.trim().isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Comment body cannot be blank.");
            return;
        }

        Comment comment = userService.addComment(userId, eventId, body);
        if (comment == null) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to post comment.");
            return;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("comment", comment);
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleDeleteComment(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Integer userId = getSessionUserId(req);
        if (userId == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Sign in to manage comments.");
            return;
        }

        String commentIdParam = req.getParameter("comment_id");
        int commentId = -1;
        if (commentIdParam != null && !commentIdParam.trim().isEmpty()) {
            try {
                commentId = Integer.parseInt(commentIdParam.trim());
            } catch (Exception ignored) {
            }
        }
        if (commentId <= 0) {
            JsonObject json = parseBody(req);
            if (json.has("commentId")) {
                commentId = json.get("commentId").getAsInt();
            }
        }

        if (commentId <= 0) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Valid commentId is required.");
            return;
        }

        boolean ok = userService.deleteComment(userId, commentId);
        if (!ok) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Cannot delete this comment (not found or not author).");
            return;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("commentId", commentId);
        resp.getWriter().write(gson.toJson(out));
    }

    // ----- Helpers -----
    private Integer getSessionUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute(SESSION_USER_ID) != null) {
            return (Integer) session.getAttribute(SESSION_USER_ID);
        }
        return null;
    }

    private JsonObject parseBody(HttpServletRequest req) {
        try (BufferedReader reader = req.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private void sendError(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", msg);
        resp.getWriter().write(gson.toJson(err));
    }
}
