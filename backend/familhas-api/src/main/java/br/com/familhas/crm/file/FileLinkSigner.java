package br.com.familhas.crm.file;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Links de download de curta duração: HMAC-SHA256 de {@code id + "\n" + expiraEm}. Servem para {@code <img>} e
 * PDF.js, que não mandam o header Authorization. Quem tem o link baixa até ele vencer: por isso o prazo é curto
 * e cada emissão vai para a auditoria.
 */
public class FileLinkSigner {

    private static final Logger log = LoggerFactory.getLogger(FileLinkSigner.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Clock clock;

    public FileLinkSigner(String configuredSecret, Clock clock) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("STORAGE_LINK_SECRET vazio: usando segredo aleatorio; links de arquivo caem a cada restart");
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        } else {
            if (configuredSecret.length() < 32) {
                throw new IllegalStateException("STORAGE_LINK_SECRET precisa de pelo menos 32 caracteres");
            }
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.clock = clock;
    }

    public String sign(UUID id, long expiresAtEpochSecond) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(id + "\n" + expiresAtEpochSecond));
    }

    /** Assinatura confere (comparação em tempo constante) e o link ainda não venceu. */
    public boolean isValid(UUID id, long expiresAtEpochSecond, String signature) {
        if (signature == null || Instant.ofEpochSecond(expiresAtEpochSecond).isBefore(clock.instant())) {
            return false;
        }
        byte[] expected = mac(id + "\n" + expiresAtEpochSecond);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, given);
    }

    private byte[] mac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
