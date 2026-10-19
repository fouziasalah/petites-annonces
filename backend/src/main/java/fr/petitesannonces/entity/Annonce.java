package fr.petitesannonces.entity;

// Classes du projet
import fr.petitesannonces.exception.ConflitException;
// Jakarta Persistence (JPA, implémentée par Hibernate)
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
// Java standard
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Annonce, correspondant à la table {@code annonce}.
 *
 * <p>Rôle : représenter une annonce et faire respecter son cycle de vie. Les changements de statut
 * passent obligatoirement par les méthodes {@link #publier()}, {@link #rejeter(String)} et
 * {@link #modifier}, qui vérifient que la transition est permise. L'entité ne connaît aucun rôle :
 * savoir QUI a le droit de déclencher une action est le travail des {@code @PreAuthorize}.
 *
 * <p>Utilisé par : {@code AnnonceService} (création, modifications) et {@code AnnonceRepository}
 * (lecture et écriture en base). Table créée par la migration Flyway {@code V1__schema_initial.sql}.
 */
@Entity
public class Annonce {

    // Clé primaire de type UUID, générée par le code Java (voir le constructeur), pas par la base.
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String titre;

    @Column(nullable = false, length = 2000)
    private String description;

    // precision = 12 chiffres au total, scale = 2 après la virgule : NUMERIC(12, 2) en SQL.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prix;

    // @ManyToOne (JPA) : plusieurs annonces pour une catégorie (clé étrangère categorie_id).
    // FetchType.LAZY : la catégorie n'est chargée depuis la base que si on y accède.
    // optional = false : une annonce a toujours une catégorie.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // @JoinColumn (JPA) : nom de la colonne de clé étrangère dans la table annonce.
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    // @Enumerated(STRING) (JPA) : enregistre le nom de la valeur ("PUBLIEE") et non sa position (1),
    // pour que l'ajout d'une valeur dans l'enum ne fausse pas les données existantes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutAnnonce statut;

    // updatable = false : JPA n'écrira jamais cette colonne lors d'une mise à jour.
    @Column(name = "auteur_id", nullable = false, updatable = false, length = 64)
    private String auteurId;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    @Column(name = "date_modification", nullable = false)
    private Instant dateModification;

    // @Version (JPA) : verrouillage optimiste. JPA incrémente ce numéro à chaque mise à jour ; si
    // deux personnes modifient la même annonce en même temps, la seconde reçoit une erreur
    // (traduite en 409 par ApiExceptionHandler) au lieu d'écraser la première.
    @Version
    private long version;

    // Constructeur exigé par JPA pour recréer l'objet depuis la base.
    protected Annonce() {
    }

    /**
     * Crée une nouvelle annonce, toujours en attente de modération.
     * Exemple : {@code new Annonce("Vélo", "Bon état", new BigDecimal("250"), categorie, userId)}.
     */
    public Annonce(String titre, String description, BigDecimal prix, Categorie categorie, String auteurId) {
        // UUID.randomUUID() (Java) : identifiant unique aléatoire.
        this.id = UUID.randomUUID();
        this.titre = titre;
        this.description = description;
        this.prix = prix;
        this.categorie = categorie;
        this.auteurId = auteurId;
        this.statut = StatutAnnonce.EN_ATTENTE;
        // Instant.now() (Java) : date et heure actuelles en UTC.
        this.dateCreation = Instant.now();
        this.dateModification = this.dateCreation;
    }

    /** Modifie le contenu ; l'annonce repasse en attente de modération et perd son éventuel motif de rejet. */
    public void modifier(String titre, String description, BigDecimal prix, Categorie categorie) {
        this.titre = titre;
        this.description = description;
        this.prix = prix;
        this.categorie = categorie;
        this.statut = StatutAnnonce.EN_ATTENTE;
        this.motifRejet = null;
        this.dateModification = Instant.now();
    }

    /** Publie l'annonce. Lève {@link ConflitException} (→ 409) si elle n'est pas en attente. */
    public void publier() {
        verifierEnAttente();
        this.statut = StatutAnnonce.PUBLIEE;
        this.dateModification = Instant.now();
    }

    /** Rejette l'annonce avec un motif. Lève {@link ConflitException} (→ 409) si elle n'est pas en attente. */
    public void rejeter(String motif) {
        verifierEnAttente();
        this.statut = StatutAnnonce.REJETEE;
        this.motifRejet = motif;
        this.dateModification = Instant.now();
    }

    // Seule une annonce en attente peut être publiée ou rejetée.
    private void verifierEnAttente() {
        if (statut != StatutAnnonce.EN_ATTENTE) {
            throw new ConflitException("Seule une annonce EN_ATTENTE peut être modérée (statut actuel : " + statut + ").");
        }
    }

    // Accesseurs en lecture seule : les modifications passent par les méthodes ci-dessus.

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
