package com.atlas.personalai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String ALIAS = "atlas_core_v1";
    private static final String PREF = "atlas_secure_core";
    private SecureStore() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            kg.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }

    public static void set(Context c, String name, String value) {
        try {
            if (value == null || value.trim().isEmpty()) return;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] enc = cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(enc, Base64.NO_WRAP);
            p(c).edit().putString(name, packed).apply();
        } catch (Exception e) {
            throw new RuntimeException("Secure storage failed", e);
        }
    }

    public static String get(Context c, String name) {
        try {
            String packed = p(c).getString(name, "");
            if (packed == null || packed.isEmpty()) return "";
            String[] parts = packed.split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] enc = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean has(Context c, String name) {
        return !get(c, name).isEmpty();
    }

    public static void clearCoreKeys(Context c) {
        p(c).edit().clear().apply();
    }
}
