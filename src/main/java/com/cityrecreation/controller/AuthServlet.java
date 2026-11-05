package com.cityrecreation.controller;

import com.cityrecreation.util.DatabaseUtil;
import com.google.gson.Gson;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authentication plus the intentionally fake email-verification/2FA flows. */
@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private final Gson gson = new Gson();
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        Map<String, String> input = readBody(req);
        try {
            String action = input.get("action");
            if ("login".equals(action)) login(req, resp, input);
            else if ("verify_email".equals(action)) verifyEmail(req, resp, input);
            else if ("verify_2fa".equals(action)) verifyTwoFactor(req, resp, input);
            else if ("register".equals(action)) register(req, resp, input);
            else if ("logout".equals(action)) logout(req, resp);
            else write(resp, 400, false, "Choose a valid account action.");
        } catch (SQLException e) {
            e.printStackTrace();
            write(resp, 500, false, "The account service is unavailable right now.");
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp, Map<String, String> input)
            throws SQLException, IOException {
        String email = normalizeEmail(input.get("email"));
        String password = input.get("password");
        if (email == null || password == null || password.isEmpty()) {
            write(resp, 400, false, "Enter your email address and password."); return;
        }
        String sql = "SELECT u.user_id, u.email, u.password_hash, u.email_verified, "
                + "u.twofa_enabled, r.role_name FROM `USER` u JOIN ROLE r "
                + "ON r.role_id = u.role_id WHERE u.email = ?";
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !passwordMatches(password, rs.getString("password_hash"))) {
                    write(resp, 401, false, "That email and password do not match."); return;
                }
                if (!rs.getBoolean("email_verified")) {
                    write(resp, 403, false, "Verify your email before signing in."); return;
                }
                if (rs.getBoolean("twofa_enabled")) {
                    HttpSession session = req.getSession(true);
                    String code = newCode();
                    session.setAttribute("pending2faUserId", rs.getInt("user_id"));
                    session.setAttribute("pending2faCode", code);
                    session.setAttribute("pending2faEmail", rs.getString("email"));
                    session.setAttribute("pending2faRole", rs.getString("role_name"));
                    Map<String, Object> result = base(true, "A demo 2FA code was sent to your phone.");
                    result.put("requires2fa", true); result.put("code", code);
                    write(resp, 200, result); return;
                }
                establishSession(req, rs.getInt("user_id"), rs.getString("email"), rs.getString("role_name"));
                write(resp, 200, true, "Signed in successfully.");
            }
        }
    }

    private void verifyEmail(HttpServletRequest req, HttpServletResponse resp, Map<String, String> input)
            throws SQLException, IOException {
        HttpSession session = req.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute("pendingEmailCode");
        String email = normalizeEmail(input.get("email"));
        if (expected == null || !expected.equals(input.get("code")) || email == null
                || !email.equals(session.getAttribute("pendingEmail"))) {
            write(resp, 400, false, "That email verification code is not valid."); return;
        }
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE `USER` SET email_verified = TRUE WHERE email = ?")) {
            ps.setString(1, email); ps.executeUpdate();
        }
        session.removeAttribute("pendingEmailCode");
        write(resp, 200, true, "Email verified. You can now sign in.");
    }

    private void verifyTwoFactor(HttpServletRequest req, HttpServletResponse resp, Map<String, String> input)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || !String.valueOf(session.getAttribute("pending2faCode")).equals(input.get("code"))) {
            write(resp, 400, false, "That 2FA code is not valid."); return;
        }
        establishSession(req, (Integer) session.getAttribute("pending2faUserId"),
                (String) session.getAttribute("pending2faEmail"),
                (String) session.getAttribute("pending2faRole"));
        write(resp, 200, true, "Two-factor verification complete.");
    }

    private void register(HttpServletRequest req, HttpServletResponse resp, Map<String, String> input)
            throws SQLException, IOException {
        String email = normalizeEmail(input.get("email"));
        String password = input.get("password");
        String confirmation = input.get("confirmPassword");
        String role = input.get("role");
        if (email == null || password == null || password.length() < 8) {
            write(resp, 400, false, "Use a valid email and a password with at least 8 characters."); return;
        }
        if (!password.equals(confirmation)) { write(resp, 400, false, "Your passwords do not match."); return; }
        if (!"GENERAL_USER".equals(role) && !"RESERVATION_USER".equals(role)) {
            write(resp, 400, false, "Choose a valid account type."); return;
        }
        String sql = "INSERT INTO `USER` (role_id, email, password_hash, email_verified, twofa_enabled) "
                + "SELECT role_id, ?, ?, FALSE, role_name = 'RESERVATION_USER' FROM ROLE WHERE role_name = ?";
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email); ps.setString(2, hashPassword(password)); ps.setString(3, role);
            if (ps.executeUpdate() != 1) { write(resp, 500, false, "The account could not be created."); return; }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { write(resp, 409, false, "An account with that email already exists."); return; }
            throw e;
        }
        HttpSession session = req.getSession(true);
        String code = newCode();
        session.setAttribute("pendingEmail", email);
        session.setAttribute("pendingEmailCode", code);
        Map<String, Object> result = base(true, "A demo verification email was sent to " + email + ".");
        result.put("requiresEmailVerification", true); result.put("email", email); result.put("code", code);
        write(resp, 201, result);
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false); if (session != null) session.invalidate();
        write(resp, 200, true, "Signed out.");
    }

    private void establishSession(HttpServletRequest req, int userId, String email, String role) {
        HttpSession session = req.getSession(true);
        session.setAttribute("userId", userId); session.setAttribute("email", email); session.setAttribute("role", role);
        session.removeAttribute("pending2faCode"); session.removeAttribute("pending2faUserId");
        session.removeAttribute("pending2faEmail"); session.removeAttribute("pending2faRole");
    }

    private String newCode() { return String.valueOf(1000 + random.nextInt(9000)); }

    private Map<String, String> readBody(HttpServletRequest req) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        String body = req.getReader().lines().reduce("", (a, b) -> a + b);
        if (body.trim().startsWith("{")) {
            Map<?, ?> parsed = gson.fromJson(body, Map.class);
            if (parsed != null) for (Map.Entry<?, ?> e : parsed.entrySet())
                values.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return values;
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null; String value = email.trim().toLowerCase();
        return value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? value : null;
    }

    private static String hashPassword(String password) {
        try { byte[] salt = new byte[SALT_LENGTH]; new SecureRandom().nextBytes(salt);
            return ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
        } catch (Exception e) { throw new IllegalStateException("Unable to hash password", e); }
    }

    private static boolean passwordMatches(String password, String stored) {
        try { String[] p = stored == null ? new String[0] : stored.split("\\$", -1);
            // schema.sql ships mock accounts with values such as "hashedpwd4".
            // This compatibility path is for the classroom fixture only; new accounts use PBKDF2 below.
            if (p.length != 3) return stored != null && stored.startsWith("hashedpwd") && stored.equals(password);
            return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(p[1]), Integer.parseInt(p[0])),
                    Base64.getDecoder().decode(p[2]));
        } catch (Exception e) { return false; }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private Map<String, Object> base(boolean ok, String message) { Map<String, Object> m = new LinkedHashMap<>(); m.put("ok", ok); m.put("message", message); return m; }
    private void write(HttpServletResponse resp, int status, boolean ok, String message) throws IOException { write(resp, status, base(ok, message)); }
    private void write(HttpServletResponse resp, int status, Map<String, Object> payload) throws IOException { resp.setStatus(status); resp.getWriter().write(gson.toJson(payload)); }
}
