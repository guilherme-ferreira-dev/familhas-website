package br.com.familhas.client;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.client.event.UserRegisteredEvent;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.messaging.OutboxService;

/**
 * Sincroniza o usuário do Keycloak com o Client local ("no primeiro login, criar o Client").
 *
 * - keycloakId (claim sub) é a chave do vínculo;
 * - sem vínculo, procura por e-mail e só vincula com e-mail verificado e conta local sem login;
 * - sem conta local e com e-mail verificado, cria o Client;
 * - conta INACTIVE recebe 403.
 *
 * Só a criação publica o UserRegisteredEvent (boas-vindas), gravado no outbox na mesma transação do Client:
 * vincular um cadastro feito pelo Enéas não é um cadastro novo, e logins seguintes não geram evento.
 *
 * Usa transação própria (REQUIRES_NEW) para o vínculo ficar gravado mesmo que a requisição falhe depois.
 */
@Service
public class KeycloakClientSyncService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakClientSyncService.class);

    private final ClientRepository clientRepository;
    private final OutboxService outboxService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public KeycloakClientSyncService(ClientRepository clientRepository, OutboxService outboxService, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.clientRepository = clientRepository;
        this.outboxService = outboxService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Dados mínimos do token. Nada além disso é guardado (LGPD). */
    public record KeycloakUser(String subject, String email, boolean emailVerified, String displayName,
            String givenName) {

        /** Claims usados na sincronização: sub, email, email_verified, name e given_name. */
        public static KeycloakUser fromJwt(Jwt jwt) {
            Object verified = jwt.getClaims().get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));
            return new KeycloakUser(jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified,
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("given_name"));
        }
    }

    /** Retorna o id do Client local vinculado ao usuário autenticado. */
    public java.util.UUID syncClientId(KeycloakUser user) {
        if (user.subject() == null || user.subject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token sem o claim sub");
        }
        try {
            return transactionTemplate.execute(status -> doSync(user));
        } catch (DataIntegrityViolationException ex) {
            // Primeiro login simultâneo (duas abas): a segunda requisição encontra o vínculo já criado.
            return clientRepository.findByKeycloakId(user.subject())
                    .map(this::ensureActive)
                    .map(ClientEntity::getId)
                    .orElseThrow(() -> ex);
        }
    }

    private java.util.UUID doSync(KeycloakUser user) {
        String email = normalizeEmail(user.email());

        Optional<ClientEntity> linked = clientRepository.findByKeycloakId(user.subject());
        if (linked.isPresent()) {
            ClientEntity client = ensureActive(linked.get());
            refreshEmail(client, email, user.emailVerified());
            return client.getId();
        }

        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Token sem e-mail: autorize o scope openid email");
        }

        Optional<ClientEntity> sameEmail = clientRepository.findByEmailIgnoreCase(email);
        if (sameEmail.isPresent()) {
            ClientEntity client = ensureActive(sameEmail.get());
            if (client.getKeycloakId() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "E-mail ja vinculado a outro usuario do Keycloak");
            }
            requireVerifiedEmail(user);
            client.setKeycloakId(user.subject());
            clientRepository.saveAndFlush(client);
            log.info("Cliente vinculado ao Keycloak: clientId={}", client.getId());
            return client.getId();
        }

        requireVerifiedEmail(user);
        ClientEntity created = new ClientEntity();
        created.setKeycloakId(user.subject());
        created.setEmail(email);
        created.setName(displayName(user.displayName(), email));
        created.setStatus(EntityStatus.ACTIVE);
        clientRepository.saveAndFlush(created);
        outboxService.append(UserRegisteredEvent.of(created.getId(), email, firstName(user), clock.instant()));
        log.info("Cliente criado no primeiro login: clientId={}", created.getId());
        return created.getId();
    }

    private static void requireVerifiedEmail(KeycloakUser user) {
        if (!user.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Confirme o e-mail no Keycloak antes de acessar a conta");
        }
    }

    private ClientEntity ensureActive(ClientEntity client) {
        if (client.getStatus() != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta inativa");
        }
        return client;
    }

    /** O Keycloak manda no e-mail das contas com login; só atualiza se estiver verificado. */
    private void refreshEmail(ClientEntity client, String email, boolean verified) {
        if (email == null || !verified || email.equalsIgnoreCase(client.getEmail())) {
            return;
        }
        Optional<ClientEntity> owner = clientRepository.findByEmailIgnoreCase(email);
        if (owner.isPresent() && !owner.get().getId().equals(client.getId())) {
            log.warn("E-mail do Keycloak ja pertence a outro cliente; mantendo o atual. clientId={}",
                    client.getId());
            return;
        }
        client.setEmail(email);
        clientRepository.save(client);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /** Claim given_name; sem ele, o primeiro termo do claim name. Pode ser nulo. */
    static String firstName(KeycloakUser user) {
        if (user.givenName() != null && !user.givenName().isBlank()) {
            return user.givenName().trim();
        }
        if (user.displayName() != null && !user.displayName().isBlank()) {
            return user.displayName().trim().split("\\s+")[0];
        }
        return null;
    }

    private static String displayName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return email;
    }
}
