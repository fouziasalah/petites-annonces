package fr.petitesannonces.dto;

// Jakarta Validation (dépendance spring-boot-starter-validation) : contraintes sur les champs
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
// Java standard : nombre décimal exact (adapté aux montants)
import java.math.BigDecimal;

/**
 * Données envoyées par le client pour créer ou modifier une annonce.
 *
 * <p>Rôle : recevoir le corps JSON de {@code POST /api/annonces} et {@code PUT /api/annonces/{id}}.
 * Les annotations décrivent les règles ; elles sont vérifiées quand le contrôleur écrit
 * {@code @Valid}. Une règle non respectée renvoie une erreur 400 qui indique le champ en cause.
 *
 * <p>Il n'y a volontairement pas de champ {@code auteurId} : s'il est envoyé, il est ignoré.
 * L'auteur est toujours l'utilisateur connecté.
 *
 * <p>Exemple JSON : {@code {"titre": "Vélo de course", "description": "Très bon état",
 * "prix": 250.00, "categorieId": 2}}.
 */
// record (Java 16+) : classe immuable dont Java génère le constructeur, les accesseurs
// (titre(), prix()…), equals, hashCode et toString.
public record AnnonceRequest(
        // @NotBlank : obligatoire et pas seulement des espaces ; @Size : entre 5 et 100 caractères.
        @NotBlank @Size(min = 5, max = 100) String titre,
        // Obligatoire, 2000 caractères maximum.
        @NotBlank @Size(max = 2000) String description,
        // @NotNull : obligatoire ; @DecimalMin("0.00") : pas de prix négatif ;
        // @Digits : 10 chiffres avant la virgule et 2 après au maximum.
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal prix,
        // Identifiant de la catégorie choisie (doit exister, vérifié par le service).
        @NotNull Long categorieId) {
}
