package com.cityrecreation.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing (PBKDF2WithHmacSHA256) and email normalization utilities.
 * Includes classroom fixture compatibility for seeded "hashedpwd*" values in schema.sql.
 */
public final class PasswordUtil {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String value = email.trim().toLowerCase();
        return value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? value : null;
    }

    public static String hashPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        try {
            byte[] salt = new byte[SALT_LENGTH];
            RANDOM.nextBytes(salt);
            byte[] derived = derive(password, salt, ITERATIONS);
            return ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    public static boolean passwordMatches(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        try {
            String[] parts = stored.split("\\$", -1);
            // schema.sql seeds classroom fixture passwords like "hashedpwd1"
            if (parts.length != 3) {
                return stored.startsWith("hashedpwd") && stored.equals(password);
            }
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = derive(password, salt, iterations);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}

