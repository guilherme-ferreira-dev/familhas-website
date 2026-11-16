package br.com.familhas.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.familhas.crm.company.CompanyProperties;
import br.com.familhas.crm.flightsearch.FlightSearchGateway;
import br.com.familhas.crm.flightsearch.FlightSearchProperties;
import br.com.familhas.crm.flightsearch.SerpApiFlightSearchGateway;
import br.com.familhas.crm.file.FileLinkSigner;
import br.com.familhas.crm.file.FileStorage;
import br.com.familhas.crm.file.LocalFileStorage;
import br.com.familhas.crm.file.StorageProperties;
import br.com.familhas.crm.integration.IntegrationProperties;
import br.com.familhas.crm.outbound.OutboundUrlPolicy;

/** Fundação do CRM interno (ADR 0014): dados da empresa, integrações, storage privado e saída para a internet. */
@Configuration
@EnableConfigurationProperties({ CompanyProperties.class, IntegrationProperties.class, StorageProperties.class,
        FlightSearchProperties.class })
public class CrmConfig {

    @Bean
    FileStorage fileStorage(StorageProperties properties) throws IOException {
        return new LocalFileStorage(Path.of(properties.localRoot()));
    }

    @Bean
    FileLinkSigner fileLinkSigner(StorageProperties properties, Clock clock) {
        return new FileLinkSigner(properties.linkSecret(), clock);
    }

    /** SerpApi com a chave do servidor. Sem chave o registry barra antes (503); o gateway nunca é chamado. */
    @Bean
    FlightSearchGateway flightSearchGateway(FlightSearchProperties properties, IntegrationProperties integrations) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(properties.timeout());
        RestClient client = RestClient.builder().baseUrl(properties.serpapiBaseUrl()).requestFactory(factory).build();
        return new SerpApiFlightSearchGateway(client, integrations.serpapiApiKey(), properties);
    }

    @Bean
    OutboundUrlPolicy outboundUrlPolicy(@Value("${familhas.outbound.allow-http:false}") boolean allowHttp) {
        return new OutboundUrlPolicy(allowHttp, OutboundUrlPolicy.SYSTEM_RESOLVER);
    }
}
