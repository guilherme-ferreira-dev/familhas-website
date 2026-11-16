package br.com.familhas.crm.instagram;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Post da fila do Instagram (V22). */
@Entity
@Table(name = "instagram_posts")
@Getter
@Setter
@NoArgsConstructor
public class InstagramPostEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_format", nullable = false, length = 20)
    private PostFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PostOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 2000)
    private String idea;

    @Column(length = 2200)
    private String caption;

    @Column(length = 500)
    private String hashtags;

    @Column(name = "video_file_id")
    private UUID videoFileId;

    @Column(name = "meta_media_id", length = 64)
    private String metaMediaId;

    @Column(length = 300)
    private String permalink;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "publish_claimed_at")
    private Instant publishClaimedAt;

    @Column(name = "publish_attempted_at")
    private Instant publishAttemptedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 180)
    private String publishedBy;

    @Column(name = "created_by", length = 180, updatable = false)
    private String createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<InstagramPostSlideEntity> slides = new ArrayList<>();
}
