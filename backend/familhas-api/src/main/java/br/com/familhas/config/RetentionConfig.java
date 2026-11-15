package br.com.familhas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.familhas.retention.RetentionProperties;
import br.com.familhas.retention.RetentionService;

/** Agenda a retenção (ADR 0012). O agendamento vem do @EnableScheduling do MessagingConfig. */
@Configuration
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionConfig {

    @Component
    @ConditionalOnProperty(prefix = "familhas.retention", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    static class RetentionScheduler {

        private final RetentionService retention;

        RetentionScheduler(RetentionService retention) {
            this.retention = retention;
        }

        @Scheduled(cron = "${familhas.retention.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
        void run() {
            retention.purge();
        }
    }
}
