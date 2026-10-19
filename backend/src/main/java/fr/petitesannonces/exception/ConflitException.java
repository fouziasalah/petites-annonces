package fr.petitesannonces.exception;

/**
 * Levée quand une action est incompatible avec l'état actuel des données : catégorie déjà
 * existante ou encore utilisée, annonce qui n'est plus en attente…
 *
 * <p>Transformée en réponse <b>409 Conflict</b> par {@link ApiExceptionHandler}.
 * Exemple : {@code throw new ConflitException("La catégorie « Emploi » existe déjà.");}.
 */
public class ConflitException extends RuntimeException {

    public ConflitException(String message) {
        super(message);
    }
}
