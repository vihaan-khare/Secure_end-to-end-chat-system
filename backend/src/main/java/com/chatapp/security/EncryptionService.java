package com.chatapp.security;

import com.chatapp.util.LoggerService;
import io.github.cdimascio.dotenv.Dotenv;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 Encryption Service for securing message content.
 * Demonstrates abstraction — complex encryption logic is hidden behind
 * simple encrypt()/decrypt() methods.
 *
 * Uses AES/CBC/PKCS5Padding with a random IV prepended to ciphertext.
 */
public class EncryptionService {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private final SecretKeySpec secretKey;
    private final LoggerService logger = LoggerService.getInstance();

    /**
     * Initializes the encryption service with the key from .env file.
     */
    public EncryptionService() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("ENCRYPTION_KEY", "MySecretKey12345MySecretKey12345");

        // Ensure key is exactly 32 bytes for AES-256
        byte[] keyBytes = new byte[32];
        byte[] providedBytes = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(providedBytes, 0, keyBytes, 0, Math.min(providedBytes.length, 32));

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        logger.info("EncryptionService initialized with AES-256");
    }

    /**
     * Encrypts a plaintext string using AES-256-CBC.
     * The IV is randomly generated and prepended to the ciphertext.
     *
     * @param plaintext The text to encrypt
     * @return Base64-encoded string containing IV + ciphertext
     */
    public String encrypt(String plaintext) {
        try {
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            return plaintext; // Fallback to plaintext on error
        }
    }

    /**
     * Decrypts an AES-256-CBC encrypted string.
     * Extracts the IV from the first 16 bytes of the decoded data.
     *
     * @param ciphertext Base64-encoded IV + ciphertext
     * @return Decrypted plaintext string
     */
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Extract ciphertext
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            return ciphertext; // Return as-is if decryption fails
        }
    }
}
