package fr.petitesannonces.controller;

// Classes du projet
import fr.petitesannonces.dto.CategorieRequest;
import fr.petitesannonces.dto.CategorieResponse;
import fr.petitesannonces.service.CategorieService;
// Jakarta Validation
import jakarta.validation.Valid;
// Spring Web : réponses HTTP
import org.springframework.http.ResponseEntity;
// Spring Security : contrôle d'accès
import org.springframework.security.access.prepost.PreAuthorize;
// Spring Web : déclaration des routes HTTP
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// Java standard
import java.net.URI;
import java.util.List;

/**
 * API REST des catégories : {@code /api/categories}.
 *
 * <p>Rôle : lister les catégories (tout utilisateur), en créer ou en supprimer (administrateurs).
 *
 * <p>Utilisé par : le front Angular (liste déroulante des catégories, écran « Catégories »).
 */
@RestController
@RequestMapping("/api/categories")
public class CategorieController {

    private final CategorieService service;

    // Injection par constructeur : Spring fournit le service.
    public CategorieController(CategorieService service) {
        this.service = service;
    }

    /** Liste toutes les catégories, triées par libellé. Exemple : {@code GET /api/categories}. */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<CategorieResponse> lister() {
        return service.lister();
    }

    /**
     * Crée une catégorie. Répond 201, ou 409 si le libellé existe déjà.
     * Exemple : {@code POST /api/categories} avec {@code {"libelle": "Jardinage"}}.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategorieResponse> creer(@Valid @RequestBody CategorieRequest request) {
        CategorieResponse creee = service.creer(request);
        // 201 Created, avec l'adresse de la nouvelle catégorie dans l'en-tête Location.
        return ResponseEntity.created(URI.create("/api/categories/" + creee.id())).body(creee);
    }

    /**
     * Supprime une catégorie. Répond 204, 404 si elle n'existe pas, 409 si des annonces l'utilisent.
     * Exemple : {@code DELETE /api/categories/5}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
