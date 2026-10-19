package fr.petitesannonces.categorie;

import fr.petitesannonces.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategorieIT extends IntegrationTest {

    @Test
    @DisplayName("Les 4 catégories initiales (Flyway) sont listées pour un USER")
    void categoriesInitiales() throws Exception {
        mvc.perform(get("/api/categories").with(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].libelle", containsInAnyOrder("Immobilier", "Véhicules", "Emploi", "Multimédia")));
    }

    @Test
    @DisplayName("MT-08 : MODERATEUR → 403, ADMIN → 201 ; doublon → 409")
    void creation() throws Exception {
        String body = "{\"libelle\": \"Jardinage\"}";
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body).with(MODO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body).with(ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("Jardinage"));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"libelle\": \"jardinage\"}").with(ADMIN))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Libellé hors bornes → 400")
    void validation() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"libelle\": \"x\"}").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].champ").value("libelle"));
    }

    @Test
    @DisplayName("MT-09 : supprimer une catégorie utilisée → 409 ; inutilisée → 204")
    void suppression() throws Exception {
        mvc.perform(post("/api/annonces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\": \"Studio centre-ville\", \"description\": \"20 m²\", \"prix\": 500, \"categorieId\": 1}")
                        .with(ALICE))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/categories/1").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        String creee = mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"Temporaire\"}").with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(creee, "$.id");
        mvc.perform(delete("/api/categories/{id}", id).with(ADMIN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/categories/{id}", id).with(ADMIN)).andExpect(status().isNotFound());
    }
}
