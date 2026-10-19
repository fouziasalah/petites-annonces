package fr.petitesannonces.categorie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contrats JSON de l'API catégories : les entités JPA ne sortent jamais du backend. */
public final class CategorieDtos {

    private CategorieDtos() {
    }

    public record CategorieRequest(
            @NotBlank @Size(min = 2, max = 50) String libelle) {
    }

    public record CategorieResponse(Long id, String libelle) {

        static CategorieResponse from(Categorie categorie) {
            return new CategorieResponse(categorie.getId(), categorie.getLibelle());
        }
    }
}
