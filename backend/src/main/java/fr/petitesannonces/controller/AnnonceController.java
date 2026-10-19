package fr.petitesannonces.controller;

// Classes du projet
import fr.petitesannonces.dto.AnnonceRequest;
import fr.petitesannonces.dto.AnnonceResponse;
import fr.petitesannonces.dto.PageResponse;
import fr.petitesannonces.service.AnnonceService;
// Jakarta Validation (dépendance spring-boot-starter-validation) : règles de validation
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
// Spring Web : réponses HTTP
import org.springframework.http.ResponseEntity;
// Spring Security : contrôle d'accès et lecture de l'utilisateur connecté
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
// Spring Web : déclaration des routes HTTP et lecture des paramètres
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
// Java standard
import java.net.URI;
import java.util.UUID;

/**
 * API REST des annonces : {@code /api/annonces}.
 *
 * <p>Rôle : recevoir les requêtes HTTP, vérifier les droits ({@code @PreAuthorize}) et la validité
 * des données ({@code @Valid}), puis déléguer le travail à {@link AnnonceService}. Le contrôleur ne
 * contient aucune logique métier.
 *
 * <p>Utilisé par : le front Angular, via la gateway, par exemple
 * {@code GET /api/annonces?page=0&size=10&categorieId=2}.
 */
// @RestController (Spring Web) : chaque méthode répond à une requête HTTP et son résultat est
// converti automatiquement en JSON (par la bibliothèque Jackson).
@RestController
// @RequestMapping (Spring Web) : préfixe commun à toutes les routes de cette classe.
@RequestMapping("/api/annonces")
public class AnnonceController {

    /**
     * Expression régulière d'un UUID (ex. 3f2a…-…). Utilisée dans les routes pour que
     * {@code /api/annonces/mes-annonces} ne soit jamais confondu avec {@code /api/annonces/{id}}.
     * Aussi utilisée par {@link ModerationController}.
     */
    static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    // Service métier auquel le contrôleur délègue tout le travail.
    private final AnnonceService service;

    // Injection par constructeur : Spring fournit automatiquement l'objet AnnonceService
    // (une classe @Service) lors de la création du contrôleur.
    public AnnonceController(AnnonceService service) {
        this.service = service;
    }

    /**
     * Liste les annonces publiées, de la plus récente à la plus ancienne.
     * Exemple : {@code GET /api/annonces?page=0&size=10&categorieId=2}.
     */
    // @GetMapping (Spring Web) : répond aux requêtes GET sur /api/annonces.
    @GetMapping
    // @PreAuthorize (Spring Security) : la méthode n'est exécutée que si l'expression est vraie.
    // hasRole('USER') vérifie que l'utilisateur possède l'autorité ROLE_USER, sinon 403.
    @PreAuthorize("hasRole('USER')")
    public PageResponse<AnnonceResponse> listerPubliees(
            // @RequestParam (Spring Web) : lit un paramètre de l'URL (?categorieId=…) ; facultatif ici.
            @RequestParam(required = false) Long categorieId,
            // page vaut 0 si absent ; @Min(0) (Jakarta Validation) refuse un nombre négatif (400).
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // size vaut 20 si absent ; doit être entre 1 et 50, sinon 400.
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.listerPubliees(categorieId, page, size);
    }

    /**
     * Liste toutes les annonces de l'utilisateur connecté, quel que soit leur statut.
     * Exemple : {@code GET /api/annonces/mes-annonces}.
     */
    @GetMapping("/mes-annonces")
    @PreAuthorize("hasRole('USER')")
    public PageResponse<AnnonceResponse> mesAnnonces(
            // @AuthenticationPrincipal (Spring Security) : injecte l'identifiant de l'utilisateur
            // connecté, placé dans le contexte de sécurité par HeaderAuthenticationFilter.
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.listerDeLAuteur(userId, page, size);
    }

    /**
     * Renvoie une annonce. Si elle n'est pas visible pour l'utilisateur, répond 404.
     * Exemple : {@code GET /api/annonces/3f2a…}.
     */
    // {id:<regex>} : la partie de l'URL n'est acceptée que si elle a la forme d'un UUID.
    @GetMapping("/{id:" + UUID_REGEX + "}")
    @PreAuthorize("hasRole('USER')")
    // @PathVariable (Spring Web) : récupère la valeur {id} de l'URL et la convertit en UUID.
    public AnnonceResponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    /**
     * Crée une annonce pour l'utilisateur connecté. Répond 201 avec l'en-tête {@code Location}.
     * Exemple : {@code POST /api/annonces} avec un corps JSON {@code {"titre": …, "prix": …}}.
     */
    // @PostMapping (Spring Web) : répond aux requêtes POST sur /api/annonces.
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    // @RequestBody (Spring Web) : convertit le corps JSON de la requête en objet AnnonceRequest.
    // @Valid (Jakarta Validation) : vérifie les règles déclarées dans AnnonceRequest, sinon 400.
    public ResponseEntity<AnnonceResponse> creer(@Valid @RequestBody AnnonceRequest request,
                                                 @AuthenticationPrincipal String userId) {
        // L'auteur est toujours l'utilisateur connecté, jamais une valeur envoyée par le client.
        AnnonceResponse creee = service.creer(request, userId);
        // ResponseEntity.created (Spring Web) : réponse 201 Created avec l'en-tête Location.
        // L'URL est relative car, derrière la gateway, l'adresse interne du backend
        // n'a pas de sens pour le navigateur.
        return ResponseEntity.created(URI.create("/api/annonces/" + creee.id())).body(creee);
    }

    /**
     * Modifie une annonce (réservé à son auteur). L'annonce repasse en attente de modération.
     * Exemple : {@code PUT /api/annonces/3f2a…} avec le même corps JSON que la création.
     */
    // @PutMapping (Spring Web) : répond aux requêtes PUT (remplacement d'une ressource).
    @PutMapping("/{id:" + UUID_REGEX + "}")
    // @annonceSecurity.estAuteur(...) appelle la méthode estAuteur du bean AnnonceSecurity ;
    // #id désigne le paramètre « id » de cette méthode, « authentication » l'utilisateur connecté.
    @PreAuthorize("hasRole('USER') and @annonceSecurity.estAuteur(#id, authentication)")
    public AnnonceResponse modifier(@PathVariable UUID id, @Valid @RequestBody AnnonceRequest request) {
        return service.modifier(id, request);
    }

    /**
     * Supprime une annonce : autorisé à son auteur, aux modérateurs et aux administrateurs
     * (ADMIN contient MODERATEUR dans Keycloak). Répond 204 No Content.
     */
    // @DeleteMapping (Spring Web) : répond aux requêtes DELETE.
    @DeleteMapping("/{id:" + UUID_REGEX + "}")
    @PreAuthorize("hasRole('MODERATEUR') or @annonceSecurity.estAuteur(#id, authentication)")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        // ResponseEntity.noContent() : réponse 204, sans corps.
        return ResponseEntity.noContent().build();
    }
}
