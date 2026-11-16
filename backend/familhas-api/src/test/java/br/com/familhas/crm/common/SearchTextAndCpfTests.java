package br.com.familhas.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchTextAndCpfTests {

    @Test
    void normalizesAccentsCaseAndSpaces() {
        assertThat(SearchText.normalize("  João   da SILVA Conceição ")).isEqualTo("joao da silva conceicao");
        assertThat(SearchText.normalize(null)).isEmpty();
        assertThat(SearchText.tokens("Silva joão SILVA")).containsExactly("silva", "joao");
        assertThat(SearchText.tokens("   ")).isEmpty();
        assertThat(SearchText.tokens("a b c d e f g h")).hasSize(6);
    }

    @Test
    void validCpfIsAcceptedAndMasked() {
        assertThat(Cpf.digits("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.isValid("52998224725")).isTrue();
        assertThat(Cpf.mask("52998224725")).isEqualTo("***.982.247-**");
        assertThat(Cpf.digits("  ")).isNull();
        assertThat(Cpf.mask(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "52998224724", "11111111111", "123", "5299822472a", "529982247250" })
    void invalidCpfIsRejected(String cpf) {
        assertThat(Cpf.isValid(cpf)).isFalse();
    }
}
