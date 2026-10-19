package fr.petitesannonces.security;

// Jakarta Servlet : requête et réponse HTTP
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// Spring Web : codes HTTP
import org.springframework.http.HttpStatus;
// Spring Security : point d'entrée appelé quand l'authentification manque
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
// Spring Framework : déclaration de composant
import org.springframework.stereotype.Component;
// Java standard
import java.io.IOException;

/**
 * Répond <b>401 Unauthorized</b> quand une requête arrive sans identité (pas d'en-tête {@code X_USER_ID}).
 *
 * <p>En fonctionnement normal cela n'arrive pas, car la gateway refuse avant les requêtes sans
 * token valide. C'est une deuxième barrière (« défense en profondeur »).
 *
 * <p>Utilisé par : Spring Security, via {@code SecurityConfig} ({@code .authenticationEntryPoint(...)}).
 */
@Component
// implements AuthenticationEntryPoint (Spring Security) : interface appelée quand une ressource
// protégée est demandée sans être authentifié.
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    // Injection par constructeur : Spring fournit le ProblemDetailWriter.
    ProblemDetailAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    /** Appelée par Spring Security : écrit la réponse 401 au format JSON ProblemDetail. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentification requise.");
    }
}
