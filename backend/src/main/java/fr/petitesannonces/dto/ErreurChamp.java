package fr.petitesannonces.dto;

/** Un champ invalide, renvoyé dans la propriété « errors » d'un ProblemDetail 400. */
public record ErreurChamp(String champ, String message) {
}
