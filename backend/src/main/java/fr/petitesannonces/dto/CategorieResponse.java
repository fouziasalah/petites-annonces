package fr.petitesannonces.dto;

import fr.petitesannonces.entity.Categorie;

public record CategorieResponse(Long id, String libelle) {

    public static CategorieResponse from(Categorie categorie) {
        return new CategorieResponse(categorie.getId(), categorie.getLibelle());
    }
}
