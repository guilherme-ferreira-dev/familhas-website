package br.com.familhas.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keepsAValidIdFromTheCallerInTheMdcAndTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cities");
        request.addHeader(CorrelationIdFilter.HEADER, "web-3f2a9c71");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturing(seenInMdc));

        assertThat(seenInMdc.get()).isEqualTo("web-3f2a9c71");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("web-3f2a9c71");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).as("MDC limpo depois da requisição").isNull();
    }

    @Test
    void generatesAnIdWhenTheCallerSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/cities"), response, chainCapturing(seenInMdc));

        assertThat(seenInMdc.get()).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seenInMdc.get());
    }

    /** Quebra de linha, JSON, longo demais ou curto demais: não entra no log, troca por um UUID. */
    @ParameterizedTest
    @ValueSource(strings = { "abc", "bad\nINFO forged line", "{\"level\":\"ERROR\"}", "spaces are bad",
            "x2345678901234567890123456789012345678901234567890123456789012345" })
    void replacesUnsafeIds(String unsafe) {
        assertThat(CorrelationIdFilter.resolve(unsafe)).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
    }

    private static MockFilterChain chainCapturing(AtomicReference<String> seen) {
        return new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });
    }
}
