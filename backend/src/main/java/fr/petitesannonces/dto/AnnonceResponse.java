package fr.petitesannonces.dto;

// Classes du projet (entités)
import fr.petitesannonces.entity.Annonce;
import fr.petitesannonces.entity.StatutAnnonce;
// Java standard
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Annonce telle qu'elle est renvoyée au client en JSON.
 *
 * <p>Rôle : séparer ce que l'API expose de l'entité JPA {@link Annonce}. On peut ainsi modifier
 * la base sans changer le contrat de l'API, et on évite de renvoyer des données internes
 * (version, relations chargées en différé…).
 *
 * <p>Utilisé par : les services, qui convertissent les entités avec {@link #from(Annonce)}.
 */
public record AnnonceResponse(
        UUID id,
        String titre,
        String description,
        BigDecimal prix,
        Long categorieId,
        // Statut sérialisé en texte : "EN_ATTENTE", "PUBLIEE" ou "REJETEE".
        StatutAnnonce statut,
        // Identifiant Keycloak (sub) de l'auteur.
        String auteurId,
        // Présent seulement si l'annonce a été rejetée.
        String motifRejet,
        // Instant (Java) : date et heure en UTC, sérialisées au format ISO 8601.
        Instant dateCreation,
        Instant dateModification) {

    /**
     * Convertit une entité en réponse JSON.
     * Exemple d'utilisation : {@code AnnonceResponse.from(annonce)}.
     */
    public static AnnonceResponse from(Annonce a) {
        // a.getCategorie().getId() ne déclenche pas de requête SQL : l'identifiant de la
        // catégorie est déjà connu, même si la catégorie n'est pas chargée.
        return new AnnonceResponse(a.getId(), a.getTitre(), a.getDescription(), a.getPrix(),
                a.getCategorie().getId(), a.getStatut(), a.getAuteurId(), a.getMotifRejet(),
                a.getDateCreation(), a.getDateModification());
    }
}
