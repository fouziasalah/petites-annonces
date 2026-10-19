package fr.petitesannonces;

// JUnit 5 (fourni par spring-boot-starter-test)
import org.junit.jupiter.api.BeforeEach;
// Spring Test / Spring Boot Test : démarrage de l'application et appels HTTP simulés
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
// Testcontainers : base PostgreSQL lancée dans Docker pour les tests
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Classe de base de tous les tests d'intégration ({@code *IT.java}).
 *
 * <p>Ce qu'elle fournit :
 * <ul>
 *   <li>l'application complète démarrée, branchée sur une <b>vraie base PostgreSQL 16</b> lancée
 *       dans Docker par Testcontainers (Docker doit donc tourner) ;</li>
 *   <li>{@link #mvc} pour envoyer des requêtes HTTP sans serveur réseau ;</li>
 *   <li>des utilisateurs prêts à l'emploi ({@link #ALICE}, {@link #MODO}…) qui ajoutent les
 *       en-têtes {@code X_USER_ID} / {@code X_ROLE_ID}, comme le ferait la gateway.</li>
 * </ul>
 *
 * <p>Utilisation : {@code class MonTestIT extends IntegrationTest { … mvc.perform(get("/api/me").with(ALICE)) … }}.
 * Lancement : {@code ./mvnw verify}.
 */
// @SpringBootTest (Spring Boot Test) : démarre tout le contexte Spring, comme en production.
@SpringBootTest
// @AutoConfigureMockMvc (Spring Boot Test) : prépare l'objet MockMvc pour simuler des appels HTTP.
@AutoConfigureMockMvc
// abstract : cette classe ne contient pas de test, elle sert seulement de base aux autres.
public abstract class IntegrationTest {

    // @ServiceConnection (Spring Boot Testcontainers) : Spring utilise automatiquement l'adresse,
    // l'utilisateur et le mot de passe de ce conteneur pour sa connexion à la base.
    @ServiceConnection
    // PostgreSQLContainer (Testcontainers) : conteneur Docker de l'image postgres:16.15-alpine.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.15-alpine");

    // Bloc static : exécuté une seule fois au chargement de la classe. Le conteneur est démarré
    // une fois pour toutes les classes de test (beaucoup plus rapide que d'en relancer un par classe).
    static {
        POSTGRES.start();
    }

    // Utilisateurs de test. Les rôles reprennent ceux de Keycloak (MODERATEUR contient USER, etc.).
    protected static final RequestPostProcessor ALICE = utilisateur("alice-sub", "USER");
    protected static final RequestPostProcessor BOB = utilisateur("bob-sub", "USER");
    protected static final RequestPostProcessor MODO = utilisateur("modo-sub", "MODERATEUR", "USER");
    protected static final RequestPostProcessor ADMIN = utilisateur("admin-sub", "ADMIN", "MODERATEUR", "USER");

    // @Autowired (Spring) : Spring injecte l'objet MockMvc qu'il a préparé.
    @Autowired
    protected MockMvc mvc;

    // JdbcTemplate (Spring JDBC) : exécute du SQL directement, ici pour nettoyer la base.
    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Crée un « utilisateur » qui ajoute les en-têtes d'identité à une requête de test.
     * Exemple : {@code mvc.perform(get("/api/me").with(utilisateur("u1", "USER")))}.
     */
    protected static RequestPostProcessor utilisateur(String userId, String... roles) {
        // RequestPostProcessor (Spring Test) : fonction qui modifie la requête avant son envoi.
        return request -> {
            request.addHeader("X_USER_ID", userId);
            if (roles.length > 0) {
                // String.join : ["MODERATEUR", "USER"] → "MODERATEUR,USER".
                request.addHeader("X_ROLE_ID", String.join(",", roles));
            }
            return request;
        };
    }

    // @BeforeEach (JUnit) : exécutée avant CHAQUE test, pour que chaque test parte d'une base propre.
    @BeforeEach
    void viderLesDonnees() {
        jdbc.update("DELETE FROM annonce");
        // On garde les 4 catégories créées par la migration V2 (identifiants 1 à 4).
        jdbc.update("DELETE FROM categorie WHERE id > 4");
    }
}
