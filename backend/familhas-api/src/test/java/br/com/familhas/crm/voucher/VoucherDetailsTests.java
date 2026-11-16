package br.com.familhas.crm.voucher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class VoucherDetailsTests {

    private static final LocalDate DAY = LocalDate.of(2027, 1, 29);

    @Test
    void requiredFieldsDependOnTheType() {
        VoucherDetails empty = details(null, null, null, null, null, null);
        assertThat(empty.missingFor(VoucherType.FLIGHT)).containsExactly("provider", "origin", "destination",
                "serviceDate");
        assertThat(empty.missingFor(VoucherType.HOTEL)).containsExactly("provider", "serviceDate", "endDate");
        assertThat(empty.missingFor(VoucherType.TRANSFER)).containsExactly("origin", "destination", "serviceDate");
        assertThat(empty.missingFor(VoucherType.TOUR)).containsExactly("description", "serviceDate");
    }

    @Test
    void completeDetailsPassAndBlankCountsAsMissing() {
        assertThat(details("LATAM", "BSB", "MCZ", DAY, null, null).missingFor(VoucherType.FLIGHT)).isEmpty();
        assertThat(details("Hotel Ponta Verde", null, null, DAY, DAY.plusDays(6), null).missingFor(VoucherType.HOTEL))
                .isEmpty();
        assertThat(details("   ", "BSB", "MCZ", DAY, null, null).missingFor(VoucherType.FLIGHT))
                .containsExactly("provider");
        assertThat(details(null, null, null, DAY, null, "Passeio de buggy").missingFor(VoucherType.TOUR)).isEmpty();
    }

    static VoucherDetails details(String provider, String origin, String destination, LocalDate start, LocalDate end,
            String description) {
        return new VoucherDetails(provider, null, origin, destination, null, start, null, end, null, null, null, null,
                null, null, description, null, null, null, null);
    }
}
