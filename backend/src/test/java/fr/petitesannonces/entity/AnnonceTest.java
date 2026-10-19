package fr.petitesannonces.entity;

// Classes du projet
import fr.petitesannonces.exception.ConflitException;
// JUnit 5
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
// Java standard
import java.math.BigDecimal;

// AssertJ : vérifications lisibles (assertThat…) et vérification d'exception (assertThatThrownBy…)
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires de l'entité {@link Annonce} : son cycle de vie (en attente, publiée, rejetée).
 *
 * <p>Test « unitaire » : on crée des objets Java directement avec {@code new}, sans Spring,
 * sans base de données ni Docker. Exécution en quelques millisecondes avec {@code ./mvnw test}.
 */
class AnnonceTest {

    // Catégorie utilisée par toutes les annonces de test.
    private final Categorie vehicules = new Categorie("Véhicules");
    // Annonce recréée avant chaque test (voir @BeforeEach), pour que les tests soient indépendants.
    private Annonce annonce;

    // @BeforeEach (JUnit) : exécutée avant CHAQUE méthode @Test.
    @BeforeEach
    void creerAnnonce() {
        annonce = new Annonce("Vélo de course", "Très bon état", new BigDecimal("250.00"), vehicules, "alice-sub");
    }

    @Test
    @DisplayName("Une nouvelle annonce est en attente, avec un identifiant et des dates")
    void nouvelleAnnonce() {
        assertThat(annonce.getStatut()).isEqualTo(StatutAnnonce.EN_ATTENTE);
        assertThat(annonce.getId()).isNotNull();
        assertThat(annonce.getAuteurId()).isEqualTo("alice-sub");
        assertThat(annonce.getCategorie()).isSameAs(vehicules);
        assertThat(annonce.getDateCreation()).isNotNull();
        // À la création, la date de modification est égale à la date de création.
        assertThat(annonce.getDateModification()).isEqualTo(annonce.getDateCreation());
        assertThat(annonce.getMotifRejet()).isNull();
    }

    @Test
    @DisplayName("Publier une annonce en attente la rend PUBLIEE")
    void publier() {
        annonce.publier();

        assertThat(annonce.getStatut()).isEqualTo(StatutAnnonce.PUBLIEE);
    }

    @Test
    @DisplayName("Rejeter une annonce en attente la rend REJETEE et conserve le motif")
    void rejeter() {
        annonce.rejeter("Photo manquante");

        assertThat(annonce.getStatut()).isEqualTo(StatutAnnonce.REJETEE);
        assertThat(annonce.getMotifRejet()).isEqualTo("Photo manquante");
    }

    @Test
    @DisplayName("Publier une annonce déjà publiée est refusé (ConflitException)")
    void publierDeuxFois() {
        annonce.publier();

        // assertThatThrownBy : vérifie que le code entre parenthèses lève bien l'exception attendue.
        assertThatThrownBy(() -> annonce.publier())
                .isInstanceOf(ConflitException.class)
                .hasMessageContaining("EN_ATTENTE");
    }

    @Test
    @DisplayName("Rejeter une annonce déjà publiée est refusé (ConflitException)")
    void rejeterUneAnnoncePubliee() {
        annonce.publier();

        assertThatThrownBy(() -> annonce.rejeter("Trop tard"))
                .isInstanceOf(ConflitException.class);
        // Le refus ne doit rien avoir modifié.
        assertThat(annonce.getStatut()).isEqualTo(StatutAnnonce.PUBLIEE);
        assertThat(annonce.getMotifRejet()).isNull();
    }

    @Test
    @DisplayName("Modifier une annonce rejetée la remet en attente et efface le motif de rejet")
    void modifierApresRejet() {
        annonce.rejeter("Photo manquante");
        Categorie emploi = new Categorie("Emploi");

        annonce.modifier("Vélo de route", "Révisé", new BigDecimal("230"), emploi);

        assertThat(annonce.getStatut()).isEqualTo(StatutAnnonce.EN_ATTENTE);
        assertThat(annonce.getMotifRejet()).isNull();
        assertThat(annonce.getTitre()).isEqualTo("Vélo de route");
        assertThat(annonce.getDescription()).isEqualTo("Révisé");
        assertThat(annonce.getPrix()).isEqualByComparingTo("230");
        assertThat(annonce.getCategorie()).isSameAs(emploi);
        // La date de modification ne peut pas être antérieure à la création.
        assertThat(annonce.getDateModification()).isAfterOrEqualTo(annonce.getDateCreation());
    }

    @Test
    @DisplayName("Modifier une annonce publiée la remet en attente de modération")
    void modifierApresPublication() {
        annonce.publier();

        annonce.modifier("Vélo de course", "Pneus neufs", new BigDecimal("260"), vehicules);

        assertThat(annonce.getStatut()).isEqualTo(StatutAnnonce.EN_ATTENTE);
    }
}
