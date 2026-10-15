package com.cityrecreation.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central JDBC connection factory for the City Recreational Spaces Navigator.
 *
 * Connection settings can be overridden with environment variables so the same
 * build runs against different machines without code edits:
 *   DB_URL, DB_USER, DB_PASSWORD
 * Defaults target a local MySQL 8 instance with the `city_recreation` schema.
 */
public class DatabaseUtil {

    private static final String URL =
            getenv("DB_URL",
                   "jdbc:mysql://localhost:3306/city_recreation"
                 + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String USER = getenv("DB_USER", "root");
    private static final String PASSWORD = getenv("DB_PASSWORD", "");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found on the classpath.", e);
        }
    }

    private static String getenv(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private DatabaseUtil() {
        // utility class; no instances
    }
}
