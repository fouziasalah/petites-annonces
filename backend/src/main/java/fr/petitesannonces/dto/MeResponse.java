package fr.petitesannonces.dto;

import java.util.List;

/** Identité telle que le backend la voit : {@code sub} Keycloak et rôles métier. */
public record MeResponse(String userId, List<String> roles) {
}
