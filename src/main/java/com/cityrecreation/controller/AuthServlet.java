package com.cityrecreation.controller;

import com.cityrecreation.model.User;
import com.cityrecreation.service.GeneralUserService;
import com.cityrecreation.service.ServiceRegistry;
import com.cityrecreation.util.PasswordUtil;
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
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles user authentication lifecycle (FR-6) including role-aware registration
 * (GENERAL_USER / RESERVATION_USER) and simulated email verification / 2FA flows:
 * - GET  /api/auth/me
 * - POST /api/auth/login
 * - POST /api/auth/register
 * - POST /api/auth/verify-email
 * - POST /api/auth/resend-email-code
 * - POST /api/auth/verify-2fa
 * - POST /api/auth/logout
 */
@WebServlet({
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/verify-email",
        "/api/auth/resend-email-code",
        "/api/auth/verify-2fa",
        "/api/auth/logout",
        "/api/auth/me"
})
public class AuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String SESSION_USER_ID = "SESSION_USER_ID";
    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();
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
            case "/api/auth/verify-email":
                handleVerifyEmail(req, resp);
                break;
            case "/api/auth/resend-email-code":
                handleResendEmailCode(req, resp);
                break;
            case "/api/auth/verify-2fa":
                handleVerifyTwoFactor(req, resp);
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

        Integer userId = null;
        if (session != null) {
            Object raw = session.getAttribute(SESSION_USER_ID);
            if (raw == null) {
                raw = session.getAttribute("userId");
            }
            if (raw instanceof Integer) {
                userId = (Integer) raw;
            }
        }

        if (userId != null) {
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
        String password = getRawString(json, "password");

        if (email.isEmpty() || password.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Email and password are required.");
            return;
        }

        User user = userService.authenticate(email, password);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid email or password.");
            return;
        }

        if (!user.isEmailVerified()) {
            String code = getOrIssuePendingEmailCode(req, user.getEmail());
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("error", "Verify your email before signing in.");
            out.put("requiresEmailVerification", true);
            out.put("email", user.getEmail());
            out.put("code", code);
            resp.getWriter().write(gson.toJson(out));
            return;
        }

        if (user.isTwofaEnabled()) {
            HttpSession session = req.getSession(true);
            String code = newCode();
            session.setAttribute("pending2faUserId", user.getUserId());
            session.setAttribute("pending2faCode", code);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("requires2fa", true);
            out.put("code", code);
            out.put("message", "A demo 2FA code was sent to your phone.");
            resp.getWriter().write(gson.toJson(out));
            return;
        }

        establishSession(req, user);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Logged in successfully.");
        out.put("user", sanitizeUser(user));
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseBody(req);
        String normalizedEmail = PasswordUtil.normalizeEmail(getString(json, "email"));
        String password = getRawString(json, "password");
        String confirmPassword = json.has("confirmPassword") ? getRawString(json, "confirmPassword") : null;
        String phone = getString(json, "phone");
        String role = getString(json, "role");

        if (normalizedEmail == null || password.length() < 8) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Use a valid email and a password with at least 8 characters.");
            return;
        }

        if (confirmPassword != null && !password.equals(confirmPassword)) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Your passwords do not match.");
            return;
        }

        if (role.isEmpty()) {
            role = "GENERAL_USER";
        } else if (!"GENERAL_USER".equals(role) && !"RESERVATION_USER".equals(role)) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Choose a valid account type.");
            return;
        }

        User user = userService.register(normalizedEmail, password, phone, role);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, "An account with that email already exists.");
            return;
        }

        String code = issuePendingEmailCode(req, user.getEmail());
        resp.setStatus(HttpServletResponse.SC_CREATED);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("requiresEmailVerification", true);
        out.put("email", user.getEmail());
        out.put("code", code);
        out.put("message", "Account created! A demo verification email was sent to " + user.getEmail() + ".");
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleVerifyEmail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseBody(req);
        String email = PasswordUtil.normalizeEmail(getString(json, "email"));
        String code = getString(json, "code");

        HttpSession session = req.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute("pendingEmailCode");
        String pendingEmail = session == null ? null : (String) session.getAttribute("pendingEmail");

        if (email == null || expected == null || !expected.equals(code) || !email.equals(pendingEmail)) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "That email verification code is not valid.");
            return;
        }

        if (!userService.markEmailVerified(email)) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not verify email right now.");
            return;
        }

        session.removeAttribute("pendingEmailCode");
        session.removeAttribute("pendingEmail");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Email verified! You can now sign in.");
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleResendEmailCode(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseBody(req);
        String email = PasswordUtil.normalizeEmail(getString(json, "email"));
        if (email == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Enter a valid email address.");
            return;
        }
        User user = userService.findByEmail(email);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "No account found for that email.");
            return;
        }
        if (user.isEmailVerified()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "This email is already verified.");
            return;
        }

        String code = issuePendingEmailCode(req, user.getEmail());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("requiresEmailVerification", true);
        out.put("email", user.getEmail());
        out.put("code", code);
        out.put("message", "A new demo verification email was sent to " + user.getEmail() + ".");
        resp.getWriter().write(gson.toJson(out));
    }

    private void handleVerifyTwoFactor(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseBody(req);
        String code = getString(json, "code");

        HttpSession session = req.getSession(false);
        if (session == null
                || session.getAttribute("pending2faUserId") == null
                || !String.valueOf(session.getAttribute("pending2faCode")).equals(code)) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "That 2FA code is not valid.");
            return;
        }

        int userId = (Integer) session.getAttribute("pending2faUserId");
        User user = userService.findById(userId);
        if (user == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Pending user session expired.");
            return;
        }

        establishSession(req, user);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Two-factor verification complete.");
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

    private String getOrIssuePendingEmailCode(HttpServletRequest req, String email) {
        HttpSession session = req.getSession(true);
        Object pendingEmail = session.getAttribute("pendingEmail");
        Object pendingCode = session.getAttribute("pendingEmailCode");
        if (email.equals(pendingEmail) && pendingCode instanceof String) {
            return (String) pendingCode;
        }
        return issuePendingEmailCode(req, email);
    }

    private String issuePendingEmailCode(HttpServletRequest req, String email) {
        HttpSession session = req.getSession(true);
        String code = newCode();
        session.setAttribute("pendingEmail", email);
        session.setAttribute("pendingEmailCode", code);
        return code;
    }

    private void establishSession(HttpServletRequest req, User user) {
        HttpSession session = req.getSession(true);
        // Set both Bradley's SESSION_USER_ID and Andrew's userId/email/role so all servlets share one session
        session.setAttribute(SESSION_USER_ID, user.getUserId());
        session.setAttribute("userId", user.getUserId());
        session.setAttribute("email", user.getEmail());
        session.setAttribute("role", user.getRoleName());
        session.removeAttribute("pending2faCode");
        session.removeAttribute("pending2faUserId");
    }

    private String newCode() {
        return String.valueOf(1000 + random.nextInt(9000));
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
        return getRawString(obj, key).trim();
    }

    private String getRawString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
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
