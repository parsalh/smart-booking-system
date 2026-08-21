package com.hua.smartbooking.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class EncryptionConfig {

    @Value("${app.security.encryption-key}")
    private String key;

    public static byte[] SECRET_KEY;

    @PostConstruct
    public void init() {
        if (key == null) {
            throw new IllegalArgumentException("Encryption key is missing from configuration");
        }
        byte[] decoded = Base64.getDecoder().decode(key);
        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                    "Invalid key length: expected 32 bytes (AES-256) after Base64 decoding, got " + decoded.length);
        }
        SECRET_KEY = decoded;
    }

}
