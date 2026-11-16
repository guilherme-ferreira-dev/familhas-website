package br.com.familhas.crm.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class BackupCipherTests {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomIvAndDetectsTampering() {
        BackupCipher cipher = new BackupCipher(KEY);
        byte[] plain = "{\"cpf\":\"52998224725\"}".getBytes(StandardCharsets.UTF_8);
        byte[] a = cipher.encrypt(plain);
        byte[] b = cipher.encrypt(plain);
        assertThat(a).isNotEqualTo(b);
        assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain("52998224725");
        assertThat(cipher.decrypt(a)).isEqualTo(plain);
        a[a.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(a)).isInstanceOf(IllegalStateException.class);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        assertThatThrownBy(() -> new BackupCipher(Base64.getEncoder().encodeToString(otherKey)).decrypt(b))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustHave32Bytes() {
        assertThatThrownBy(() -> new BackupCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
