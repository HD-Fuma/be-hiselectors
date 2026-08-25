package com.fuma.hiselectors.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCrypto(String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String plainText) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, iv)
                .doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array());
    }

    public String decrypt(String encryptedText) throws GeneralSecurityException {
        byte[] combined = Base64.getDecoder().decode(encryptedText);
        if (combined.length <= IV_LENGTH) {
            throw new IllegalArgumentException("invalid ciphertext");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
        return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new IllegalArgumentException("AES-GCM key must be 32 bytes");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher;
    }
}
