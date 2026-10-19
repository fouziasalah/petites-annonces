package fr.petitesannonces.security;

import fr.petitesannonces.annonce.AnnonceIntrouvableException;
import fr.petitesannonces.annonce.AnnonceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Règles de propriété utilisables dans les expressions de sécurité :
 * {@code @PreAuthorize("@annonceSecurity.estAuteur(#id, authentication)")}.
 *
 * <p>Une règle de propriété dépend des données (qui a créé l'annonce ?), elle ne peut donc pas
 * s'exprimer avec un simple {@code hasRole}. La placer dans ce bean dédié la garde dans la
 * couche sécurité, hors du code métier (BK-04).
 */
@Component("annonceSecurity")
public class AnnonceSecurity {

    private final AnnonceRepository annonces;

    public AnnonceSecurity(AnnonceRepository annonces) {
        this.annonces = annonces;
    }

    /**
     * @return {@code true} si l'utilisateur courant est l'auteur de l'annonce.
     * @throws AnnonceIntrouvableException si l'annonce n'existe pas : on répond 404 plutôt
     *         que 403 (« refusé » laisserait croire qu'il y a quelque chose à protéger).
     */
    @Transactional(readOnly = true)
    public boolean estAuteur(UUID id, Authentication authentication) {
        String auteurId = annonces.findAuteurIdById(id).orElseThrow(() -> new AnnonceIntrouvableException(id));
        return auteurId.equals(authentication.getName());
    }
}
