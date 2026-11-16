package br.com.familhas.crm.instagram;

/**
 * Onde a publicação roda. Não é um {@code Executor}: um bean desse tipo faria o Spring Boot desligar o
 * {@code applicationTaskExecutor} padrão (usado pelo MVC assíncrono).
 */
@FunctionalInterface
public interface PublishDispatcher {

    void dispatch(Runnable task);
}
