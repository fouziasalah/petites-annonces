package fr.petitesannonces.categorie;

import fr.petitesannonces.annonce.AnnonceRepository;
import fr.petitesannonces.categorie.CategorieDtos.CategorieRequest;
import fr.petitesannonces.categorie.CategorieDtos.CategorieResponse;
import fr.petitesannonces.common.ConflitException;
import fr.petitesannonces.common.RessourceIntrouvableException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategorieService {

    private final CategorieRepository categories;
    private final AnnonceRepository annonces;

    public CategorieService(CategorieRepository categories, AnnonceRepository annonces) {
        this.categories = categories;
        this.annonces = annonces;
    }

    @Transactional(readOnly = true)
    public List<CategorieResponse> lister() {
        return categories.findAll(Sort.by("libelle")).stream().map(CategorieResponse::from).toList();
    }

    public CategorieResponse creer(CategorieRequest request) {
        String libelle = request.libelle().trim();
        if (categories.existsByLibelleIgnoreCase(libelle)) {
            throw new ConflitException("La catégorie « " + libelle + " » existe déjà.");
        }
        return CategorieResponse.from(categories.save(new Categorie(libelle)));
    }

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
