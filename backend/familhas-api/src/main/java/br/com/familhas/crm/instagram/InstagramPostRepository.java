package br.com.familhas.crm.instagram;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstagramPostRepository extends JpaRepository<InstagramPostEntity, UUID> {

    Page<InstagramPostEntity> findByStatusIn(Collection<PostStatus> statuses, Pageable pageable);

    /**
     * Claim atômico: só um pedido de publicação passa. O UPDATE condicional é decidido pelo banco; quem recebe 0
     * linhas perdeu a corrida (ou o post já foi publicado).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InstagramPostEntity p
               set p.status = br.com.familhas.crm.instagram.PostStatus.PUBLISHING, p.publishClaimedAt = :now,
                   p.lastError = null, p.publishedBy = :who, p.version = p.version + 1
             where p.id = :id
               and p.status in (br.com.familhas.crm.instagram.PostStatus.READY,
                                br.com.familhas.crm.instagram.PostStatus.FAILED)
               and p.publishAttemptedAt is null
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now, @Param("who") String who);

    /** Publicações presas (servidor caiu no meio): para o {@code InstagramPublishRecovery}. */
    @Query("select p from InstagramPostEntity p where p.status = br.com.familhas.crm.instagram.PostStatus.PUBLISHING and p.publishClaimedAt < :before")
    List<InstagramPostEntity> findStuck(@Param("before") Instant before);
}
