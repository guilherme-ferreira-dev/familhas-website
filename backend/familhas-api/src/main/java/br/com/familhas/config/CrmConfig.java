package br.com.familhas.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.crm.backup.BackupProperties;
import br.com.familhas.crm.company.CompanyProperties;
import br.com.familhas.crm.flightsearch.FlightSearchGateway;
import br.com.familhas.crm.instagram.GraphMetaGateway;
import br.com.familhas.crm.instagram.MetaGateway;
import br.com.familhas.crm.instagram.MetaProperties;
import br.com.familhas.crm.instagram.PublishDispatcher;
import br.com.familhas.crm.flightsearch.FlightSearchProperties;
import br.com.familhas.crm.flightsearch.SerpApiFlightSearchGateway;
import br.com.familhas.crm.file.FileLinkSigner;
import br.com.familhas.crm.file.FileStorage;
import br.com.familhas.crm.file.LocalFileStorage;
import br.com.familhas.crm.file.StorageProperties;
import br.com.familhas.crm.integration.IntegrationProperties;
import br.com.familhas.crm.marketing.ImageGenerator;
import br.com.familhas.crm.marketing.OpenAiImageGenerator;
import br.com.familhas.crm.marketing.OpenAiProperties;
import br.com.familhas.crm.outbound.OutboundUrlPolicy;
import br.com.familhas.crm.privacy.PrivacyProperties;
import br.com.familhas.crm.team.KeycloakTeamDirectory;
import br.com.familhas.crm.team.TeamDirectory;
import br.com.familhas.crm.team.TeamProperties;

/** Fundação do CRM interno (ADR 0014): dados da empresa, integrações, storage privado e saída para a internet. */
@Configuration
@EnableConfigurationProperties({ CompanyProperties.class, IntegrationProperties.class, StorageProperties.class,
        FlightSearchProperties.class, MetaProperties.class, TeamProperties.class,
        BackupProperties.class, PrivacyProperties.class, OpenAiProperties.class })
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

    /** OpenAI (gpt-image-1) com a chave do servidor. Sem provedor ou chave o registry barra antes (503). */
    @Bean
    ImageGenerator imageGenerator(OpenAiProperties properties, IntegrationProperties integrations,
            ObjectMapper objectMapper) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(properties.timeout());
        RestClient client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
        return new OpenAiImageGenerator(client, integrations.openaiApiKey(), properties, objectMapper);
    }

    /** Graph API da Meta com o token do servidor. Sem credenciais o registry barra antes (503). */
    @Bean
    MetaGateway metaGateway(MetaProperties properties, IntegrationProperties integrations, ObjectMapper objectMapper) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestClient client = RestClient.builder().baseUrl(properties.graphBaseUrl()).requestFactory(factory).build();
        return new GraphMetaGateway(client, integrations.metaInstagramUserId(), integrations.metaAccessToken(),
                objectMapper);
    }

    /**
     * Publicação em segundo plano (threads virtuais): vídeo do Reel leva minutos na Meta. Nos testes de integração,
     * {@code familhas.meta.publish-async=false} roda na mesma thread.
     */
    @Bean
    PublishDispatcher instagramPublishDispatcher(@Value("${familhas.meta.publish-async:true}") boolean async) {
        if (!async) {
            return Runnable::run;
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        return executor::execute;
    }

    /** Admin API do Keycloak com a conta de serviço do realm do CRM. Sem segredo, o registry barra antes (503). */
    @Bean
    TeamDirectory teamDirectory(TeamProperties properties, IntegrationProperties integrations, Clock clock) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new KeycloakTeamDirectory(RestClient.builder().requestFactory(factory).build(), properties,
                integrations.keycloakTeamSecret(), clock);
    }

    @Bean
    OutboundUrlPolicy outboundUrlPolicy(@Value("${familhas.outbound.allow-http:false}") boolean allowHttp) {
        return new OutboundUrlPolicy(allowHttp, OutboundUrlPolicy.SYSTEM_RESOLVER);
    }
}
