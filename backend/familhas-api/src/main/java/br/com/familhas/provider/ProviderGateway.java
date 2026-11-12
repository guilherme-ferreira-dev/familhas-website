package br.com.familhas.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Ponto único de acesso aos provedores (guia, seção 6 item 7).
 *
 * - Cada provedor roda isolado: circuit breaker + timeout (Resilience4j), instância provider-&lt;tipo&gt;-&lt;nome&gt;.
 * - Falha, timeout ou circuito aberto de um provedor não derruba a busca: ele entra em unavailableProviders.
 * - Buscas bem-sucedidas ficam em cache (Caffeine) por provedor e critério; falhas nunca são cacheadas.
 * - findOffer (cotação) não usa cache: o preço precisa ser o atual.
 */
@Component
public class ProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(ProviderGateway.class);

    private final CircuitBreakerRegistry circuitBreakers;
    private final TimeLimiterRegistry timeLimiters;
    private final ExecutorService executor;
    private final Cache<CacheKey, List<?>> cache;

    public ProviderGateway(CircuitBreakerRegistry circuitBreakers, TimeLimiterRegistry timeLimiters,
            ExecutorService providerExecutor, ProviderProperties properties) {
        this.circuitBreakers = circuitBreakers;
        this.timeLimiters = timeLimiters;
        this.executor = providerExecutor;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cacheTtl())
                .maximumSize(properties.cacheMaxSize())
                .build();
    }

    record CacheKey(ProviderType type, String provider, Object criteria) {
    }

    public <C, O extends ProviderOffer> ProviderResult<O> search(List<? extends Provider<C, O>> providers,
            C criteria) {
        List<O> offers = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (Provider<C, O> provider : providers) {
            CacheKey key = new CacheKey(provider.type(), provider.name(), criteria);
            @SuppressWarnings("unchecked")
            List<O> cached = (List<O>) cache.getIfPresent(key);
            if (cached != null) {
                offers.addAll(cached);
                continue;
            }
            try {
                List<O> found = call(provider, () -> provider.search(criteria));
                cache.put(key, List.copyOf(found));
                offers.addAll(found);
            } catch (Exception ex) {
                unavailable.add(provider.name());
                log.warn("Provedor indisponivel na busca: type={} provider={} erro={}", provider.type(),
                        provider.name(), ex.getClass().getSimpleName());
            }
        }
        return new ProviderResult<>(List.copyOf(offers), List.copyOf(unavailable));
    }

    /**
     * Reconsulta a oferta no provedor dono do offerId, sem cache.
     *
     * @throws ProviderUnavailableException se o provedor falhar, estourar o timeout ou estiver com o circuito aberto
     */
    public <C, O extends ProviderOffer> Optional<O> findOffer(List<? extends Provider<C, O>> providers,
            String offerId) {
        String providerName = ProviderOffer.providerOf(offerId);
        for (Provider<C, O> provider : providers) {
            if (provider.name().equals(providerName)) {
                try {
                    return call(provider, () -> provider.findOffer(offerId));
                } catch (Exception ex) {
                    log.warn("Provedor indisponivel na consulta de oferta: type={} provider={} erro={}",
                            provider.type(), provider.name(), ex.getClass().getSimpleName());
                    throw new ProviderUnavailableException(provider.type(), provider.name());
                }
            }
        }
        return Optional.empty();
    }

    private <T> T call(Provider<?, ?> provider, Callable<T> action) throws Exception {
        String instance = "provider-" + provider.type().name().toLowerCase().replace('_', '-') + "-"
                + provider.name();
        CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker(instance);
        TimeLimiter timeLimiter = timeLimiters.timeLimiter(instance);
        Callable<T> limited = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return action.call();
                    } catch (RuntimeException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }, executor));
        return circuitBreaker.executeCallable(limited);
    }
}
