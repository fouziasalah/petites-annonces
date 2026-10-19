package fr.petitesannonces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de {@code PATCH /api/moderation/annonces/{id}/rejeter} : le motif est obligatoire (MT-07). */
public record RejetRequest(@NotBlank @Size(max = 500) String motif) {
}
