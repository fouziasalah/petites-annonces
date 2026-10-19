package fr.petitesannonces.controller;

// JsonPath (fourni par spring-boot-starter-test) : lecture d'un champ dans un texte JSON
import com.jayway.jsonpath.JsonPath;
// Classe de base des tests d'intégration
import fr.petitesannonces.IntegrationTest;
// JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
// Spring
import org.springframework.http.MediaType;

// Vérifications Hamcrest et MockMvc
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de l'API des catégories ({@code CategorieController} + {@code CategorieService}).
 * Lancement : {@code ./mvnw verify}.
 */
class CategorieIT extends IntegrationTest {

    @Test
    @DisplayName("Les 4 catégories créées par Flyway sont listées pour un USER")
    void categoriesInitiales() throws Exception {
        // .with(ALICE) : ajoute les en-têtes d'identité d'alice (voir IntegrationTest).
        mvc.perform(get("/api/categories").with(ALICE))
                .andExpect(status().isOk())
                // $[*].libelle : le libellé de chaque élément du tableau JSON.
                .andExpect(jsonPath("$[*].libelle", containsInAnyOrder("Immobilier", "Véhicules", "Emploi", "Multimédia")));
    }

    @Test
    @DisplayName("Création : MODERATEUR → 403, ADMIN → 201, libellé en double → 409")
    void creation() throws Exception {
        String body = "{\"libelle\": \"Jardinage\"}";
        // contentType + content : corps JSON de la requête POST.
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body).with(MODO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content(body).with(ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("Jardinage"));
        // Même libellé en minuscules : refusé, l'unicité ignore les majuscules.
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"libelle\": \"jardinage\"}").with(ADMIN))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Libellé trop court → 400 avec le champ en erreur")
    void validation() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"libelle\": \"x\"}").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].champ").value("libelle"));
    }

    @Test
    @DisplayName("Suppression : catégorie utilisée → 409, inutilisée → 204, déjà supprimée → 404")
    void suppression() throws Exception {
        // Une annonce dans la catégorie 1 (Immobilier) la rend non supprimable.
        mvc.perform(post("/api/annonces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titre\": \"Studio centre-ville\", \"description\": \"20 m²\", \"prix\": 500, \"categorieId\": 1}")
                        .with(ALICE))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/categories/1").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Une catégorie créée pour le test, jamais utilisée, peut être supprimée.
        String creee = mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\": \"Temporaire\"}").with(ADMIN))
                // getContentAsString : corps de la réponse sous forme de texte JSON.
                .andReturn().getResponse().getContentAsString();
        // JsonPath.read : lit le champ « id » de la réponse.
        Integer id = JsonPath.read(creee, "$.id");
        // {id} dans l'URL est remplacé par la valeur passée en second argument.
        mvc.perform(delete("/api/categories/{id}", id).with(ADMIN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/categories/{id}", id).with(ADMIN)).andExpect(status().isNotFound());
    }
}
