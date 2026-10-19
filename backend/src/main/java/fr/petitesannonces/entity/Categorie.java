package fr.petitesannonces.entity;

// Jakarta Persistence (JPA, implémentée par Hibernate via spring-boot-starter-data-jpa)
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Catégorie d'annonce (Immobilier, Véhicules…), correspondant à la table {@code categorie}.
 *
 * <p>Rôle : représenter une ligne de la table en objet Java. La table est créée par la migration
 * Flyway {@code V1__schema_initial.sql}, et les 4 catégories initiales par {@code V2}.
 *
 * <p>Utilisé par : {@code CategorieRepository} (lecture et écriture en base), {@link Annonce}
 * (chaque annonce a une catégorie).
 */
// @Entity (JPA) : cette classe correspond à une table ; par défaut, le nom de la table est
// celui de la classe (categorie).
@Entity
public class Categorie {

    // @Id (JPA) : clé primaire.
    @Id
    // @GeneratedValue IDENTITY (JPA) : la valeur est générée par la base (colonne auto-incrémentée).
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column (JPA) : colonne obligatoire (nullable = false), 50 caractères maximum.
    @Column(nullable = false, length = 50)
    private String libelle;

    // Constructeur sans argument exigé par JPA pour recréer l'objet à partir d'une ligne en base.
    // « protected » : le reste du code doit utiliser le constructeur ci-dessous.
    protected Categorie() {
    }

    /** Crée une nouvelle catégorie (l'identifiant sera attribué par la base à l'enregistrement). */
    public Categorie(String libelle) {
        this.libelle = libelle;
    }

    public Long getId() {
        return id;
    }

    public String getLibelle() {
        return libelle;
    }
}
