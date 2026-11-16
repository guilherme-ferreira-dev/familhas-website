package br.com.familhas.crm.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Anti-SSRF sem rede: o DNS é falso, para testar inclusive host público que resolve para IP interno. */
class OutboundUrlPolicyTests {

    private static final Map<String, String> DNS = Map.ofEntries(
            Map.entry("cvc.com.br", "200.155.10.20"),
            Map.entry("ipv6.example.org", "2606:4700::6810:85e5"),
            Map.entry("rebind.attacker.example", "127.0.0.1"),
            Map.entry("intranet.example", "10.0.0.5"),
            Map.entry("metadata.example", "169.254.169.254"));

    private final OutboundUrlPolicy policy = new OutboundUrlPolicy(false, host -> {
        if (DNS.containsKey(host)) {
            return List.of(InetAddress.getByName(DNS.get(host)));
        }
        if (host.matches("[0-9.:\\[\\]a-fA-F]+")) {
            return List.of(InetAddress.getByName(host));
        }
        throw new UnknownHostException(host);
    });

    @ParameterizedTest
    @ValueSource(strings = { "https://cvc.com.br/orcamento/123", "https://cvc.com.br:443/x",
            "https://ipv6.example.org/img.png", "https://8.8.8.8/" })
    void publicHttpsIsAllowed(String url) {
        assertThat(policy.check(url).getHost()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://cvc.com.br/", "file:///etc/passwd", "ftp://cvc.com.br/", "gopher://cvc.com.br/", "javascript:alert(1)",
            "https://user:pass@cvc.com.br/", "https://cvc.com.br:8443/", "https://cvc.com.br:22/",
            "https://127.0.0.1/", "https://localhost/", "https://[::1]/", "https://0.0.0.0/", "https://10.1.2.3/",
            "https://172.16.0.1/", "https://192.168.0.10/", "https://169.254.169.254/latest/meta-data/",
            "https://100.64.0.1/", "https://[fd00::1]/", "https://[fe80::1]/", "https://[::ffff:127.0.0.1]/",
            "https://224.0.0.1/", "https://255.255.255.255/", "https://rebind.attacker.example/",
            "https://intranet.example/", "https://metadata.example/", "https://nao-existe.invalid/", "", "https:///x",
            "not a url" })
    void internalOrUnsafeUrlsAreRejected(String url) {
        assertThatThrownBy(() -> policy.check(url)).isInstanceOf(OutboundUrlRejectedException.class)
                .satisfies(e -> assertThat(((OutboundUrlRejectedException) e).code()).isEqualTo("URL_NOT_ALLOWED"));
    }

    @Test
    void httpOnlyWhenExplicitlyAllowed() {
        OutboundUrlPolicy lenient = new OutboundUrlPolicy(true, host -> List.of(InetAddress.getByName("200.155.10.20")));
        assertThat(lenient.check("http://cvc.com.br/").getScheme()).isEqualTo("http");
    }

    @Test
    void fetcherRejectsInternalTargetBeforeAnyConnection() {
        SafeHttpFetcher fetcher = new SafeHttpFetcher(policy);
        assertThatThrownBy(() -> fetcher.get("https://127.0.0.1:443/", 1024))
                .isInstanceOf(OutboundUrlRejectedException.class);
    }
}
