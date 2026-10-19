package fr.petitesannonces;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests d'intégration : application complète + vraie base PostgreSQL 16 (Testcontainers).
 *
 * <p>Kong n'est pas là : les tests posent eux-mêmes {@code X_USER_ID} / {@code X_ROLE_ID},
 * exactement comme le ferait la gateway après validation du token. On teste ainsi le backend
 * seul, dans son contrat : « je reçois des headers d'identité, j'autorise ».
 *
 * <p>Conteneur « singleton » : démarré une fois pour toutes les classes de test (plus rapide).
 * {@code @ServiceConnection} branche automatiquement la datasource Spring dessus.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.15-alpine");

    static {
        POSTGRES.start();
    }

    protected static final RequestPostProcessor ALICE = utilisateur("alice-sub", "USER");
    protected static final RequestPostProcessor BOB = utilisateur("bob-sub", "USER");
    protected static final RequestPostProcessor MODO = utilisateur("modo-sub", "MODERATEUR", "USER");
    protected static final RequestPostProcessor ADMIN = utilisateur("admin-sub", "ADMIN", "MODERATEUR", "USER");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** Simule les headers posés par Kong (identity-headers). */
    protected static RequestPostProcessor utilisateur(String userId, String... roles) {
        return request -> {
            request.addHeader("X_USER_ID", userId);
            if (roles.length > 0) {
                request.addHeader("X_ROLE_ID", String.join(",", roles));
            }
            return request;
        };
    }

    @BeforeEach
    void viderLesDonnees() {
        jdbc.update("DELETE FROM annonce");
        jdbc.update("DELETE FROM categorie WHERE id > 4");
    }
}
