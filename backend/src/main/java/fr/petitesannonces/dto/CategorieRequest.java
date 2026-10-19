package fr.petitesannonces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategorieRequest(@NotBlank @Size(min = 2, max = 50) String libelle) {
}
