package br.com.familhas.crm.team;

/** Erro da Admin API do Keycloak, com o status HTTP (409 = usuário ou e-mail já existe). */
public class KeycloakAdminException extends RuntimeException {

    private final int status;

    public KeycloakAdminException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
