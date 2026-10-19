package fr.petitesannonces.dto;

// Java standard
import java.util.List;

/**
 * Réponse de {@code GET /api/me} : identité de l'utilisateur connecté.
 * Exemple : {@code {"userId": "3f2a…", "roles": ["MODERATEUR", "USER"]}}.
 *
 * <p>Utilisé par : {@code MeController}.
 */
public record MeResponse(
        // Identifiant Keycloak (sub) reçu dans l'en-tête X_USER_ID.
        String userId,
        // Rôles sans le préfixe "ROLE_".
        List<String> roles) {
}
