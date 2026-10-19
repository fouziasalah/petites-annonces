package fr.petitesannonces.exception;

/**
 * Levée quand un champ respecte les règles de format mais est refusé par le métier,
 * par exemple un {@code categorieId} qui ne correspond à aucune catégorie.
 *
 * <p>Transformée en réponse <b>400 Bad Request</b> par {@link ApiExceptionHandler}, avec le nom du
 * champ dans la liste {@code errors}.
 * Exemple : {@code throw new ChampInvalideException("categorieId", "Catégorie 999 inconnue.");}.
 */
public class ChampInvalideException extends RuntimeException {

    // Nom du champ en cause, renvoyé au client pour qu'il affiche l'erreur au bon endroit.
    private final String champ;

    public ChampInvalideException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
