package fr.petitesannonces.service;

// Classes du projet
import fr.petitesannonces.dto.CategorieRequest;
import fr.petitesannonces.dto.CategorieResponse;
import fr.petitesannonces.entity.Categorie;
import fr.petitesannonces.exception.ConflitException;
import fr.petitesannonces.exception.RessourceIntrouvableException;
import fr.petitesannonces.repository.AnnonceRepository;
import fr.petitesannonces.repository.CategorieRepository;
// Spring Data : tri
import org.springframework.data.domain.Sort;
// Spring Framework : déclaration de service et transactions
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Java standard
import java.util.List;

/**
 * Logique métier des catégories : lister, créer, supprimer.
 *
 * <p>Rôle : appliquer les règles métier (libellé unique, catégorie utilisée non supprimable)
 * avant d'écrire en base.
 *
 * <p>Utilisé par : {@code CategorieController}.
 */
@Service
@Transactional
public class CategorieService {

    private final CategorieRepository categories;
    // Nécessaire pour savoir si une catégorie est encore utilisée par des annonces.
    private final AnnonceRepository annonces;

    // Injection par constructeur : Spring fournit les deux repositories.
    public CategorieService(CategorieRepository categories, AnnonceRepository annonces) {
        this.categories = categories;
        this.annonces = annonces;
    }

    /** Toutes les catégories, triées par libellé. */
    @Transactional(readOnly = true)
    public List<CategorieResponse> lister() {
        // Sort.by("libelle") (Spring Data) : ORDER BY libelle.
        return categories.findAll(Sort.by("libelle")).stream().map(CategorieResponse::from).toList();
    }

    /** Crée une catégorie ; lève {@link ConflitException} (→ 409) si le libellé existe déjà. */
    public CategorieResponse creer(CategorieRequest request) {
        String libelle = request.libelle().trim();
        if (categories.existsByLibelleIgnoreCase(libelle)) {
            throw new ConflitException("La catégorie « " + libelle + " » existe déjà.");
        }
        return CategorieResponse.from(categories.save(new Categorie(libelle)));
    }

    /**
     * Supprime une catégorie : 404 si elle n'existe pas, 409 si des annonces l'utilisent encore.
     */
    public void supprimer(Long id) {
        if (!categories.existsById(id)) {
            throw new RessourceIntrouvableException("Catégorie " + id + " introuvable.");
        }
        if (annonces.existsByCategorieId(id)) {
            throw new ConflitException("La catégorie " + id + " est utilisée par au moins une annonce.");
        }
        categories.deleteById(id);
    }
}
