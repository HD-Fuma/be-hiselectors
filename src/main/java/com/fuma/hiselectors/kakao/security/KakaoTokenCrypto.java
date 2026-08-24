package com.fuma.hiselectors.kakao.security;

import com.fuma.hiselectors.common.security.AesGcmCrypto;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.security.GeneralSecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KakaoTokenCrypto {

    private final AesGcmCrypto crypto;

    public KakaoTokenCrypto(@Value("${kakao.token-encryption-key:}") String encodedKey) {
        this.crypto = new AesGcmCrypto(encodedKey);
    }

    public String encrypt(String plainText) {
        try {
            return crypto.encrypt(plainText);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.KAKAO_CONFIGURATION_INVALID);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            return crypto.decrypt(encryptedText);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.KAKAO_TOKEN_DECRYPTION_FAILED);
        }
    }

}
