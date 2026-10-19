package fr.petitesannonces.security;

import fr.petitesannonces.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecuriteIT extends IntegrationTest {

    @Test
    @DisplayName("BK-02 : sans X_USER_ID → 401 au format ProblemDetail")
    void sansIdentite401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/me"));
    }

    @Test
    @DisplayName("BK-02 : X_USER_ID vide → 401")
    void identiteVide401() throws Exception {
        mvc.perform(get("/api/annonces").header("X_USER_ID", "  ").header("X_ROLE_ID", "USER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BK-03 : principal et autorités reconstruits depuis les headers")
    void contexteReconstruit() throws Exception {
        mvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", "MODERATEUR,USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.roles", contains("MODERATEUR", "USER")));
    }

    @Test
    @DisplayName("Identifié sans rôle : /api/me OK mais 403 ProblemDetail sur une ressource USER")
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
    @DisplayName("BK-05 : aucune fuite de contexte entre requêtes, aucune session créée")
    void statelessEtSansFuite() throws Exception {
        MvcResult premiere = mvc.perform(get("/api/me").header("X_USER_ID", "u1").header("X_ROLE_ID", "ADMIN"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(premiere.getRequest().getSession(false)).isNull();
        // MockMvc exécute la requête dans le thread du test : le contexte doit avoir été vidé.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        mvc.perform(get("/api/me").header("X_USER_ID", "u2").header("X_ROLE_ID", "USER"))
                .andExpect(jsonPath("$.userId").value("u2"))
                .andExpect(jsonPath("$.roles", contains("USER")));

        // Même thread, sans headers : l'identité de u2 ne doit pas être réutilisée.
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Healthcheck accessible sans identité")
    void healthPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
