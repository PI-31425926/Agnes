package com.bilibili.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesUtil {
    @Value("${aes.secret}")
    private String secretKey;

    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final String ECB_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * 解密：优先尝试 GCM 格式，失败后兼容 ECB 旧格式。
     * 如果使用了 ECB 解密，调用方应触发迁移（返回 true 表示是旧格式）。
     */
    public DecryptResult decrypt(String encryptedText) throws Exception {
        // 1. 尝试 GCM 格式（IV + ciphertext）
        try {
            return new DecryptResult(decryptGcm(encryptedText), false);
        } catch (Exception gcmEx) {
            // 2. 兼容 ECB 旧格式
            try {
                String plaintext = decryptEcb(encryptedText);
                return new DecryptResult(plaintext, true);
            } catch (Exception ecbEx) {
                // 两者都失败，抛出 GCM 异常（更准确）
                throw gcmEx;
            }
        }
    }

    /** convenience: just decrypt, ignore legacy flag */
    public String decryptLegacy(String encryptedText) throws Exception {
        return decrypt(encryptedText).plaintext();
    }

    private String decryptGcm(String encryptedText) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedText);

        // GCM 密文至少需要 IV(12) + Tag(16) = 28 字节
        if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH / 8) {
            throw new IllegalArgumentException("Not a GCM ciphertext");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);

        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        return new String(cipher.doFinal(ciphertext));
    }

    private String decryptEcb(String encryptedText) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedText);

        // ECB 密文长度必须是 16 的倍数
        if (decoded.length % 16 != 0) {
            throw new IllegalArgumentException("Not an ECB ciphertext");
        }

        Cipher cipher = Cipher.getInstance(ECB_TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);

        return new String(cipher.doFinal(decoded));
    }

    public String encrypt(String plainText) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        byte[] encrypted = cipher.doFinal(plainText.getBytes());

        // Prepend IV to ciphertext
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /** Inner class to hold decrypt result with legacy flag */
    public record DecryptResult(String plaintext, boolean legacy) {}
}
