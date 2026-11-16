package br.com.familhas.crm.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalFileStorageTests {

    @TempDir Path root;

    @Test
    void storesReadsAndDeletesByKey() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        String key = UUID.randomUUID().toString();
        storage.put(key, new ByteArrayInputStream("conteudo".getBytes()));
        try (InputStream in = storage.open(key)) {
            assertThat(new String(in.readAllBytes())).isEqualTo("conteudo");
        }
        storage.delete(key);
        assertThat(Files.exists(storage.resolve(key))).isFalse();
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty(); // sem .part esquecido
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "../../etc/passwd", "..", "a/b", "/etc/passwd", "voucher.pdf", "",
            "00000000-0000-0000-0000-000000000000/../x", "0000000-0000-0000-0000-000000000000" })
    void rejectsAnyKeyThatIsNotAUuid(String key) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.put(key, InputStream.nullInputStream()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
