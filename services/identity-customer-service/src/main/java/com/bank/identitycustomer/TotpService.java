package com.bank.identitycustomer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/** RFC 6238 TOTP with encrypted-at-rest secrets and a small clock-skew window. */
@Service
public class TotpService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();
    private final byte[] encryptionKey;

    public TotpService(@Value("${app.security.mfa-encryption-key}") String secret) {
        try {
            this.encryptionKey = MessageDigest.getInstance("SHA-256").digest((secret + ":mfa-secret-encryption").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("Unable to initialize MFA encryption", e); }
    }

    public String newSecret() {
        byte[] bytes = new byte[20]; random.nextBytes(bytes); return base32(bytes);
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) { throw new IllegalStateException("Unable to protect MFA secret", e); }
    }

    public String decrypt(String encoded) {
        try {
            String[] parts = encoded.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("Unsupported MFA secret format");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to read MFA secret", e); }
    }

    public boolean verify(String secret, String code) {
        return matchingStep(secret, code) != null;
    }

    public Long matchingStep(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return null;
        long step = Instant.now().getEpochSecond() / 30;
        byte[] supplied = code.getBytes(StandardCharsets.US_ASCII);
        for (long drift = -1; drift <= 1; drift++) {
            if (MessageDigest.isEqual(supplied, generate(secret, step + drift).getBytes(StandardCharsets.US_ASCII))) return step + drift;
        }
        return null;
    }

    public String provisioningUri(String secret, String username) {
        String label = java.net.URLEncoder.encode("MoneyPal:" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=MoneyPal&algorithm=SHA1&digits=6&period=30";
    }

    private String generate(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16) | ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (Exception e) { throw new IllegalStateException("Unable to verify MFA code", e); }
    }

    private String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte value : bytes) { buffer = (buffer << 8) | (value & 0xff); bits += 8; while (bits >= 5) { out.append(BASE32.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; } }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private byte[] unbase32(String value) {
        String normalized = value.toUpperCase(java.util.Locale.ROOT).replace("=", "");
        byte[] out = new byte[normalized.length() * 5 / 8]; int buffer = 0, bits = 0, index = 0;
        for (char c : normalized.toCharArray()) { int digit = BASE32.indexOf(c); if (digit < 0) throw new IllegalArgumentException("Invalid MFA secret"); buffer = (buffer << 5) | digit; bits += 5; if (bits >= 8) { out[index++] = (byte) (buffer >> (bits - 8)); bits -= 8; } }
        return Arrays.copyOf(out, index);
    }
}
