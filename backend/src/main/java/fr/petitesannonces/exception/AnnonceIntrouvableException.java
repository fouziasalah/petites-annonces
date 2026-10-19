package fr.petitesannonces.exception;

// Java standard
import java.util.UUID;

/**
 * Levée quand une annonce n'existe pas, ou n'est pas visible pour l'utilisateur.
 *
 * <p>Sous-classe de {@link RessourceIntrouvableException} : elle est donc aussi transformée en
 * <b>404</b> par {@link ApiExceptionHandler}. Exemple : {@code throw new AnnonceIntrouvableException(id);}.
 *
 * <p>Utilisée par : {@code AnnonceService}, {@code AnnonceSecurity}, {@code NotFoundWhenDenied}.
 */
public class AnnonceIntrouvableException extends RessourceIntrouvableException {

    public AnnonceIntrouvableException(UUID id) {
        super("Annonce " + id + " introuvable.");
    }
}
