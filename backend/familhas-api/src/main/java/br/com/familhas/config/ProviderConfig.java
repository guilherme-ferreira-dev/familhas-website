package br.com.familhas.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.familhas.pricing.PricingProperties;
import br.com.familhas.provider.ProviderProperties;
import br.com.familhas.quote.QuoteProperties;

/** Executor das chamadas a provedores: virtual threads, para o timeout não prender threads de plataforma. */
@Configuration
@EnableConfigurationProperties({ ProviderProperties.class, PricingProperties.class, QuoteProperties.class })
public class ProviderConfig {

    @Bean(destroyMethod = "close")
    ExecutorService providerExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
