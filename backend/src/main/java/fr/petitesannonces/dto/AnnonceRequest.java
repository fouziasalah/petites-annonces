package fr.petitesannonces.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Création / modification d'une annonce. Il n'y a volontairement PAS de champ {@code auteurId} :
 * s'il est envoyé, Jackson l'ignore. L'auteur vient toujours du contexte de sécurité (MT-01).
 */
public record AnnonceRequest(
        @NotBlank @Size(min = 5, max = 100) String titre,
        @NotBlank @Size(max = 2000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal prix,
        @NotNull Long categorieId) {
}
