package fr.petitesannonces.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Écrit une réponse d'erreur au format RFC 9457 (application/problem+json).
 *
 * <p>Les erreurs de Spring Security (401, 403) sont produites par des filtres, AVANT les
 * contrôleurs : le {@code @RestControllerAdvice} ne les voit pas. On sérialise donc le
 * {@link ProblemDetail} nous-mêmes pour garder un format d'erreur unique dans toute l'API.
 */
@Component
class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
