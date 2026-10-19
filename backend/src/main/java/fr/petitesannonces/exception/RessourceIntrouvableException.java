package fr.petitesannonces.exception;

/**
 * Levée quand une ressource demandée n'existe pas.
 *
 * <p>Transformée en réponse <b>404 Not Found</b> par {@link ApiExceptionHandler}.
 * Exemple : {@code throw new RessourceIntrouvableException("Catégorie 5 introuvable.");}.
 */
// extends RuntimeException (Java) : exception « non vérifiée », qu'on n'est pas obligé de
// déclarer avec « throws » ; elle remonte d'elle-même jusqu'à ApiExceptionHandler.
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        // super(message) : transmet le message à RuntimeException ; il deviendra le « detail » du JSON.
        super(message);
    }
}
