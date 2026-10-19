package fr.petitesannonces.annonce;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrats JSON de l'API annonces. */
public final class AnnonceDtos {

    private AnnonceDtos() {
    }

    /**
     * Création / modification. Il n'y a volontairement PAS de champ {@code auteurId} : s'il est
     * envoyé, Jackson l'ignore. L'auteur vient toujours du contexte de sécurité (MT-01).
     */
    public record AnnonceRequest(
            @NotBlank @Size(min = 5, max = 100) String titre,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal prix,
            @NotNull Long categorieId) {
    }

    public record RejetRequest(@NotBlank @Size(max = 500) String motif) {
    }

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

        static AnnonceResponse from(Annonce a) {
            return new AnnonceResponse(a.getId(), a.getTitre(), a.getDescription(), a.getPrix(),
                    a.getCategorie().getId(), a.getStatut(), a.getAuteurId(), a.getMotifRejet(),
                    a.getDateCreation(), a.getDateModification());
        }
    }
}
