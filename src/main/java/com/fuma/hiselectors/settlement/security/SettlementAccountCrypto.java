package com.fuma.hiselectors.settlement.security;

import com.fuma.hiselectors.common.security.AesGcmCrypto;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SettlementAccountCrypto {

    private static final String VERSION_PREFIX = "enc:v1:";

    private final AesGcmCrypto crypto;

    public SettlementAccountCrypto(
            @Value("${settlement.account-encryption-key:}") String encodedKey) {
        this.crypto = new AesGcmCrypto(encodedKey);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            return VERSION_PREFIX + crypto.encrypt(plainText);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SETTLEMENT_ENCRYPTION_FAILED);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        if (!encryptedText.startsWith(VERSION_PREFIX)) {
            throw new BusinessException(ErrorCode.SETTLEMENT_DECRYPTION_FAILED);
        }
        try {
            return crypto.decrypt(encryptedText.substring(VERSION_PREFIX.length()));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SETTLEMENT_DECRYPTION_FAILED);
        }
    }
}
