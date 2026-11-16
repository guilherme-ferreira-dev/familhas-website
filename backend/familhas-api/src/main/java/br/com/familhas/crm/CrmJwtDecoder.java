package br.com.familhas.crm;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Decoder dos tokens do realm do CRM. Não estende {@code JwtDecoder} de propósito: assim o {@code JwtDecoder}
 * da Loja continua sendo o único desse tipo (os testes trocam um e outro por mocks sem ambiguidade).
 */
@FunctionalInterface
public interface CrmJwtDecoder {

    Jwt decode(String token) throws JwtException;
}
