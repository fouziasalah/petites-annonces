package fr.petitesannonces.controller;

// Classes du projet
import fr.petitesannonces.dto.AnnonceResponse;
import fr.petitesannonces.dto.PageResponse;
import fr.petitesannonces.dto.RejetRequest;
import fr.petitesannonces.service.AnnonceService;
// Jakarta Validation : règles de validation
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
// Spring Security : contrôle d'accès
import org.springframework.security.access.prepost.PreAuthorize;
// Spring Web : déclaration des routes HTTP
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
// Java standard
import java.util.UUID;

// Import statique : permet d'écrire UUID_REGEX au lieu de AnnonceController.UUID_REGEX.
import static fr.petitesannonces.controller.AnnonceController.UUID_REGEX;

/**
 * API REST de modération : {@code /api/moderation/annonces}.
 *
 * <p>Rôle : permettre aux modérateurs de consulter les annonces en attente, puis de les publier
 * ou de les rejeter. Toutes les méthodes exigent le rôle {@code MODERATEUR}
 * (les administrateurs l'ont aussi, car ADMIN contient MODERATEUR dans Keycloak).
 *
 * <p>Utilisé par : l'écran « Modération » du front Angular.
 */
@RestController
@RequestMapping("/api/moderation/annonces")
public class ModerationController {

    private final AnnonceService service;

    // Injection par constructeur : Spring fournit le service.
    public ModerationController(AnnonceService service) {
        this.service = service;
    }

    /**
     * Liste les annonces en attente, les plus anciennes d'abord.
     * Exemple : {@code GET /api/moderation/annonces?page=0&size=20}.
     */
    @GetMapping
    @PreAuthorize("hasRole('MODERATEUR')")
    public PageResponse<AnnonceResponse> enAttente(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.listerEnAttente(page, size);
    }

    /**
     * Publie une annonce en attente : elle devient visible par tous.
     * Exemple : {@code PATCH /api/moderation/annonces/3f2a…/publier}.
     */
    // @PatchMapping (Spring Web) : répond aux requêtes PATCH (modification partielle).
    @PatchMapping("/{id:" + UUID_REGEX + "}/publier")
    @PreAuthorize("hasRole('MODERATEUR')")
    public AnnonceResponse publier(@PathVariable UUID id) {
        return service.publier(id);
    }

    /**
     * Rejette une annonce en attente, avec un motif obligatoire.
     * Exemple : {@code PATCH /api/moderation/annonces/3f2a…/rejeter} avec {@code {"motif": "…"}}.
     */
    @PatchMapping("/{id:" + UUID_REGEX + "}/rejeter")
    @PreAuthorize("hasRole('MODERATEUR')")
    // @Valid vérifie que le motif n'est pas vide (règle déclarée dans RejetRequest), sinon 400.
    public AnnonceResponse rejeter(@PathVariable UUID id, @Valid @RequestBody RejetRequest request) {
        // request.motif() : accesseur généré automatiquement par Java pour le champ du record.
        return service.rejeter(id, request.motif());
    }
}
