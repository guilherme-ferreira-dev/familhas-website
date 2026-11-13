package br.com.familhas.admin.pricing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.pricing.PricingSettingsResponse.Source;
import br.com.familhas.pricing.PricingProperties;
import br.com.familhas.pricing.PricingRulesProvider;

/**
 * Margem e taxa de serviço em vigor (ADR 0011): a linha salva no Admin ou, sem ela, as variáveis de ambiente.
 *
 * O PricingService lê as regras em toda busca e cotação, então o valor fica em memória por {@link #CACHE_TTL}.
 * Esta instância vê a mudança na hora; as outras instâncias, em até 30 s.
 */
@Service
@Primary
public class PricingSettingsService implements PricingRulesProvider {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(PricingSettingsService.class);

    private record Cached(PricingSettingsResponse settings, Instant loadedAt) {
    }

    private final PricingSettingsRepository repository;
    private final PricingProperties defaults;
    private final Clock clock;
    private volatile Cached cached;

    public PricingSettingsService(PricingSettingsRepository repository, PricingProperties defaults, Clock clock) {
        this.repository = repository;
        this.defaults = defaults;
        this.clock = clock;
    }

    @Override
    public PricingProperties current() {
        PricingSettingsResponse settings = settings();
        return new PricingProperties(settings.marginPercent(), settings.serviceFeePerPassenger(),
                settings.serviceFeeCurrency());
    }

    @Transactional(readOnly = true)
    public PricingSettingsResponse settings() {
        Cached snapshot = cached;
        Instant now = clock.instant();
        if (snapshot != null && now.isBefore(snapshot.loadedAt().plus(CACHE_TTL))) {
            return snapshot.settings();
        }
        PricingSettingsResponse loaded = repository.findById(PricingSettingsEntity.SINGLE_ROW_ID)
                .map(PricingSettingsService::toResponse)
                .orElseGet(() -> new PricingSettingsResponse(defaults.marginPercent(),
                        defaults.serviceFeePerPassenger(), defaults.serviceFeeCurrency(), Source.ENVIRONMENT, null,
                        null));
        cached = new Cached(loaded, now);
        return loaded;
    }

    @Transactional
    public PricingSettingsResponse update(PricingSettingsRequest request, AuditActor actor) {
        PricingSettingsEntity row = repository.findById(PricingSettingsEntity.SINGLE_ROW_ID).orElseGet(() -> {
            PricingSettingsEntity created = new PricingSettingsEntity();
            created.setId(PricingSettingsEntity.SINGLE_ROW_ID);
            return created;
        });
        row.setMarginPercent(request.marginPercent());
        row.setServiceFeePerPassenger(request.serviceFeePerPassenger());
        row.setServiceFeeCurrency(request.serviceFeeCurrency());
        row.setUpdatedBy(actor.displayName());
        PricingSettingsResponse saved = toResponse(repository.saveAndFlush(row));
        cached = new Cached(saved, clock.instant());
        log.info("Regras de preco alteradas: marginPercent={} serviceFee={} {} por {}", saved.marginPercent(),
                saved.serviceFeePerPassenger(), saved.serviceFeeCurrency(), actor.subject());
        return saved;
    }

    private static PricingSettingsResponse toResponse(PricingSettingsEntity row) {
        return new PricingSettingsResponse(row.getMarginPercent(), row.getServiceFeePerPassenger(),
                row.getServiceFeeCurrency(), Source.DATABASE, row.getUpdatedAt(), row.getUpdatedBy());
    }
}
