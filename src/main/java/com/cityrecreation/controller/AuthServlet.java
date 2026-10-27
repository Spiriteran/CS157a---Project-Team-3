package com.cityrecreation.controller;

import com.cityrecreation.util.DatabaseUtil;
import com.google.gson.Gson;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

/** Handles the small account surface used by the public HTML pages. */
@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        Map<String, String> input = readBody(req);
        String action = input.get("action");
        try {
            if ("login".equals(action)) {
                login(req, resp, input);
            } else if ("register".equals(action)) {
                register(req, resp, input);
            } else {
                write(resp, 400, false, "Choose a valid account action.");
            }
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
            write(resp, 400, false, "Enter your email address and password.");
            return;
        }

        String sql = "SELECT u.user_id, u.email, u.password_hash, r.role_name "
                + "FROM `USER` u JOIN ROLE r ON r.role_id = u.role_id WHERE u.email = ?";
        try (Connection conn = DatabaseUtil.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !passwordMatches(password, rs.getString("password_hash"))) {
                    write(resp, 401, false, "That email and password do not match.");
                    return;
                }
                req.getSession(true).setAttribute("userId", rs.getInt("user_id"));
                req.getSession().setAttribute("email", rs.getString("email"));
                req.getSession().setAttribute("role", rs.getString("role_name"));
                write(resp, 200, true, "Signed in successfully.");
            }
        }
    }

    private void register(HttpServletRequest req, HttpServletResponse resp, Map<String, String> input)
            throws SQLException, IOException {
        String email = normalizeEmail(input.get("email"));
        String password = input.get("password");
        String confirmation = input.get("confirmPassword");
        String role = input.get("role");
        if (email == null || password == null || password.length() < 8) {
            write(resp, 400, false, "Use a valid email and a password with at least 8 characters.");
            return;
        }
        if (!password.equals(confirmation)) {
            write(resp, 400, false, "Your passwords do not match.");
            return;
        }
        if (!"GENERAL_USER".equals(role) && !"RESERVATION_USER".equals(role)) {
            write(resp, 400, false, "Choose a valid account type.");
            return;
        }
