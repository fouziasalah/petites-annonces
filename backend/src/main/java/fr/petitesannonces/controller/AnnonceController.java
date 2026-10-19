package fr.petitesannonces.controller;

import fr.petitesannonces.service.AnnonceService;
import fr.petitesannonces.dto.AnnonceRequest;
import fr.petitesannonces.dto.AnnonceResponse;
import fr.petitesannonces.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Endpoints annonces. Chaque règle d'accès du tableau de la spec est lisible ici, en annotation.
 *
 * <p>{@code {id:UUID}} : la regex garantit que {@code /mes-annonces} ne peut jamais être pris
 * pour un identifiant (Spring privilégie déjà le segment littéral, la regex le rend explicite).
 *
 * <p>{@code @AuthenticationPrincipal String userId} : le principal posé par
 * {@code HeaderAuthenticationFilter}, c'est-à-dire le {@code sub} Keycloak reçu de Kong.
 */
@RestController
@RequestMapping("/api/annonces")
public class AnnonceController {

    static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final AnnonceService service;

    public AnnonceController(AnnonceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public PageResponse<AnnonceResponse> listerPubliees(
            @RequestParam(required = false) Long categorieId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.listerPubliees(categorieId, page, size);
    }

    @GetMapping("/mes-annonces")
    @PreAuthorize("hasRole('USER')")
    public PageResponse<AnnonceResponse> mesAnnonces(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.listerDeLAuteur(userId, page, size);
    }

    @GetMapping("/{id:" + UUID_REGEX + "}")
    @PreAuthorize("hasRole('USER')")
    public AnnonceResponse consulter(@PathVariable UUID id) {
        return service.consulter(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AnnonceResponse> creer(@Valid @RequestBody AnnonceRequest request,
                                                 @AuthenticationPrincipal String userId) {
        AnnonceResponse creee = service.creer(request, userId);
        // Location RELATIVE : derrière Kong, l'URL vue par le backend (http://backend:8081)
        // n'a aucun sens pour le navigateur. Une URI relative est valide (RFC 9110).
        return ResponseEntity.created(URI.create("/api/annonces/" + creee.id())).body(creee);
    }

    @PutMapping("/{id:" + UUID_REGEX + "}")
    @PreAuthorize("hasRole('USER') and @annonceSecurity.estAuteur(#id, authentication)")
    public AnnonceResponse modifier(@PathVariable UUID id, @Valid @RequestBody AnnonceRequest request) {
        return service.modifier(id, request);
    }

    /** ADMIN hérite de MODERATEUR (rôle composite dans Keycloak, déjà développé dans le token). */
    @DeleteMapping("/{id:" + UUID_REGEX + "}")
    @PreAuthorize("hasRole('MODERATEUR') or @annonceSecurity.estAuteur(#id, authentication)")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
