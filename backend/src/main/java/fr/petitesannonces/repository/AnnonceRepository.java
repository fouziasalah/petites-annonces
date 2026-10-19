package fr.petitesannonces.repository;

// Classes du projet (entités)
import fr.petitesannonces.entity.Annonce;
import fr.petitesannonces.entity.StatutAnnonce;
// Spring Data (dépendance spring-boot-starter-data-jpa) : pagination et repositories JPA
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
// Java standard
import java.util.Optional;
import java.util.UUID;

/**
 * Accès à la table {@code annonce}.
 *
 * <p>Rôle : lire et écrire les annonces en base sans écrire de SQL. C'est une <b>interface</b> :
 * Spring Data génère automatiquement son implémentation au démarrage.
 *
 * <p>Méthodes héritées de {@link JpaRepository} (sans rien écrire) : {@code findById(id)},
 * {@code save(annonce)}, {@code delete(annonce)}, {@code findAll()}, {@code count()}…
 *
 * <p>Pour les méthodes déclarées ici, Spring Data déduit la requête du <b>nom de la méthode</b> :
 * {@code findByStatut} → {@code WHERE statut = ?}, {@code AndCategorieId} → {@code AND categorie_id = ?}.
 *
 * <p>Utilisé par : {@code AnnonceService}, {@code CategorieService}, {@code AnnonceSecurity}.
 */
// JpaRepository<Annonce, UUID> : repository pour l'entité Annonce, dont la clé primaire est un UUID.
public interface AnnonceRepository extends JpaRepository<Annonce, UUID> {

    /**
     * Renvoie uniquement l'identifiant de l'auteur d'une annonce (vide si l'annonce n'existe pas).
     * Utilisée pour vérifier la propriété sans charger toute l'annonce.
     */
    // @Query (Spring Data) : requête écrite à la main en JPQL (langage de requête sur les entités Java).
    // :id est remplacé par le paramètre « id » de la méthode.
    @Query("select a.auteurId from Annonce a where a.id = :id")
    Optional<String> findAuteurIdById(UUID id);

    /** Annonces d'un statut donné, paginées et triées selon le {@link Pageable} reçu. */
    // @EntityGraph (Spring Data) : charge la catégorie dans la même requête SQL (jointure),
    // au lieu d'une requête supplémentaire par annonce (problème dit « N+1 »).
    @EntityGraph(attributePaths = "categorie")
    Page<Annonce> findByStatut(StatutAnnonce statut, Pageable pageable);

    /** Annonces d'un statut et d'une catégorie donnés (filtre de la liste publique). */
    @EntityGraph(attributePaths = "categorie")
    Page<Annonce> findByStatutAndCategorieId(StatutAnnonce statut, Long categorieId, Pageable pageable);

    /** Toutes les annonces d'un auteur, tous statuts confondus (« Mes annonces »). */
    @EntityGraph(attributePaths = "categorie")
    Page<Annonce> findByAuteurId(String auteurId, Pageable pageable);

    /** Vrai si au moins une annonce utilise cette catégorie (empêche sa suppression). */
    // existsBy… : Spring Data génère une requête « SELECT … LIMIT 1 », plus rapide qu'un comptage.
    boolean existsByCategorieId(Long categorieId);
}
