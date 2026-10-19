package fr.petitesannonces.controller;

// JsonPath : lecture d'un champ dans un texte JSON
import com.jayway.jsonpath.JsonPath;
// Classe de base des tests d'intégration
import fr.petitesannonces.IntegrationTest;
// JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
// Spring Test : construction des requêtes simulées
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Vérifications Hamcrest (contains, hasItem, not, matchesPattern) et MockMvc
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de l'API des annonces et de la modération (contrôleurs + service + base PostgreSQL).
 * Les tests sont regroupés par thème dans des classes {@code @Nested}. Lancement : {@code ./mvnw verify}.
 */
class AnnonceIT extends IntegrationTest {

    // Bloc de texte Java (""" … """) : corps JSON d'une annonce valide dans la catégorie 2.
    private static final String ANNONCE_VALIDE = """
            {"titre": "Vélo de course", "description": "Très bon état", "prix": 250.00, "categorieId": 2}
            """;

    // Ajoute un corps JSON à une requête de test.
    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // Crée une annonce (en attente) au nom de l'utilisateur donné et renvoie son identifiant.
    private String creerAnnonce(RequestPostProcessor auteur) throws Exception {
        String body = mvc.perform(json(post("/api/annonces"), ANNONCE_VALIDE).with(auteur))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // Crée une annonce puis la fait publier par le modérateur ; renvoie son identifiant.
    private String creerAnnoncePubliee(RequestPostProcessor auteur) throws Exception {
        String id = creerAnnonce(auteur);
        mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(MODO)).andExpect(status().isOk());
        return id;
    }

    // @Nested (JUnit) : groupe de tests sur un même thème, affiché comme une sous-partie du rapport.
    @Nested
    class Creation {

