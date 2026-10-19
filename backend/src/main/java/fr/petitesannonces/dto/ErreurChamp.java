package fr.petitesannonces.dto;

/**
 * Un champ invalide dans une requête, renvoyé dans la liste {@code errors} d'une erreur 400.
 * Exemple : {@code {"champ": "titre", "message": "la taille doit être comprise entre 5 et 100"}}.
 *
 * <p>Utilisé par : {@code ApiExceptionHandler}, qui construit les réponses d'erreur.
 */
public record ErreurChamp(
        // Nom du champ en cause (titre, prix, size…).
        String champ,
        // Message d'erreur en français.
        String message) {
}
