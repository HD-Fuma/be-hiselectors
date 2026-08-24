package com.fuma.hiselectors.settlement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SettlementAccountCryptoTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsSettlementInformation() {
        SettlementAccountCrypto crypto = new SettlementAccountCrypto(KEY);

        String encrypted = crypto.encrypt("900101-1234567");

        assertThat(encrypted).startsWith("enc:v1:").doesNotContain("900101-1234567");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("900101-1234567");
    }

    @Test
    void usesRandomIvForEachEncryption() {
        SettlementAccountCrypto crypto = new SettlementAccountCrypto(KEY);

        assertThat(crypto.encrypt("123-456"))
                .isNotEqualTo(crypto.encrypt("123-456"));
    }

    @Test
    void rejectsPlaintextAndTamperedCiphertext() {
        SettlementAccountCrypto crypto = new SettlementAccountCrypto(KEY);
        String encrypted = crypto.encrypt("123-456");
        char replacement = encrypted.endsWith("A") ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + replacement;

        assertDecryptionFailure(() -> crypto.decrypt("123-456"));
        assertDecryptionFailure(() -> crypto.decrypt(tampered));
    }

    @Test
    void rejectsInvalidEncryptionKey() {
        SettlementAccountCrypto crypto = new SettlementAccountCrypto(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(() -> crypto.encrypt("123-456"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SETTLEMENT_ENCRYPTION_FAILED));
    }

    private void assertDecryptionFailure(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SETTLEMENT_DECRYPTION_FAILED));
    }
}
