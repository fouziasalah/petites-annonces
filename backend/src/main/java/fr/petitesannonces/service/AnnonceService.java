package fr.petitesannonces.service;

// Classes du projet
import fr.petitesannonces.dto.AnnonceRequest;
import fr.petitesannonces.dto.AnnonceResponse;
import fr.petitesannonces.dto.PageResponse;
import fr.petitesannonces.entity.Annonce;
import fr.petitesannonces.entity.Categorie;
import fr.petitesannonces.entity.StatutAnnonce;
import fr.petitesannonces.exception.AnnonceIntrouvableException;
import fr.petitesannonces.exception.ChampInvalideException;
import fr.petitesannonces.repository.AnnonceRepository;
import fr.petitesannonces.repository.CategorieRepository;
import fr.petitesannonces.security.NotFoundWhenDenied;
// Spring Data : pagination et tri
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
// Spring Security : contrôle d'accès après exécution
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.authorization.method.HandleAuthorizationDenied;
// Spring Framework : déclaration de service et transactions
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Java standard
import java.util.UUID;

/**
 * Logique métier des annonces : création, modification, suppression, modération, listes.
 *
 * <p>Rôle : faire le lien entre les contrôleurs et la base. Le service charge les entités via les
 * repositories, appelle leurs méthodes métier ({@code publier()}, {@code modifier()}…) et convertit
 * le résultat en DTO. Il ne vérifie aucun rôle : c'est le travail des {@code @PreAuthorize}
 * des contrôleurs.
 *
 * <p>Utilisé par : {@code AnnonceController}, {@code ModerationController}.
 */
// @Service (Spring) : Spring crée une seule instance de cette classe et l'injecte là où on la demande.
@Service
// @Transactional (Spring) : chaque méthode publique s'exécute dans une transaction de base de
// données ; si une exception est levée, toutes ses écritures sont annulées (rollback).
@Transactional
public class AnnonceService {

    // Tri par date de création décroissante : les plus récentes d'abord.
    private static final Sort PLUS_RECENTES = Sort.by(Sort.Direction.DESC, "dateCreation");
    // Tri par date de création croissante : les plus anciennes d'abord (file de modération).
    private static final Sort PLUS_ANCIENNES = Sort.by(Sort.Direction.ASC, "dateCreation");

    private final AnnonceRepository annonces;
    private final CategorieRepository categories;

    // Injection par constructeur : Spring fournit les deux repositories.
    public AnnonceService(AnnonceRepository annonces, CategorieRepository categories) {
        this.annonces = annonces;
        this.categories = categories;
    }

    /**
     * Annonces publiées, éventuellement filtrées par catégorie.
     *
     * @param categorieId catégorie à filtrer, ou {@code null} pour toutes
     * @param page        numéro de page (0 = première)
     * @param size        nombre d'annonces par page
     */
    // readOnly = true : transaction en lecture seule, que Hibernate peut optimiser.
    @Transactional(readOnly = true)
    public PageResponse<AnnonceResponse> listerPubliees(Long categorieId, int page, int size) {
        // PageRequest.of (Spring Data) : demande de la page « page », de taille « size », avec un tri.
        PageRequest pageRequest = PageRequest.of(page, size, PLUS_RECENTES);
        // Avec ou sans filtre de catégorie selon le paramètre reçu.
        var resultat = categorieId == null
                ? annonces.findByStatut(StatutAnnonce.PUBLIEE, pageRequest)
                : annonces.findByStatutAndCategorieId(StatutAnnonce.PUBLIEE, categorieId, pageRequest);
        // Conversion des entités en DTO ; AnnonceResponse::from est une référence de méthode Java.
        return PageResponse.of(resultat, AnnonceResponse::from);
    }

