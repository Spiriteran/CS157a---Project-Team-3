package com.cityrecreation.controller;

import com.cityrecreation.model.User;
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
import java.util.Map;

/**
 * Handles user authentication lifecycle:
 * - POST /api/auth/login
 * - POST /api/auth/register
 * - POST /api/auth/logout
 * - GET  /api/auth/me
 */
@WebServlet({"/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/me"})
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_USER_ID = "SESSION_USER_ID";
    private final Gson gson = new Gson();
    private final GeneralUserService userService = ServiceRegistry.getGeneralUserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getServletPath();
        resp.setContentType("application/json;charset=UTF-8");

        if ("/api/auth/me".equals(path)) {
            handleMe(req, resp);
        } else {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getServletPath();
        resp.setContentType("application/json;charset=UTF-8");

        switch (path) {
            case "/api/auth/login":
                handleLogin(req, resp);
                break;
            case "/api/auth/register":
                handleRegister(req, resp);
                break;
            case "/api/auth/logout":
                handleLogout(req, resp);
                break;
            default:
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
                break;
        }
    }

    private void handleMe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        Map<String, Object> out = new LinkedHashMap<>();

        if (session != null && session.getAttribute(SESSION_USER_ID) != null) {
            int userId = (Integer) session.getAttribute(SESSION_USER_ID);
            User user = userService.findById(userId);
            if (user != null) {
                out.put("authenticated", true);
                out.put("user", sanitizeUser(user));
                resp.getWriter().write(gson.toJson(out));
                return;
            }
        }

        out.put("authenticated", false);
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseBody(req);
        String email = getString(json, "email");
        String password = getString(json, "password");

        if (email.isEmpty() || password.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Email and password are required.");
            return;
        }

        User user = userService.authenticate(email, password);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid email or password.");
            return;
        }

        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getUserId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Logged in successfully.");
        out.put("user", sanitizeUser(user));
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseBody(req);
        String email = getString(json, "email");
        String password = getString(json, "password");
        String phone = getString(json, "phone");
        String role = getString(json, "role");

        if (email.isEmpty() || password.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Email and password are required.");
            return;
        }

        if (role.isEmpty()) {
            role = "GENERAL_USER";
        }

        User user = userService.register(email, password, phone, role);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, "An account with that email already exists.");
            return;
        }

        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getUserId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Account registered successfully.");
        out.put("user", sanitizeUser(user));
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Logged out.");
        resp.getWriter().write(gson.toJson(out));
    }

    private Map<String, Object> sanitizeUser(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", user.getUserId());
        m.put("roleId", user.getRoleId());
        m.put("roleName", user.getRoleName());
        m.put("email", user.getEmail());
        m.put("phoneNum", user.getPhoneNum());
        m.put("emailVerified", user.isEmailVerified());
        m.put("twofaEnabled", user.isTwofaEnabled());
        return m;
    }

    private JsonObject parseBody(HttpServletRequest req) {
        try (BufferedReader reader = req.getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString().trim();
        }
        return "";
    }

    private void sendError(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", msg);
        resp.getWriter().write(gson.toJson(err));
    }
}
