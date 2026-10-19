package fr.petitesannonces.exception;

/** L'action est incompatible avec l'état actuel des données. Traduite en 409. */
public class ConflitException extends RuntimeException {

    public ConflitException(String message) {
        super(message);
    }
}
