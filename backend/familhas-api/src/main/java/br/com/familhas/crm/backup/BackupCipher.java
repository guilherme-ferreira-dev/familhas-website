package br.com.familhas.crm.backup;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM para os backups guardados: IV aleatório de 12 bytes no início do arquivo e tag de 128 bits.
 * Arquivo alterado no storage não decifra (a tag falha). A chave vem só de variável de ambiente.
 */
public class BackupCipher {

    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public BackupCipher(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key.strip());
        if (bytes.length != 32) {
            throw new IllegalStateException("BACKUP_ENCRYPTION_KEY precisa de 32 bytes em Base64");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] body = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + body.length).put(iv).put(body).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, data, 0, IV_BYTES));
            return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Backup guardado nao decifra (chave trocada ou arquivo alterado)", e);
        }
    }
}
