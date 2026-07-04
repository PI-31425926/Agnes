package com.bilibili.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class AesUtilTest {

    private AesUtil createUtil(String key) throws Exception {
        AesUtil util = new AesUtil();
        Field field = AesUtil.class.getDeclaredField("secretKey");
        field.setAccessible(true);
        field.set(util, key);
        return util;
    }

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");

        String original = "test-api-key-12345";
        String encrypted = util.encrypt(original);
        assertNotEquals(original, encrypted);

        String decrypted = util.decryptLegacy(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertext() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");

        String original = "same-plaintext";
        String enc1 = util.encrypt(original);
        String enc2 = util.encrypt(original);

        assertNotEquals(enc1, enc2);
    }

    @Test
    void decryptDetectsLegacyFormat() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");

        String encrypted = util.encrypt("test");
        AesUtil.DecryptResult result = util.decrypt(encrypted);
        assertFalse(result.legacy());
        assertEquals("test", result.plaintext());
    }

    @Test
    void decryptWithWrongKeyThrowsException() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");
        String encrypted = util.encrypt("test");

        AesUtil wrongUtil = createUtil("abcdefghijklmnop");
        assertThrows(Exception.class, () -> wrongUtil.decrypt(encrypted));
    }

    @Test
    void emptyStringEncryptDecrypt() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");

        String encrypted = util.encrypt("");
        String decrypted = util.decryptLegacy(encrypted);
        assertEquals("", decrypted);
    }
}
