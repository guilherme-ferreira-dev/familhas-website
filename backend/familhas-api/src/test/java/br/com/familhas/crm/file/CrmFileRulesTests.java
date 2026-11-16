package br.com.familhas.crm.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.familhas.api.BusinessException;

class CrmFileRulesTests {

    @Test
    void typeMustBeAcceptedByTheCategory() {
        assertThat(CrmFileService.checkType(FileCategory.VOUCHER_SOURCE, "application/pdf", StoredFileTypeTests.PDF))
                .isEqualTo(StoredFileType.PDF);
        assertThat(CrmFileService.checkType(FileCategory.INSTAGRAM_MEDIA, "video/mp4", StoredFileTypeTests.MP4))
                .isEqualTo(StoredFileType.MP4);
        assertCode(() -> CrmFileService.checkType(FileCategory.VOUCHER_SOURCE, "video/mp4", StoredFileTypeTests.MP4),
                "FILE_TYPE_NOT_ALLOWED");
        assertCode(() -> CrmFileService.checkType(FileCategory.DOCUMENT, "image/svg+xml", StoredFileTypeTests.SVG),
                "FILE_TYPE_NOT_ALLOWED");
        assertCode(() -> CrmFileService.checkType(FileCategory.DOCUMENT, null, StoredFileTypeTests.EXE),
                "FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void declaredTypeThatContradictsTheContentIsRejected() {
        assertCode(() -> CrmFileService.checkType(FileCategory.DOCUMENT, "image/png", StoredFileTypeTests.PDF),
                "FILE_TYPE_MISMATCH");
        // Navegador sem tipo, ou tipo genérico: vale o conteúdo.
        assertThat(CrmFileService.checkType(FileCategory.DOCUMENT, "application/octet-stream", StoredFileTypeTests.PNG))
                .isEqualTo(StoredFileType.PNG);
        assertThat(CrmFileService.checkType(FileCategory.DOCUMENT, null, StoredFileTypeTests.JPEG))
                .isEqualTo(StoredFileType.JPEG);
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "voucher hotel.pdf             | PDF  | voucher hotel.pdf",
            "../../etc/passwd              | PDF  | passwd.pdf",
            "C:\\\\fakepath\\\\foto.JPG    | JPEG | foto.jpg",
            "fatura.pdf.exe                | PDF  | fatura.pdf.pdf",
            "imagem.png                    | JPEG | imagem.jpg",
            "'<script>a</script>.png'      | PNG  | script.png",
            "'a<b>\"c|d?.png'             | PNG  | abcd.png",
            "'   '                         | PNG  | arquivo.png",
            "''                            | MP4  | arquivo.mp4" })
    void displayNameIsSanitizedAndUsesTheRealExtension(String original, StoredFileType type, String expected) {
        assertThat(CrmFileService.safeName(original, type)).isEqualTo(expected);
    }

    @Test
    void displayNameIsCappedAt200Characters() {
        assertThat(CrmFileService.safeName("a".repeat(500) + ".pdf", StoredFileType.PDF)).hasSize(200).endsWith(".pdf");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
