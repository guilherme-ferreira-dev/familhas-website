package br.com.familhas.crm.instagram;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicação presa em PUBLISHING (servidor reiniciou no meio). Sem tentativa de media_publish: FAILED, pode tentar
 * de novo. Com tentativa: UNCERTAIN, uma pessoa confere no Instagram.
 */
@Component
public class InstagramPublishRecovery {

    private static final Logger log = LoggerFactory.getLogger(InstagramPublishRecovery.class);

    private final InstagramPostRepository repository;
    private final MetaProperties properties;
    private final Clock clock;

    public InstagramPublishRecovery(InstagramPostRepository repository, MetaProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${familhas.meta.recovery-delay:PT5M}", fixedDelayString = "${familhas.meta.recovery-delay:PT5M}")
    @Transactional
    public int recover() {
        var stuck = repository.findStuck(clock.instant().minus(properties.stuckAfter()));
        for (InstagramPostEntity post : stuck) {
            boolean attempted = post.getPublishAttemptedAt() != null;
            post.setStatus(attempted ? PostStatus.UNCERTAIN : PostStatus.FAILED);
            post.setLastError(attempted
                    ? "Publicacao interrompida depois do media_publish. Confira no Instagram."
                    : "Publicacao interrompida antes de enviar. Pode tentar de novo.");
            log.warn("Publicacao presa {} resolvida como {}", post.getId(), post.getStatus());
        }
        return stuck.size();
    }
}
