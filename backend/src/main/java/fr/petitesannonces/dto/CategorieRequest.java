package fr.petitesannonces.dto;

// Jakarta Validation : contraintes sur les champs
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Données envoyées par un administrateur pour créer une catégorie.
 *
 * <p>Rôle : recevoir le corps JSON de {@code POST /api/categories}.
 * Exemple : {@code {"libelle": "Jardinage"}}.
 */
public record CategorieRequest(
        // Obligatoire, entre 2 et 50 caractères, sinon 400.
        @NotBlank @Size(min = 2, max = 50) String libelle) {
}
