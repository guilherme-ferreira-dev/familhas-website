package br.com.familhas.crm.team;

import java.util.List;
import java.util.Set;

/** O que o CRM faz na Admin API do Keycloak. Só papéis do CRM entram aqui (a lista é fechada no service). */
public interface TeamDirectory {

    record User(String id, String username, String email, String firstName, String lastName, boolean enabled,
            Long createdTimestamp, List<String> requiredActions) {
    }

    /**
     * Usuários do realm. A conta de serviço só tem view/query/manage-users: papéis vêm de cada usuário, sem
     * {@code view-realm} (ADR 0017).
     */
    List<User> users();

    User user(String id);

    Set<String> realmRoles(String userId);

    boolean hasOtp(String userId);

    /** Cria o usuário e devolve o id. 409 se usuário ou e-mail já existem. */
    String create(User user);

    void addRealmRoles(String userId, Set<String> roles);

    void removeRealmRoles(String userId, Set<String> roles);

    void setEnabled(String userId, boolean enabled);

    void setRequiredActions(String userId, List<String> actions);

    /** E-mail do Keycloak com o link para cumprir as ações (definir senha, cadastrar TOTP). */
    void sendActionsEmail(String userId, List<String> actions);

    /** Encerra as sessões abertas (ao desativar). */
    void logout(String userId);
}
