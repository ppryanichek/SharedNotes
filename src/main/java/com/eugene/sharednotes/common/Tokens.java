package com.eugene.sharednotes.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;


public final class Tokens {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Tokens() {}

    /** Генерирует криптостойкий случайный токен (32 байта, Base64URL без padding). */
    public static String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Хеширует токены алгоритмом SHA-256.
     * <p>
     * Хеш детерминирован — это позволяет искать токен в БД по хешу,
     * не храня его значение.
     */
    public static String hash(String token) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        }catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
