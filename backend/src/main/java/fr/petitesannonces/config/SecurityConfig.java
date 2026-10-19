package fr.petitesannonces.config;

// Classes du projet (package security)
import fr.petitesannonces.security.HeaderAuthenticationFilter;
import fr.petitesannonces.security.ProblemDetailAccessDeniedHandler;
import fr.petitesannonces.security.ProblemDetailAuthenticationEntryPoint;
// Spring Framework : déclaration d'objets gérés par Spring
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Spring Security : configuration de la sécurité web et des méthodes
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Configuration de Spring Security pour toute l'API.
 *
 * <p>Rôle : définir comment chaque requête HTTP est sécurisée. Ici le backend n'authentifie
 * personne lui-même : il lit l'identité dans les en-têtes {@code X_USER_ID} et {@code X_ROLE_ID}
 * (posés par la gateway) grâce à {@link HeaderAuthenticationFilter}, puis vérifie que la requête
 * est authentifiée. Les droits précis (rôles, propriété) sont vérifiés par les annotations
 * {@code @PreAuthorize} des contrôleurs.
 *
 * <p>Utilisé par : Spring Boot, automatiquement au démarrage (grâce à {@code @Configuration}).
 */
// @Configuration (Spring) : classe de configuration ; ses méthodes @Bean créent des objets Spring.
@Configuration
// @EnableMethodSecurity (Spring Security) : active les annotations de sécurité sur les méthodes
// (@PreAuthorize, @PostAuthorize). Sans elle, ces annotations seraient ignorées.
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Construit la chaîne de filtres de sécurité appliquée à chaque requête HTTP.
     *
     * @param http                     constructeur de configuration fourni par Spring Security
     * @param authenticationEntryPoint réponse 401 quand la requête n'a pas d'identité
     * @param accessDeniedHandler      réponse 403 quand l'identité n'a pas le droit
     */
    // @Bean (Spring) : l'objet renvoyé est enregistré dans Spring et utilisé par Spring Security.
    // Les paramètres sont injectés automatiquement par Spring (ce sont des @Component du projet).
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // Désactive la protection CSRF : elle protège les sessions par cookie. Cette API
                // n'utilise ni session ni cookie d'authentification, elle n'en a pas besoin.
                .csrf(AbstractHttpConfigurer::disable)
                // Désactive la page de login HTML fournie par défaut par Spring Security.
                .formLogin(AbstractHttpConfigurer::disable)
                // Désactive l'authentification « Basic » (identifiant:mot de passe en en-tête).
                .httpBasic(AbstractHttpConfigurer::disable)
                // Désactive l'URL /logout : la déconnexion se fait côté Keycloak.
                .logout(AbstractHttpConfigurer::disable)
                // Désactive la mémorisation de la requête d'origine (utile seulement avec un login HTML).
                .requestCache(AbstractHttpConfigurer::disable)
                // STATELESS : aucune session HTTP n'est créée ; l'identité est relue à chaque requête.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Ajoute notre filtre qui lit X_USER_ID / X_ROLE_ID, juste avant le filtre
                // « anonyme » de Spring Security (qui marque comme anonyme toute requête sans identité).
                // Il est créé avec « new » et pas déclaré @Component : sinon Spring Boot
                // l'exécuterait une deuxième fois, en dehors de la chaîne de sécurité.
                .addFilterBefore(new HeaderAuthenticationFilter(), AnonymousAuthenticationFilter.class)
                // Règles d'accès par URL.
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health (Spring Boot Actuator) reste accessible sans identité :
                        // il sert à vérifier que l'application est démarrée.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Toutes les autres URL exigent une identité (sinon 401).
                        .anyRequest().authenticated())
                // Réponses en cas d'erreur de sécurité.
                .exceptionHandling(errors -> errors
                        // Pas d'identité → 401 au format JSON ProblemDetail.
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // Identité sans le droit nécessaire → 403 au format JSON ProblemDetail.
                        .accessDeniedHandler(accessDeniedHandler))
                // Assemble la configuration en un objet SecurityFilterChain.
                .build();
    }

    /**
     * Déclare une liste d'utilisateurs locaux vide.
     *
     * <p>Sans ce bean, Spring Boot créerait un utilisateur « user » avec un mot de passe aléatoire
     * affiché dans les logs. Ce backend n'authentifie personne lui-même : on l'en empêche.
     */
    @Bean
    UserDetailsService noLocalUsers() {
        // InMemoryUserDetailsManager (Spring Security) : gestionnaire d'utilisateurs en mémoire,
        // créé ici sans aucun utilisateur.
        return new InMemoryUserDetailsManager();
    }
}
