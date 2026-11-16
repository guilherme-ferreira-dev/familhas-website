package br.com.familhas.crm.instagram;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lâmina de um post: imagem final com marca, cópia "-limpa", gancho, título, tópicos e legenda própria. */
@Entity
@Table(name = "instagram_post_slides")
@Getter
@Setter
@NoArgsConstructor
public class InstagramPostSlideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private InstagramPostEntity post;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "image_file_id", nullable = false)
    private UUID imageFileId;

    @Column(name = "clean_file_id")
    private UUID cleanFileId;

    @Column(length = 120)
    private String hook;

    @Column(length = 120)
    private String title;

    /** Tópicos separados por quebra de linha. */
    @Column(length = 1000)
    private String bullets;

    @Column(length = 1000)
    private String caption;
}
