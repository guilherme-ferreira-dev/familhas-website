package br.com.familhas.crm.outbound;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Política anti-SSRF para toda URL que o servidor busca a pedido do usuário (link de orçamento, imagem, post de
 * inspiração). ADR 0014, D5.
 *
 * Aceita só https (http só se ligado de propósito), portas 443/80, sem usuário:senha na URL, e host cujo DNS resolve
 * apenas para endereços públicos. Bloqueia loopback, privados (10/8, 172.16/12, 192.168/16), link-local (inclui o
 * metadata 169.254.169.254), CGNAT 100.64/10, 0/8, multicast, reservados, ULA fc00::/7 e IPv4 mapeado em IPv6.
 * Cada redirect é revalidado por {@link SafeHttpFetcher}.
 *
 * Risco residual: DNS rebinding entre a checagem e a conexão. Em produção, o egress da API deve passar por firewall
 * ou proxy que também bloqueie as faixas internas.
 */
public class OutboundUrlPolicy {

    @FunctionalInterface
    public interface Resolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    public static final Resolver SYSTEM_RESOLVER = host -> List.of(InetAddress.getAllByName(host));

    private final boolean allowHttp;
    private final Resolver resolver;

    public OutboundUrlPolicy(boolean allowHttp, Resolver resolver) {
        this.allowHttp = allowHttp;
        this.resolver = resolver;
    }

    /** Valida e devolve a URI normalizada; recusa com {@link OutboundUrlRejectedException}. */
    public URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim()).normalize();
        } catch (IllegalArgumentException e) {
            throw new OutboundUrlRejectedException("URL malformada");
        }
        return check(uri);
    }

    public URI check(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
            throw new OutboundUrlRejectedException("esquema nao permitido: " + scheme);
        }
        if (uri.getRawUserInfo() != null) {
            throw new OutboundUrlRejectedException("credenciais na URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new OutboundUrlRejectedException("sem host");
        }
        int port = uri.getPort();
        if (port != -1 && port != 443 && port != 80) {
            throw new OutboundUrlRejectedException("porta nao permitida: " + port);
        }
        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new OutboundUrlRejectedException("host nao resolve");
        }
        if (addresses.isEmpty()) {
            throw new OutboundUrlRejectedException("host nao resolve");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new OutboundUrlRejectedException("endereco interno: " + address.getHostAddress());
            }
        }
        return uri;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            return !(first == 0                                   // 0.0.0.0/8
                    || first == 100 && second >= 64 && second <= 127 // 100.64.0.0/10 CGNAT
                    || first == 192 && second == 0 && (b[2] & 0xFF) == 0 // 192.0.0.0/24
                    || first == 198 && (second == 18 || second == 19)    // 198.18.0.0/15
                    || first >= 240);                                  // reservado e broadcast
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {                        // fc00::/7 ULA
                return false;
            }
            boolean mappedV4 = true;                             // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) {
                mappedV4 &= b[i] == 0;
            }
            if (mappedV4 && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                return false;
            }
            return !(first == 0x20 && (b[1] & 0xFF) == 0x01 && b[2] == 0x0d && (b[3] & 0xFF) == 0xb8); // 2001:db8::/32
        }
        return false;
    }
}
