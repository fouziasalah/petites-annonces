package fr.petitesannonces.dto;

// Jakarta Validation : contraintes sur les champs
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Données envoyées par un modérateur pour rejeter une annonce.
 *
 * <p>Rôle : recevoir le corps JSON de {@code PATCH /api/moderation/annonces/{id}/rejeter}.
 * Exemple : {@code {"motif": "Photo manquante"}}. Sans motif, la réponse est 400.
 */
public record RejetRequest(
        // Motif obligatoire, 500 caractères maximum (taille de la colonne en base).
        @NotBlank @Size(max = 500) String motif) {
}