        @Test
        @DisplayName("Création : 201 + Location, EN_ATTENTE, auteur = utilisateur connecté même si le corps en indique un autre")
        void creation() throws Exception {
            // Le client tente d'imposer un autre auteur et un statut : ces champs doivent être ignorés.
            String corpsAvecAuteurForge = """
                    {"titre": "Vélo de course", "description": "Très bon état", "prix": 250,
                     "categorieId": 2, "auteurId": "bob-sub", "statut": "PUBLIEE"}
                    """;
            mvc.perform(json(post("/api/annonces"), corpsAvecAuteurForge).with(ALICE))
                    .andExpect(status().isCreated())
                    // matchesPattern : l'en-tête Location doit avoir la forme /api/annonces/<uuid>.
                    .andExpect(header().string("Location", matchesPattern("/api/annonces/[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                    .andExpect(jsonPath("$.auteurId").value("alice-sub"));
        }

        @Test
        @DisplayName("Titre trop court et prix négatif → 400 avec les deux champs en erreur")
        void validation() throws Exception {
            mvc.perform(json(post("/api/annonces"),
                            "{\"titre\": \"abc\", \"description\": \"x\", \"prix\": -1, \"categorieId\": 1}").with(ALICE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    // $.errors[*].champ : liste des noms de champs en erreur ; hasItem : contient cette valeur.
                    .andExpect(jsonPath("$.errors[*].champ", hasItem("titre")))
                    .andExpect(jsonPath("$.errors[*].champ", hasItem("prix")));
        }

        @Test
        @DisplayName("Catégorie inconnue → 400 sur le champ categorieId")
        void categorieInconnue() throws Exception {
            mvc.perform(json(post("/api/annonces"),
                            "{\"titre\": \"Vélo de course\", \"description\": \"x\", \"prix\": 1, \"categorieId\": 999}").with(ALICE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].champ").value("categorieId"));
        }
    }

    @Nested
    class Consultation {

        @Test
        @DisplayName("Annonce en attente : 404 pour un autre utilisateur, visible pour l'auteur et le modérateur")
        void visibilite() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(get("/api/annonces/{id}", id).with(BOB))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
            mvc.perform(get("/api/annonces/{id}", id).with(ALICE)).andExpect(status().isOk());
            mvc.perform(get("/api/annonces/{id}", id).with(MODO)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Annonce inexistante → 404 ; identifiant qui n'est pas un UUID → 404 (aucune route)")
        void inexistante() throws Exception {
            mvc.perform(get("/api/annonces/{id}", "00000000-0000-0000-0000-000000000000").with(ALICE))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/annonces/pas-un-uuid").with(ALICE)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("mes-annonces n'est pas confondu avec {id} et ne renvoie que les annonces de l'utilisateur")
        void mesAnnonces() throws Exception {
            String id = creerAnnonce(ALICE);
            creerAnnonce(BOB);
            mvc.perform(get("/api/annonces/mes-annonces").with(ALICE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(id));
        }

        @Test
        @DisplayName("Pagination : size > 50 → 400")
        void tailleDePageLimitee() throws Exception {
            // .param(nom, valeur) : ajoute un paramètre à l'URL (?size=51).
            mvc.perform(get("/api/annonces").param("size", "51").with(ALICE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].champ").value("size"));
        }

        @Test
        @DisplayName("Filtre par catégorie")
        void filtreCategorie() throws Exception {
            String id = creerAnnoncePubliee(ALICE);
            mvc.perform(get("/api/annonces").param("categorieId", "2").with(BOB))
                    .andExpect(jsonPath("$.content[*].id", hasItem(id)));
            mvc.perform(get("/api/annonces").param("categorieId", "1").with(BOB))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
        }
    }

    @Nested
    class ModificationEtSuppression {

        @Test
        @DisplayName("Un autre utilisateur ne peut ni modifier ni supprimer l'annonce → 403")
        void proprietaireUniquement() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(json(put("/api/annonces/{id}", id), ANNONCE_VALIDE).with(BOB)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/annonces/{id}", id).with(BOB)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Modifier une annonce publiée la repasse EN_ATTENTE et la retire de la liste publique")
        void modificationRepasseEnAttente() throws Exception {
            String id = creerAnnoncePubliee(ALICE);
            mvc.perform(json(put("/api/annonces/{id}", id),
                            "{\"titre\": \"Vélo de route\", \"description\": \"Révisé\", \"prix\": 230, \"categorieId\": 2}").with(ALICE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                    .andExpect(jsonPath("$.titre").value("Vélo de route"));
            mvc.perform(get("/api/annonces").with(BOB))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
        }

        @Test
        @DisplayName("Suppression par l'auteur, par un modérateur et par un admin → 204")
        void suppression() throws Exception {
            mvc.perform(delete("/api/annonces/{id}", creerAnnonce(ALICE)).with(ALICE)).andExpect(status().isNoContent());
            mvc.perform(delete("/api/annonces/{id}", creerAnnonce(ALICE)).with(MODO)).andExpect(status().isNoContent());
            mvc.perform(delete("/api/annonces/{id}", creerAnnonce(ALICE)).with(ADMIN)).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Modifier une annonce inexistante → 404 (et non 403)")
        void modificationInexistante() throws Exception {
            mvc.perform(json(put("/api/annonces/{id}", "00000000-0000-0000-0000-000000000000"), ANNONCE_VALIDE).with(ALICE))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Moderation {

        @Test
        @DisplayName("Un USER ne peut pas publier → 403")
        void userNePeutPasPublier() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(ALICE)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Le modérateur publie → l'annonce apparaît dans la liste publique")
        void publication() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(MODO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("PUBLIEE"));
            mvc.perform(get("/api/annonces").with(BOB))
                    .andExpect(jsonPath("$.content[*].id", hasItem(id)));
        }

        @Test
        @DisplayName("Rejet sans motif → 400 ; avec motif → REJETEE")
        void rejet() throws Exception {
            String id = creerAnnonce(ALICE);
            // Corps JSON sans motif.
            mvc.perform(json(patch("/api/moderation/annonces/{id}/rejeter", id), "{}").with(MODO))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].champ").value("motif"));
            // Corps vide.
            mvc.perform(json(patch("/api/moderation/annonces/{id}/rejeter", id), "").with(MODO))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(patch("/api/moderation/annonces/{id}/rejeter", id), "{\"motif\": \"Photo manquante\"}").with(MODO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("REJETEE"))
                    .andExpect(jsonPath("$.motifRejet").value("Photo manquante"));
        }

        @Test
        @DisplayName("File de modération : annonces en attente uniquement, les plus anciennes d'abord")
        void fileDeModeration() throws Exception {
            String premiere = creerAnnonce(ALICE);
            String seconde = creerAnnonce(BOB);
            creerAnnoncePubliee(ALICE);
            mvc.perform(get("/api/moderation/annonces").with(MODO))
                    .andExpect(status().isOk())
                    // contains : exactement ces éléments, dans cet ordre.
                    .andExpect(jsonPath("$.content[*].id", contains(premiere, seconde)));
            mvc.perform(get("/api/moderation/annonces").with(ALICE)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Publier une annonce déjà publiée → 409")
        void transitionInvalide() throws Exception {
            String id = creerAnnoncePubliee(ALICE);
            mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(MODO)).andExpect(status().isConflict());
        }
    }
}
