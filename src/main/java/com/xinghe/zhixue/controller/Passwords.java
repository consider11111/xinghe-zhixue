package com.xinghe.zhixue.controller;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Passwords {
    private Passwords() {}

    static String encode(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2$120000$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt, 120000));
    }

    static boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        // Existing demo accounts remain usable until their password is reset.
        if (!stored.startsWith("pbkdf2$")) return MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        try {
            String[] parts = stored.split("\\$");
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[3]),
                    derive(password, Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1])));
        } catch (RuntimeException e) { return false; }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded();
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
