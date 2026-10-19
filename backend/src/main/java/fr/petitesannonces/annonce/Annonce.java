package fr.petitesannonces.annonce;

import fr.petitesannonces.categorie.Categorie;
import fr.petitesannonces.common.ConflitException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Annonce et ses transitions d'état.
 *
 * <p>Les transitions sont des méthodes du domaine (publier, rejeter, modifier) : l'entité garantit
 * elle-même son cycle de vie. Elle ne connaît en revanche AUCUN rôle : savoir <i>qui</i> a le
 * droit de déclencher une transition relève des {@code @PreAuthorize} (BK-04).
 */
@Entity
public class Annonce {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String titre;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prix;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAnnonce statut;

    @Column(name = "auteur_id", nullable = false, updatable = false, length = 64)
    private String auteurId;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    @Version
    private long version;

    protected Annonce() {
        // requis par JPA
    }

    public Annonce(String titre, String description, BigDecimal prix, Categorie categorie, String auteurId) {
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.description = description;
        this.prix = prix;
        this.categorie = categorie;
        this.auteurId = auteurId;
        this.statut = StatutAnnonce.EN_ATTENTE;
        this.dateCreation = Instant.now();
        this.dateModification = this.dateCreation;
    }

    /** Toute modification repasse l'annonce en attente de modération. */
    public void modifier(String titre, String description, BigDecimal prix, Categorie categorie) {
        this.titre = titre;
        this.description = description;
        this.prix = prix;
        this.categorie = categorie;
        this.statut = StatutAnnonce.EN_ATTENTE;
        this.motifRejet = null;
        this.dateModification = Instant.now();
    }

    public void publier() {
        verifierEnAttente();
        this.statut = StatutAnnonce.PUBLIEE;
        this.dateModification = Instant.now();
    }

    public void rejeter(String motif) {
        verifierEnAttente();
        this.statut = StatutAnnonce.REJETEE;
        this.motifRejet = motif;
        this.dateModification = Instant.now();
    }

    private void verifierEnAttente() {
        if (statut != StatutAnnonce.EN_ATTENTE) {
            throw new ConflitException("Seule une annonce EN_ATTENTE peut être modérée (statut actuel : " + statut + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrix() {
        return prix;
    }

    public Categorie getCategorie() {
        return categorie;
    }

    public StatutAnnonce getStatut() {
        return statut;
    }

    public String getAuteurId() {
        return auteurId;
    }

    public String getMotifRejet() {
        return motifRejet;
    }

    public Instant getDateCreation() {
        return dateCreation;
    }

    public Instant getDateModification() {
        return dateModification;
    }
}
