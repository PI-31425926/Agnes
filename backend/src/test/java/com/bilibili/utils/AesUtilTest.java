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
        // 16-byte key for AES-128
        AesUtil util = createUtil("test-secret-key-1234");

        String original = "test-api-key-12345";
        String encrypted = util.encrypt(original);
        assertNotEquals(original, encrypted);

        String decrypted = util.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertext() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");

        String original = "same-plaintext";
        String enc1 = util.encrypt(original);
        String enc2 = util.encrypt(original);

        // GCM with random IV produces different ciphertext each time
        assertNotEquals(enc1, enc2);
    }

    @Test
    void decryptWithWrongKeyThrowsException() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");
        String encrypted = util.encrypt("test");

        AesUtil wrongUtil = createUtil("abcdefghijklmnopqrstuvwxyz"); // 26 bytes, invalid
        // Use a different valid-length key instead
        AesUtil wrongUtil2 = createUtil("abcdefghijklmnop"); // 16 bytes, different
        assertThrows(Exception.class, () -> wrongUtil2.decrypt(encrypted));
    }

    @Test
    void emptyStringEncryptDecrypt() throws Exception {
        AesUtil util = createUtil("test-secret-key-1234");

        String encrypted = util.encrypt("");
        String decrypted = util.decrypt(encrypted);
        assertEquals("", decrypted);
    }
}
