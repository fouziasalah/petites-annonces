package fr.petitesannonces.security;

// Classes du projet
import fr.petitesannonces.exception.AnnonceIntrouvableException;
import fr.petitesannonces.repository.AnnonceRepository;
// Spring Security : utilisateur connecté
import org.springframework.security.core.Authentication;
// Spring Framework : composant et transactions
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
// Java standard
import java.util.UUID;

/**
 * Règle de sécurité « l'utilisateur est-il l'auteur de cette annonce ? ».
 *
 * <p>Pourquoi un bean à part ? Cette règle dépend des données en base, elle ne peut pas s'écrire
 * avec un simple {@code hasRole(...)}. On l'appelle depuis une annotation de sécurité :
 * <pre>@PreAuthorize("@annonceSecurity.estAuteur(#id, authentication)")</pre>
 * {@code @annonceSecurity} désigne ce bean par son nom, {@code #id} le paramètre de la méthode
 * annotée, et {@code authentication} l'utilisateur connecté.
 *
 * <p>Utilisé par : {@code AnnonceController} (modification et suppression d'une annonce).
 */
// @Component("annonceSecurity") (Spring) : bean nommé « annonceSecurity », nom utilisé dans les
// expressions @PreAuthorize.
@Component("annonceSecurity")
public class AnnonceSecurity {

    private final AnnonceRepository annonces;

    // Injection par constructeur : Spring fournit le repository.
    public AnnonceSecurity(AnnonceRepository annonces) {
        this.annonces = annonces;
    }

    /**
     * @return {@code true} si l'utilisateur connecté a créé l'annonce
     * @throws AnnonceIntrouvableException si l'annonce n'existe pas : on répond 404 plutôt que 403,
     *         car « refusé » laisserait croire qu'il y a quelque chose à protéger
     */
    @Transactional(readOnly = true)
    public boolean estAuteur(UUID id, Authentication authentication) {
        // On ne lit que l'auteur, sans charger toute l'annonce.
        String auteurId = annonces.findAuteurIdById(id).orElseThrow(() -> new AnnonceIntrouvableException(id));
        // authentication.getName() : identifiant de l'utilisateur connecté (valeur de X_USER_ID).
        return auteurId.equals(authentication.getName());
    }
}
