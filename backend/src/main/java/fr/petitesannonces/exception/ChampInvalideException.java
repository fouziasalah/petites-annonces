package fr.petitesannonces.exception;

/** Un champ de la requête est syntaxiquement valide mais inacceptable (ex. catégorie inconnue). Traduite en 400. */
public class ChampInvalideException extends RuntimeException {

    private final String champ;

    public ChampInvalideException(String champ, String message) {
        super(message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
