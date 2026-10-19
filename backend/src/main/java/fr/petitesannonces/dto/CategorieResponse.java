package fr.petitesannonces.dto;

// Classes du projet (entité)
import fr.petitesannonces.entity.Categorie;

/**
 * Catégorie telle qu'elle est renvoyée au client en JSON.
 * Exemple : {@code {"id": 2, "libelle": "Véhicules"}}.
 *
 * <p>Utilisé par : {@code CategorieService}, qui convertit les entités avec {@link #from(Categorie)}.
 */
public record CategorieResponse(Long id, String libelle) {

    /** Convertit une entité en réponse JSON. Exemple : {@code CategorieResponse.from(categorie)}. */
    public static CategorieResponse from(Categorie categorie) {
        return new CategorieResponse(categorie.getId(), categorie.getLibelle());
    }
}
