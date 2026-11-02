package br.com.familhas.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

class CommonUtilitiesTests {

    @Test
    void currencyDefaultsToBrlOnCreateAndKeepsCurrentOnUpdate() {
        assertThat(Currencies.resolve(null, null)).isEqualTo("BRL");
        assertThat(Currencies.resolve("  ", "USD")).isEqualTo("USD");
        assertThat(Currencies.resolve("eur", "BRL")).isEqualTo("EUR");
    }

    @Test
    void currencyRejectsUnknownIsoCode() {
        assertThatThrownBy(() -> Currencies.resolve("XYZ1", null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> Currencies.resolve("QQQ", null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sortAcceptsOnlyWhitelistedProperties() {
        Set<String> allowed = Set.of("name", "createdAt");

        assertThatCode(() -> Pagination.validateSort(PageRequest.of(0, 10, Sort.by("name")), allowed))
                .doesNotThrowAnyException();
        assertThatCode(() -> Pagination.validateSort(PageRequest.of(0, 10), allowed))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Pagination.validateSort(PageRequest.of(0, 10, Sort.by("password")), allowed))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("password");
    }

    @Test
    void likePatternEscapesWildcardsTypedByUser() {
        assertThat(Specs.likePattern(" 50%_OFF ")).isEqualTo("%50\\%\\_off%");
    }
}
