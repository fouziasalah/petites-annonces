package fr.petitesannonces.security;

// Classe de base des tests d'intégration
import fr.petitesannonces.IntegrationTest;
// JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
// Spring
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

// Vérifications : AssertJ (assertThat), Hamcrest (contains, empty) et MockMvc (status, jsonPath…)
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la sécurité : lecture des en-têtes d'identité, réponses 401/403, absence de session.
 * Lancement : {@code ./mvnw verify}.
 */
class SecuriteIT extends IntegrationTest {

    @Test
    // @DisplayName (JUnit) : intitulé lisible affiché dans les rapports de test.
    @DisplayName("Sans X_USER_ID → 401 au format ProblemDetail")
    void sansIdentite401() throws Exception {
        // mvc.perform(get(...)) : envoie une requête GET simulée ; andExpect : vérifie la réponse.
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // jsonPath("$.status") : lit le champ « status » du JSON de réponse.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/me"));
    }

    @Test
    @DisplayName("X_USER_ID vide → 401")
    void identiteVide401() throws Exception {
        // .header(nom, valeur) : ajoute un en-tête à la requête.
        mvc.perform(get("/api/annonces").header("X_USER_ID", "  ").header("X_ROLE_ID", "USER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("L'identifiant et les rôles sont reconstruits depuis les en-têtes")
    void contexteReconstruit() throws Exception {
        mvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", "MODERATEUR,USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.roles", contains("MODERATEUR", "USER")));
    }

    @Test
    @DisplayName("Identifié sans rôle : /api/me répond, mais 403 sur une ressource USER")
    void sansRole403() throws Exception {
        mvc.perform(get("/api/me").header("X_USER_ID", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", empty()));
        mvc.perform(get("/api/annonces").header("X_USER_ID", "u1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Aucune identité ne passe d'une requête à l'autre, aucune session n'est créée")
    void statelessEtSansFuite() throws Exception {
        // andReturn() : récupère le résultat pour faire des vérifications supplémentaires.
        MvcResult premiere = mvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", "ADMIN"))
                .andExpect(jsonPath("$.userId").value("u1"))
                // Pas de cookie de session dans la réponse.
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        // getSession(false) : renvoie la session HTTP si elle existe, sans en créer ; doit être null.
        assertThat(premiere.getRequest().getSession(false)).isNull();
        // MockMvc exécute la requête dans le thread du test : après la requête, le contexte de
        // sécurité doit avoir été vidé par le filtre.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mvc.perform(get("/api/me").header("X_USER_ID", "u2").header("X_ROLE_ID", "USER"))
                .andExpect(jsonPath("$.userId").value("u2"))
                .andExpect(jsonPath("$.roles", contains("USER")));

        // Même thread, sans en-têtes : l'identité de u2 ne doit pas être réutilisée.
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/actuator/health est accessible sans identité")
    void healthPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
