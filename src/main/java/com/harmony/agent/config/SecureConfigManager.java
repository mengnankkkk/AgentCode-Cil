package com.harmony.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Secure configuration manager for sensitive data (API keys)
 * Uses AES-256-GCM encryption with PBKDF2 key derivation
 */
public class SecureConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(SecureConfigManager.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";

    private final Path secureConfigFile;
    private final SecureRandom secureRandom;

    public SecureConfigManager(Path secureConfigFile) {
        this.secureConfigFile = secureConfigFile;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Save API key with encryption
     */
    public void saveApiKey(String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be empty");
        }

        try {
            // Generate salt
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);

            // Derive encryption key from system properties
            SecretKey key = deriveKey(getSystemSecret(), salt);

            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] plaintext = apiKey.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintext);

            // Combine: salt + iv + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            // Write to file
            Files.write(secureConfigFile, byteBuffer.array());

            // Set file permissions (Unix-like systems only)
            setSecureFilePermissions();

            logger.info("API key saved securely");

        } catch (Exception e) {
            logger.error("Failed to save API key", e);
            throw new Exception("Failed to save API key securely: " + e.getMessage(), e);
        }
    }

    /**
     * Load API key with decryption
     */
    public String loadApiKey() throws Exception {
        if (!Files.exists(secureConfigFile)) {
            logger.debug("Secure config file does not exist");
            return null;
        }

        try {
            // Read encrypted data
            byte[] encryptedData = Files.readAllBytes(secureConfigFile);

            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);

            // Extract salt
            byte[] salt = new byte[16];
            byteBuffer.get(salt);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            // Extract ciphertext
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Derive decryption key
            SecretKey key = deriveKey(getSystemSecret(), salt);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);

            logger.info("API key loaded successfully");
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logger.error("Failed to load API key", e);
            throw new Exception("Failed to load API key: " + e.getMessage(), e);
        }
    }

    /**
     * Derive encryption key using PBKDF2
     */
    private SecretKey deriveKey(String password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);

        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * Get system-specific secret for key derivation
     * Uses combination of system properties for machine-specific encryption
     */
    private String getSystemSecret() {
        StringBuilder secret = new StringBuilder();

        secret.append(System.getProperty("user.name", "default"));
        secret.append(System.getProperty("user.home", "home"));
        secret.append(System.getProperty("os.name", "os"));

        // Add a constant salt to make it harder to brute force
        secret.append("HarmonySafeAgent-v1.0");

        return secret.toString();
    }

    /**
     * Set secure file permissions (Unix-like systems)
     */
    private void setSecureFilePermissions() {
        try {
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                // On Unix-like systems, restrict to owner read/write only
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(secureConfigFile,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                logger.debug("Set secure file permissions: rw-------");
            }
        } catch (IOException e) {
            logger.warn("Failed to set file permissions", e);
        } catch (UnsupportedOperationException e) {
            logger.debug("POSIX file permissions not supported on this system");
        }
    }

    /**
     * Delete secure config file
     */
    public boolean delete() {
        try {
            if (Files.exists(secureConfigFile)) {
                Files.delete(secureConfigFile);
                logger.info("Secure config file deleted");
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.error("Failed to delete secure config file", e);
            return false;
        }
    }

    /**
     * Check if secure config exists
     */
    public boolean exists() {
        return Files.exists(secureConfigFile);
    }
}
