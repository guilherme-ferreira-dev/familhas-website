package br.com.familhas.crm.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StoredFileTypeTests {

    static final byte[] PDF = "%PDF-1.7\n%....".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R' };
    static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0 };
    static final byte[] WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] MP4 = "\u0000\u0000\u0000\u0018ftypmp42".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] EXE = "MZ\u0090\u0000\u0003\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] HTML = "<!doctype html><script>".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    void detectsAcceptedTypesByTheirFirstBytes() {
        assertThat(StoredFileType.detect(PDF, PDF.length)).contains(StoredFileType.PDF);
        assertThat(StoredFileType.detect(PNG, PNG.length)).contains(StoredFileType.PNG);
        assertThat(StoredFileType.detect(JPEG, JPEG.length)).contains(StoredFileType.JPEG);
        assertThat(StoredFileType.detect(WEBP, WEBP.length)).contains(StoredFileType.WEBP);
        assertThat(StoredFileType.detect(MP4, MP4.length)).contains(StoredFileType.MP4);
    }

    @Test
    void rejectsExecutablesScriptsAndTruncatedHeaders() {
        assertThat(StoredFileType.detect(EXE, EXE.length)).isEmpty();
        assertThat(StoredFileType.detect(SVG, SVG.length)).isEmpty();
        assertThat(StoredFileType.detect(HTML, HTML.length)).isEmpty();
        assertThat(StoredFileType.detect(PNG, 4)).isEmpty();
        assertThat(StoredFileType.detect(new byte[0], 0)).isEmpty();
        assertThat(StoredFileType.detect("RIFF\u0000\u0000\u0000\u0000WAVE".getBytes(StandardCharsets.ISO_8859_1), 12))
                .isEmpty();
    }
}
