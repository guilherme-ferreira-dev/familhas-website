package br.com.familhas.crm.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FileLinkSignerTests {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final FileLinkSigner signer = new FileLinkSigner(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID id = UUID.randomUUID();
    private final long expires = NOW.plusSeconds(300).getEpochSecond();

    @Test
    void validSignatureWithinTheDeadline() {
        assertThat(signer.isValid(id, expires, signer.sign(id, expires))).isTrue();
    }

    @Test
    void tamperedOrExpiredLinksAreRejected() {
        String signature = signer.sign(id, expires);
        assertThat(signer.isValid(UUID.randomUUID(), expires, signature)).as("outro arquivo").isFalse();
        assertThat(signer.isValid(id, expires + 3600, signature)).as("prazo esticado").isFalse();
        assertThat(signer.isValid(id, expires, signature.substring(1))).as("assinatura cortada").isFalse();
        assertThat(signer.isValid(id, expires, "!!!")).as("base64 invalido").isFalse();
        assertThat(signer.isValid(id, expires, null)).isFalse();
        long past = NOW.minusSeconds(1).getEpochSecond();
        assertThat(signer.isValid(id, past, signer.sign(id, past))).as("vencido").isFalse();
    }

    @Test
    void anotherSecretDoesNotValidate() {
        FileLinkSigner other = new FileLinkSigner("ffffffffffffffffffffffffffffffff", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(other.isValid(id, expires, signer.sign(id, expires))).isFalse();
    }

    @Test
    void shortSecretIsRefusedAndBlankSecretIsRandom() {
        assertThatThrownBy(() -> new FileLinkSigner("curto", Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        FileLinkSigner a = new FileLinkSigner("", Clock.fixed(NOW, ZoneOffset.UTC));
        FileLinkSigner b = new FileLinkSigner("", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(b.isValid(id, expires, a.sign(id, expires))).isFalse();
    }
}
