package br.com.familhas.crm.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * Onde o conteúdo dos arquivos mora. Hoje um volume local privado ({@link LocalFileStorage}); um adapter S3 pode
 * entrar depois sem mudar os módulos. A chave é sempre gerada pelo CRM (UUID), nunca pelo usuário.
 */
public interface FileStorage {

    /** Grava o conteúdo sob a chave. Falha se a chave já existir. */
    void put(String key, InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
