package br.com.familhas.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.client.event.UserRegisteredEvent;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.messaging.OutboxService;

@ExtendWith(MockitoExtension.class)
class KeycloakClientSyncServiceTests {

    private static final String SUB = "0b6d1f1e-1111-4c2a-9f00-aaaaaaaaaaaa";
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private OutboxService outboxService;

    private KeycloakClientSyncService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakClientSyncService(clientRepository, outboxService, Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    private static KeycloakClientSyncService.KeycloakUser user(String email, boolean verified) {
        return new KeycloakClientSyncService.KeycloakUser(SUB, email, verified, "Ana Souza", "Ana");
    }

    private static ClientEntity existing(String email, String keycloakId, EntityStatus status) {
        ClientEntity client = new ClientEntity();
        client.setId(UUID.randomUUID());
        client.setEmail(email);
        client.setName("Ana");
        client.setKeycloakId(keycloakId);
        client.setStatus(status);
        return client;
    }

    @Test
    void linksExistingAccountByVerifiedEmail() {
        ClientEntity local = existing("ana@familhas.com", null, EntityStatus.ACTIVE);
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.empty());
        when(clientRepository.findByEmailIgnoreCase("ana@familhas.com")).thenReturn(Optional.of(local));

        service.syncClientId(user("ana@familhas.com", true));

        assertThat(local.getKeycloakId()).isEqualTo(SUB);
    }

    @Test
    void refusesToLinkUnverifiedEmail() {
        ClientEntity local = existing("ana@familhas.com", null, EntityStatus.ACTIVE);
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.empty());
        when(clientRepository.findByEmailIgnoreCase("ana@familhas.com")).thenReturn(Optional.of(local));

        assertThatThrownBy(() -> service.syncClientId(user("ana@familhas.com", false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(local.getKeycloakId()).isNull();
    }

    @Test
    void refusesEmailAlreadyLinkedToAnotherKeycloakUser() {
        ClientEntity local = existing("ana@familhas.com", "outro-sub", EntityStatus.ACTIVE);
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.empty());
        when(clientRepository.findByEmailIgnoreCase("ana@familhas.com")).thenReturn(Optional.of(local));

        assertThatThrownBy(() -> service.syncClientId(user("ana@familhas.com", true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createsClientOnFirstVerifiedLoginAndAppendsUserRegisteredEvent() {
        UUID generatedId = UUID.randomUUID();
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.empty());
        when(clientRepository.findByEmailIgnoreCase("ana@familhas.com")).thenReturn(Optional.empty());
        when(clientRepository.saveAndFlush(any(ClientEntity.class))).thenAnswer(invocation -> {
            ClientEntity entity = invocation.getArgument(0);
            entity.setId(generatedId);
            return entity;
        });

        UUID clientId = service.syncClientId(user("Ana@Familhas.com", true));

        org.mockito.ArgumentCaptor<ClientEntity> saved = org.mockito.ArgumentCaptor.forClass(ClientEntity.class);
        verify(clientRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getKeycloakId()).isEqualTo(SUB);
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@familhas.com");
        assertThat(saved.getValue().getStatus()).isEqualTo(EntityStatus.ACTIVE);

        org.mockito.ArgumentCaptor<UserRegisteredEvent> event = org.mockito.ArgumentCaptor
                .forClass(UserRegisteredEvent.class);
        verify(outboxService).append(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(clientId).isEqualTo(generatedId);
        assertThat(event.getValue().email()).isEqualTo("ana@familhas.com");
        assertThat(event.getValue().firstName()).isEqualTo("Ana");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().schemaVersion()).isEqualTo(1);
    }

    @Test
    void linkingExistingAccountDoesNotPublishUserRegistered() {
        ClientEntity local = existing("ana@familhas.com", null, EntityStatus.ACTIVE);
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.empty());
        when(clientRepository.findByEmailIgnoreCase("ana@familhas.com")).thenReturn(Optional.of(local));

        service.syncClientId(user("ana@familhas.com", true));

        verify(outboxService, never()).append(any());
    }

    @Test
    void laterLoginsDoNotPublishUserRegistered() {
        ClientEntity local = existing("ana@familhas.com", SUB, EntityStatus.ACTIVE);
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.of(local));

        service.syncClientId(user("ana@familhas.com", true));

        verify(outboxService, never()).append(any());
    }

    @Test
    void unverifiedFirstLoginCreatesNothingAndPublishesNothing() {
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.empty());
        when(clientRepository.findByEmailIgnoreCase("ana@familhas.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncClientId(user("ana@familhas.com", false)))
                .isInstanceOf(ResponseStatusException.class);
        verify(outboxService, never()).append(any());
    }

    @Test
    void firstNameFallsBackToFirstTermOfNameClaim() {
        assertThat(KeycloakClientSyncService.firstName(
                new KeycloakClientSyncService.KeycloakUser(SUB, "a@b.com", true, "  Maria   Clara Souza", null)))
                .isEqualTo("Maria");
        assertThat(KeycloakClientSyncService.firstName(
                new KeycloakClientSyncService.KeycloakUser(SUB, "a@b.com", true, null, " ")))
                .isNull();
    }

    @Test
    void inactiveAccountGetsForbidden() {
        ClientEntity local = existing("ana@familhas.com", SUB, EntityStatus.INACTIVE);
        when(clientRepository.findByKeycloakId(SUB)).thenReturn(Optional.of(local));

        assertThatThrownBy(() -> service.syncClientId(user("ana@familhas.com", true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenWithoutSubjectIsRejected() {
        assertThatThrownBy(() -> service.syncClientId(
                new KeycloakClientSyncService.KeycloakUser(null, "ana@familhas.com", true, "Ana", "Ana")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
