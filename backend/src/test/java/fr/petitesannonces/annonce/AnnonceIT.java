package fr.petitesannonces.annonce;

import com.jayway.jsonpath.JsonPath;
import fr.petitesannonces.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnonceIT extends IntegrationTest {

    private static final String ANNONCE_VALIDE = """
            {"titre": "Vélo de course", "description": "Très bon état", "prix": 250.00, "categorieId": 2}
            """;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String creerAnnonce(RequestPostProcessor auteur) throws Exception {
        String body = mvc.perform(json(post("/api/annonces"), ANNONCE_VALIDE).with(auteur))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String creerAnnoncePubliee(RequestPostProcessor auteur) throws Exception {
        String id = creerAnnonce(auteur);
        mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(MODO)).andExpect(status().isOk());
        return id;
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("MT-01 : 201 + Location, EN_ATTENTE, auteurId = sub même si le corps en fournit un autre")
        void creation() throws Exception {
            String corpsAvecAuteurForge = """
                    {"titre": "Vélo de course", "description": "Très bon état", "prix": 250,
                     "categorieId": 2, "auteurId": "bob-sub", "statut": "PUBLIEE"}
                    """;
            mvc.perform(json(post("/api/annonces"), corpsAvecAuteurForge).with(ALICE))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/annonces/[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                    .andExpect(jsonPath("$.auteurId").value("alice-sub"));
        }

        @Test
        @DisplayName("MT-10 : titre trop court et prix négatif → 400 détaillant les champs")
        void validation() throws Exception {
            mvc.perform(json(post("/api/annonces"),
                            "{\"titre\": \"abc\", \"description\": \"x\", \"prix\": -1, \"categorieId\": 1}").with(ALICE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
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
        @DisplayName("MT-02 : annonce EN_ATTENTE invisible pour un tiers → 404 ; visible pour l'auteur et le modérateur")
        void visibilite() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(get("/api/annonces/{id}", id).with(BOB))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
            mvc.perform(get("/api/annonces/{id}", id).with(ALICE)).andExpect(status().isOk());
            mvc.perform(get("/api/annonces/{id}", id).with(MODO)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("Annonce inexistante → 404 ; identifiant non UUID → 404 (aucune route)")
        void inexistante() throws Exception {
            mvc.perform(get("/api/annonces/{id}", "00000000-0000-0000-0000-000000000000").with(ALICE))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/annonces/pas-un-uuid").with(ALICE)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("mes-annonces n'est pas confondu avec {id} et liste tous les statuts de l'auteur")
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
        @DisplayName("MT-03 : bob ne peut ni modifier ni supprimer l'annonce d'alice → 403")
        void proprietaireUniquement() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(json(put("/api/annonces/{id}", id), ANNONCE_VALIDE).with(BOB)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/annonces/{id}", id).with(BOB)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("MT-04 : modifier une annonce publiée la repasse EN_ATTENTE et la retire de la liste publique")
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
        @DisplayName("MT-05 : un USER ne peut pas publier → 403")
        void userNePeutPasPublier() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(ALICE)).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("MT-06 : le modérateur publie → visible dans la liste publique")
        void publication() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(patch("/api/moderation/annonces/{id}/publier", id).with(MODO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("PUBLIEE"));
            mvc.perform(get("/api/annonces").with(BOB))
                    .andExpect(jsonPath("$.content[*].id", hasItem(id)));
        }

        @Test
        @DisplayName("MT-07 : rejet sans motif → 400 ProblemDetail ; avec motif → REJETEE")
        void rejet() throws Exception {
            String id = creerAnnonce(ALICE);
            mvc.perform(json(patch("/api/moderation/annonces/{id}/rejeter", id), "{}").with(MODO))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].champ").value("motif"));
            mvc.perform(json(patch("/api/moderation/annonces/{id}/rejeter", id), "").with(MODO))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(patch("/api/moderation/annonces/{id}/rejeter", id), "{\"motif\": \"Photo manquante\"}").with(MODO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("REJETEE"))
                    .andExpect(jsonPath("$.motifRejet").value("Photo manquante"));
        }

        @Test
        @DisplayName("File de modération : EN_ATTENTE uniquement, plus anciennes d'abord")
        void fileDeModeration() throws Exception {
            String premiere = creerAnnonce(ALICE);
            String seconde = creerAnnonce(BOB);
            creerAnnoncePubliee(ALICE);
            mvc.perform(get("/api/moderation/annonces").with(MODO))
                    .andExpect(status().isOk())
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
