package fr.petitesannonces.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 : la requête n'a pas d'identité (aucun {@code X_USER_ID}).
 * En fonctionnement normal cela n'arrive pas, Kong rejette avant ; c'est la défense en profondeur.
 */
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    ProblemDetailAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentification requise.");
    }
}
