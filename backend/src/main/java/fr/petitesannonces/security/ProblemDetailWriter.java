package fr.petitesannonces.security;

// Jackson (fourni par spring-boot-starter-web) : conversion objet Java ↔ JSON
import com.fasterxml.jackson.databind.ObjectMapper;
// Jakarta Servlet : requête et réponse HTTP
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// Spring Web : codes HTTP, types de contenu, format d'erreur ProblemDetail
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
// Spring Framework : déclaration de composant
import org.springframework.stereotype.Component;
// Java standard
import java.io.IOException;
import java.net.URI;

/**
 * Écrit une réponse d'erreur au format JSON ProblemDetail (RFC 9457) directement dans la réponse HTTP.
 *
 * <p>Pourquoi cette classe ? Les erreurs 401 et 403 de Spring Security sont produites par des
 * filtres, AVANT d'arriver aux contrôleurs : {@code ApiExceptionHandler} ne les voit pas. On écrit
 * donc le JSON nous-mêmes, pour garder le même format d'erreur dans toute l'API.
 *
 * <p>Utilisé par : {@link ProblemDetailAuthenticationEntryPoint} (401) et
 * {@link ProblemDetailAccessDeniedHandler} (403).
 */
// @Component (Spring) : Spring crée une instance et l'injecte dans les classes qui la demandent.
// Classe sans « public » : elle n'est utilisée qu'à l'intérieur du package security.
@Component
class ProblemDetailWriter {

    // ObjectMapper (Jackson) : celui configuré par Spring Boot, pour un JSON identique au reste de l'API.
    private final ObjectMapper objectMapper;

    ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Écrit l'erreur dans la réponse.
     * Exemple : {@code writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentification requise.")}.
     */
    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        // Corps de l'erreur : { "status": 401, "title": "Unauthorized", "detail": "…" }.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        // « instance » : l'URL demandée, pour savoir quelle requête a échoué.
        problem.setInstance(URI.create(request.getRequestURI()));
        // Code HTTP de la réponse.
        response.setStatus(status.value());
        // En-tête Content-Type: application/problem+json (type standard des ProblemDetail).
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // writeValue (Jackson) : convertit l'objet en JSON et l'écrit dans le corps de la réponse.
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
