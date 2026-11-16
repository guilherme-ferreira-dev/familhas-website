package br.com.familhas.crm.instagram;

/** Erro da Graph API. {@code tokenExpired}: código 190 (token inválido ou vencido). */
public class MetaApiException extends RuntimeException {

    private final int code;

    public MetaApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean tokenExpired() {
        return code == 190;
    }
}
