package fr.petitesannonces.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Chaîne de sécurité HTTP.
 *
 * <ul>
 *   <li><b>Stateless</b> : aucune session HTTP, l'identité est reconstruite à chaque requête.</li>
 *   <li><b>Pas de form login, http basic, logout</b> : l'authentification est faite en amont
 *       (Keycloak + Kong), le backend ne fait qu'en lire le résultat.</li>
 *   <li><b>CSRF désactivé</b> : il protège les sessions par cookie ; ici il n'y a ni session ni
 *       cookie, le navigateur ne peut pas « rejouer » une identité à l'insu de l'utilisateur.</li>
 *   <li><b>{@code @EnableMethodSecurity}</b> : toutes les règles d'accès sont des
 *       {@code @PreAuthorize}/{@code @PostAuthorize} sur les méthodes (BK-04). Ici on n'exige
 *       globalement qu'une chose : être authentifié.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Instancié ici et NON déclaré en @Component : sinon Spring Boot l'enregistrerait
                // aussi comme filtre servlet global, en dehors de la chaîne de sécurité.
                .addFilterBefore(new HeaderAuthenticationFilter(), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Healthcheck Docker : appelé sans headers, et jamais routé par Kong.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)   // → 401
                        .accessDeniedHandler(accessDeniedHandler))            // → 403
                .build();
    }

    /**
     * Empêche Spring Boot de créer son utilisateur « user » par défaut avec un mot de passe
     * aléatoire : ce backend n'authentifie personne lui-même.
     */
    @Bean
    UserDetailsService noLocalUsers() {
        return new InMemoryUserDetailsManager();
    }
}
