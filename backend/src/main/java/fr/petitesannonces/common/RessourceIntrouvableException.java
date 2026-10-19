package fr.petitesannonces.common;

/** Traduite en 404 par {@link ApiExceptionHandler}. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
