package fr.petitesannonces.security;

// Jakarta Servlet : requête et réponse HTTP
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// Spring Web : codes HTTP
import org.springframework.http.HttpStatus;
// Spring Security : gestion des accès refusés
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
// Spring Framework : déclaration de composant
import org.springframework.stereotype.Component;
// Java standard
import java.io.IOException;

/**
 * Répond <b>403 Forbidden</b> quand l'utilisateur est identifié mais n'a pas le droit de faire l'action
 * (une règle {@code @PreAuthorize} est fausse, par exemple un USER qui veut créer une catégorie).
 *
 * <p>Utilisé par : Spring Security, via {@code SecurityConfig} ({@code .accessDeniedHandler(...)}).
 */
@Component
// implements AccessDeniedHandler (Spring Security) : interface appelée quand l'accès est refusé.
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    // Injection par constructeur : Spring fournit le ProblemDetailWriter.
    ProblemDetailAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    /** Appelée par Spring Security : écrit la réponse 403 au format JSON ProblemDetail. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN, "Accès refusé.");
    }
}
