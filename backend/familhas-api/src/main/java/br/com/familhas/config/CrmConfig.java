package br.com.familhas.config;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.familhas.crm.company.CompanyProperties;
import br.com.familhas.crm.file.FileLinkSigner;
import br.com.familhas.crm.file.FileStorage;
import br.com.familhas.crm.file.LocalFileStorage;
import br.com.familhas.crm.file.StorageProperties;
import br.com.familhas.crm.integration.IntegrationProperties;
import br.com.familhas.crm.outbound.OutboundUrlPolicy;

/** Fundação do CRM interno (ADR 0014): dados da empresa, integrações, storage privado e saída para a internet. */
@Configuration
@EnableConfigurationProperties({ CompanyProperties.class, IntegrationProperties.class, StorageProperties.class })
public class CrmConfig {

    @Bean
    FileStorage fileStorage(StorageProperties properties) throws IOException {
        return new LocalFileStorage(Path.of(properties.localRoot()));
    }

    @Bean
    FileLinkSigner fileLinkSigner(StorageProperties properties, Clock clock) {
        return new FileLinkSigner(properties.linkSecret(), clock);
    }

    @Bean
    OutboundUrlPolicy outboundUrlPolicy(@Value("${familhas.outbound.allow-http:false}") boolean allowHttp) {
        return new OutboundUrlPolicy(allowHttp, OutboundUrlPolicy.SYSTEM_RESOLVER);
    }
}
