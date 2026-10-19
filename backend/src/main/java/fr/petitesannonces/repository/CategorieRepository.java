package fr.petitesannonces.repository;

// Classes du projet (entité)
import fr.petitesannonces.entity.Categorie;
// Spring Data : tri et repositories JPA
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
// Java standard
import java.util.List;

/**
 * Accès à la table {@code categorie}.
 *
 * <p>Rôle : lire et écrire les catégories en base. Interface dont Spring Data génère
 * l'implémentation ; {@code findById}, {@code save}, {@code deleteById}, {@code existsById}…
 * sont hérités de {@link JpaRepository}.
 *
 * <p>Utilisé par : {@code CategorieService}, {@code AnnonceService}.
 */
public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    /**
     * Vrai si une catégorie porte déjà ce libellé, sans tenir compte des majuscules.
     * Le nom suffit à Spring Data : {@code WHERE upper(libelle) = upper(?)}.
     */
    boolean existsByLibelleIgnoreCase(String libelle);

    /**
     * Toutes les catégories dans l'ordre demandé. Exemple : {@code findAll(Sort.by("libelle"))}.
     */
    // Redéclarée ici pour renvoyer une List (la version héritée renvoie un Iterable).
    List<Categorie> findAll(Sort sort);
}
