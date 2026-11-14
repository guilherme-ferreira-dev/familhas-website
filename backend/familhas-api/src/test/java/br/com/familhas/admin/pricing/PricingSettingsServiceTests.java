package br.com.familhas.admin.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.pricing.PricingSettingsResponse.Source;
import br.com.familhas.common.Money;
import br.com.familhas.pricing.PricingProperties;
import br.com.familhas.pricing.PricingService;

class PricingSettingsServiceTests {

    static final PricingProperties ENV = new PricingProperties(BigDecimal.TEN, BigDecimal.ZERO, "BRL");

    private final PricingSettingsRepository repository = mock(PricingSettingsRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2027-01-10T12:00:00Z"));
    private final PricingSettingsService service = new PricingSettingsService(repository, ENV, clock);

    @Test
    void environmentValuesApplyWhileNothingWasSaved() {
        when(repository.findById(1)).thenReturn(Optional.empty());

        assertThat(service.settings().source()).isEqualTo(Source.ENVIRONMENT);
        assertThat(service.current()).isEqualTo(ENV);
    }

    @Test
    void savedRowWinsAndIsCachedFor30Seconds() {
        when(repository.findById(1)).thenReturn(Optional.of(row("12.5000", "25.00")));

        assertThat(service.current().marginPercent()).isEqualByComparingTo("12.5");
        service.current();
        clock.advanceSeconds(29);
        service.current();
        verify(repository, times(1)).findById(1);

        clock.advanceSeconds(2);
        service.current();
        verify(repository, times(2)).findById(1);
    }

    @Test
    void updateCreatesTheRowAndAppliesImmediately() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.current();

        PricingSettingsResponse saved = service.update(
                new PricingSettingsRequest(new BigDecimal("15"), new BigDecimal("30.00"), "BRL"),
                new AuditActor("kc-1", "admin.familhas"));

        assertThat(saved.source()).isEqualTo(Source.DATABASE);
        assertThat(saved.updatedBy()).isEqualTo("admin.familhas");
        assertThat(service.current().marginPercent()).isEqualByComparingTo("15");
        assertThat(service.current().serviceFeePerPassenger()).isEqualByComparingTo("30.00");
    }

    @Test
    void pricingServiceUsesTheCurrentRules() {
        when(repository.findById(1)).thenReturn(Optional.of(row("20.0000", "0.00")));
        PricingService pricing = new PricingService(service);

        assertThat(pricing.customerPrice(new Money(new BigDecimal("100.00"), "BRL"), 1)
                .amount()).isEqualByComparingTo("120.00");
    }

    private static PricingSettingsEntity row(String margin, String fee) {
        PricingSettingsEntity row = new PricingSettingsEntity();
        row.setId(1);
        row.setMarginPercent(new BigDecimal(margin));
        row.setServiceFeePerPassenger(new BigDecimal(fee));
        row.setServiceFeeCurrency("BRL");
        return row;
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