    /**
     * Renvoie une annonce si l'utilisateur a le droit de la voir, sinon répond 404.
     *
     * <p>Visible si : elle est publiée, OU l'utilisateur en est l'auteur, OU il est modérateur.
     */
    @Transactional(readOnly = true)
    // @PostAuthorize (Spring Security) : l'expression est vérifiée APRÈS l'exécution de la méthode,
    // sur son résultat. « returnObject » désigne la valeur renvoyée (ici un AnnonceResponse) et
    // « authentication » l'utilisateur connecté.
    @PostAuthorize("returnObject.statut().name() == 'PUBLIEE'"
            + " or returnObject.auteurId() == authentication.name"
            + " or hasRole('MODERATEUR')")
    // @HandleAuthorizationDenied (Spring Security 6.3+) : en cas de refus, au lieu du 403 habituel,
    // on délègue à NotFoundWhenDenied qui répond 404, pour ne pas révéler que l'annonce existe.
    @HandleAuthorizationDenied(handlerClass = NotFoundWhenDenied.class)
    public AnnonceResponse consulter(UUID id) {
        return AnnonceResponse.from(charger(id));
    }

    /** Toutes les annonces d'un auteur, des plus récentes aux plus anciennes. */
    @Transactional(readOnly = true)
    public PageResponse<AnnonceResponse> listerDeLAuteur(String auteurId, int page, int size) {
        return PageResponse.of(annonces.findByAuteurId(auteurId, PageRequest.of(page, size, PLUS_RECENTES)),
                AnnonceResponse::from);
    }

    /** Annonces en attente de modération, des plus anciennes aux plus récentes. */
    @Transactional(readOnly = true)
    public PageResponse<AnnonceResponse> listerEnAttente(int page, int size) {
        return PageResponse.of(annonces.findByStatut(StatutAnnonce.EN_ATTENTE, PageRequest.of(page, size, PLUS_ANCIENNES)),
                AnnonceResponse::from);
    }

    /**
     * Crée une annonce en attente de modération.
     *
     * @param auteurId identifiant de l'utilisateur connecté (fourni par le contrôleur)
     */
    public AnnonceResponse creer(AnnonceRequest request, String auteurId) {
        // trim() (Java) : retire les espaces en début et fin de texte.
        Annonce annonce = new Annonce(request.titre().trim(), request.description().trim(), request.prix(),
                categorie(request.categorieId()), auteurId);
        // save (Spring Data) : INSERT en base ; renvoie l'entité enregistrée.
        return AnnonceResponse.from(annonces.save(annonce));
    }

    /** Modifie une annonce existante ; elle repasse en attente de modération. */
    public AnnonceResponse modifier(UUID id, AnnonceRequest request) {
        Annonce annonce = charger(id);
        annonce.modifier(request.titre().trim(), request.description().trim(), request.prix(),
                categorie(request.categorieId()));
        // Pas besoin d'appeler save : dans une transaction, JPA détecte les modifications de
        // l'entité chargée et fait l'UPDATE automatiquement à la fin de la méthode.
        return AnnonceResponse.from(annonce);
    }

    /** Supprime une annonce (404 si elle n'existe pas). */
    public void supprimer(UUID id) {
        // delete (Spring Data) : DELETE en base.
        annonces.delete(charger(id));
    }

    /** Publie une annonce en attente (409 si elle n'est pas en attente). */
    public AnnonceResponse publier(UUID id) {
        Annonce annonce = charger(id);
        annonce.publier();
        return AnnonceResponse.from(annonce);
    }

    /** Rejette une annonce en attente avec un motif (409 si elle n'est pas en attente). */
    public AnnonceResponse rejeter(UUID id, String motif) {
        Annonce annonce = charger(id);
        annonce.rejeter(motif.trim());
        return AnnonceResponse.from(annonce);
    }

    // Charge une annonce ou lève AnnonceIntrouvableException (→ 404).
    private Annonce charger(UUID id) {
        // findById (Spring Data) renvoie un Optional ; orElseThrow (Java) lève l'exception s'il est vide.
        return annonces.findById(id).orElseThrow(() -> new AnnonceIntrouvableException(id));
    }

    // Charge une catégorie ou lève ChampInvalideException (→ 400 sur le champ categorieId).
    private Categorie categorie(Long categorieId) {
        return categories.findById(categorieId)
                .orElseThrow(() -> new ChampInvalideException("categorieId", "Catégorie " + categorieId + " inconnue."));
    }
}
