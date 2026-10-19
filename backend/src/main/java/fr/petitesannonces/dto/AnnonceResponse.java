package fr.petitesannonces.dto;

import fr.petitesannonces.entity.Annonce;
import fr.petitesannonces.entity.StatutAnnonce;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Annonce telle qu'exposée par l'API : l'entité JPA ne sort jamais du backend. */
public record AnnonceResponse(
        UUID id,
        String titre,
        String description,
        BigDecimal prix,
        Long categorieId,
        StatutAnnonce statut,
        String auteurId,
        String motifRejet,
        Instant dateCreation,
        Instant dateModification) {

    public static AnnonceResponse from(Annonce a) {
        return new AnnonceResponse(a.getId(), a.getTitre(), a.getDescription(), a.getPrix(),
                a.getCategorie().getId(), a.getStatut(), a.getAuteurId(), a.getMotifRejet(),
                a.getDateCreation(), a.getDateModification());
    }
}
