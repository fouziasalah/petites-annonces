package fr.petitesannonces.annonce;

import fr.petitesannonces.annonce.AnnonceDtos.AnnonceRequest;
import fr.petitesannonces.annonce.AnnonceDtos.AnnonceResponse;
import fr.petitesannonces.categorie.Categorie;
import fr.petitesannonces.categorie.CategorieRepository;
import fr.petitesannonces.common.ChampInvalideException;
import fr.petitesannonces.common.PageResponse;
import fr.petitesannonces.security.NotFoundWhenDenied;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Logique métier des annonces. Aucune vérification de rôle ici : les règles d'accès sont dans les
 * {@code @PreAuthorize} des contrôleurs, et la seule règle qui dépend du résultat (visibilité
 * d'une annonce) est un {@code @PostAuthorize} déclaratif.
 */
@Service
@Transactional
public class AnnonceService {

    private static final Sort PLUS_RECENTES = Sort.by(Sort.Direction.DESC, "dateCreation");
    private static final Sort PLUS_ANCIENNES = Sort.by(Sort.Direction.ASC, "dateCreation");

    private final AnnonceRepository annonces;
    private final CategorieRepository categories;

    public AnnonceService(AnnonceRepository annonces, CategorieRepository categories) {
        this.annonces = annonces;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnonceResponse> listerPubliees(Long categorieId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, PLUS_RECENTES);
        var resultat = categorieId == null
                ? annonces.findByStatut(StatutAnnonce.PUBLIEE, pageRequest)
                : annonces.findByStatutAndCategorieId(StatutAnnonce.PUBLIEE, categorieId, pageRequest);
        return PageResponse.of(resultat, AnnonceResponse::from);
    }

    /**
     * Visible si publiée, ou si l'on en est l'auteur, ou si l'on est modérateur.
     * Sinon le refus devient un 404 ({@link NotFoundWhenDenied}) : on ne révèle pas l'existence
     * d'une annonce non publiée (MT-02).
     */
    @Transactional(readOnly = true)
    @PostAuthorize("returnObject.statut().name() == 'PUBLIEE'"
            + " or returnObject.auteurId() == authentication.name"
            + " or hasRole('MODERATEUR')")
    @HandleAuthorizationDenied(handlerClass = NotFoundWhenDenied.class)
    public AnnonceResponse consulter(UUID id) {
        return AnnonceResponse.from(charger(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnonceResponse> listerDeLAuteur(String auteurId, int page, int size) {
        return PageResponse.of(annonces.findByAuteurId(auteurId, PageRequest.of(page, size, PLUS_RECENTES)),
                AnnonceResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnonceResponse> listerEnAttente(int page, int size) {
        return PageResponse.of(annonces.findByStatut(StatutAnnonce.EN_ATTENTE, PageRequest.of(page, size, PLUS_ANCIENNES)),
                AnnonceResponse::from);
    }

    public AnnonceResponse creer(AnnonceRequest request, String auteurId) {
        Annonce annonce = new Annonce(request.titre().trim(), request.description().trim(), request.prix(),
                categorie(request.categorieId()), auteurId);
        return AnnonceResponse.from(annonces.save(annonce));
    }

    public AnnonceResponse modifier(UUID id, AnnonceRequest request) {
        Annonce annonce = charger(id);
        annonce.modifier(request.titre().trim(), request.description().trim(), request.prix(),
                categorie(request.categorieId()));
        return AnnonceResponse.from(annonce);
    }

    public void supprimer(UUID id) {
        annonces.delete(charger(id));
    }

    public AnnonceResponse publier(UUID id) {
        Annonce annonce = charger(id);
        annonce.publier();
        return AnnonceResponse.from(annonce);
    }

    public AnnonceResponse rejeter(UUID id, String motif) {
        Annonce annonce = charger(id);
        annonce.rejeter(motif.trim());
        return AnnonceResponse.from(annonce);
    }

    private Annonce charger(UUID id) {
        return annonces.findById(id).orElseThrow(() -> new AnnonceIntrouvableException(id));
    }

    private Categorie categorie(Long categorieId) {
        return categories.findById(categorieId)
                .orElseThrow(() -> new ChampInvalideException("categorieId", "Catégorie " + categorieId + " inconnue."));
    }
}
