package fr.petitesannonces.annonce;

import fr.petitesannonces.annonce.AnnonceDtos.AnnonceResponse;
import fr.petitesannonces.annonce.AnnonceDtos.RejetRequest;
import fr.petitesannonces.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static fr.petitesannonces.annonce.AnnonceController.UUID_REGEX;

@RestController
@RequestMapping("/api/moderation/annonces")
public class ModerationController {

    private final AnnonceService service;

    public ModerationController(AnnonceService service) {
        this.service = service;
    }

    /** Annonces en attente, les plus anciennes d'abord (file d'attente de modération). */
    @GetMapping
    @PreAuthorize("hasRole('MODERATEUR')")
    public PageResponse<AnnonceResponse> enAttente(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.listerEnAttente(page, size);
    }

    @PatchMapping("/{id:" + UUID_REGEX + "}/publier")
    @PreAuthorize("hasRole('MODERATEUR')")
    public AnnonceResponse publier(@PathVariable UUID id) {
        return service.publier(id);
    }

    @PatchMapping("/{id:" + UUID_REGEX + "}/rejeter")
    @PreAuthorize("hasRole('MODERATEUR')")
    public AnnonceResponse rejeter(@PathVariable UUID id, @Valid @RequestBody RejetRequest request) {
        return service.rejeter(id, request.motif());
    }
}
