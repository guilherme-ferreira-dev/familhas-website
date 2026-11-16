package br.com.familhas.crm.instagram;

import java.util.Map;

/** Chamadas à Graph API do Instagram. Todas lançam {@link MetaApiException} em erro da Meta. */
public interface MetaGateway {

    /** Cria um contêiner de mídia (POST /{ig-user}/media). Devolve o id do contêiner. */
    String createContainer(Map<String, String> params);

    /** status_code do contêiner: FINISHED, IN_PROGRESS, ERROR, EXPIRED ou PUBLISHED. */
    String containerStatus(String containerId);

    /** POST /{ig-user}/media_publish. Devolve o id da mídia publicada. */
    String publish(String containerId);

    /** Link público do post (melhor esforço: null se não vier). */
    String permalink(String mediaId);
}
