package com.fongmi.android.tv.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Prefers;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keeps the WebDAV password encrypted with a key that never leaves Android Keystore. */
public final class WebDAVCredentialStore {

    private static final String KEY_ALIAS = "xybox_webdav_password_v1";
    private static final String ENCRYPTED_PASSWORD = "webdav_password_encrypted_v1";
    private static final String LEGACY_PASSWORD = "webdav_password";

    private WebDAVCredentialStore() {
    }

    public static String getPassword() {
        String encrypted = Prefers.getString(ENCRYPTED_PASSWORD, "");
        if (!TextUtils.isEmpty(encrypted)) {
            try {
                return decrypt(encrypted);
            } catch (Exception e) {
                Logger.e("WebDAV: 无法解密已保存的密码: " + e.getMessage());
            }
        }

        String legacy = Prefers.getString(LEGACY_PASSWORD, "");
        if (!TextUtils.isEmpty(legacy)) {
            putPassword(legacy);
            return legacy;
        }
        return "";
    }

    public static void putPassword(String password) {
        if (TextUtils.isEmpty(password)) {
            Prefers.put(ENCRYPTED_PASSWORD, "");
            Prefers.put(LEGACY_PASSWORD, "");
            return;
        }
        try {
            Prefers.put(ENCRYPTED_PASSWORD, encrypt(password));
            Prefers.put(LEGACY_PASSWORD, "");
        } catch (Exception e) {
            // Never discard a credential merely because a vendor Keystore is unavailable.
            Logger.e("WebDAV: 加密密码失败，暂时保留兼容存储: " + e.getMessage());
            Prefers.put(LEGACY_PASSWORD, password);
        }
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] data = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        String[] parts = value.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("密码数据格式无效");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] data = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
