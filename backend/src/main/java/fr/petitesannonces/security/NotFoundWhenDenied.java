package fr.petitesannonces.security;

import fr.petitesannonces.annonce.AnnonceIntrouvableException;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.security.authorization.method.MethodInvocationResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Transforme un refus d'autorisation en 404.
 *
 * <p>Cas de {@code GET /api/annonces/{id}} : une annonce non publiée n'est visible que par son
 * auteur ou un modérateur. Pour les autres, répondre 403 révélerait son existence ; on répond
 * donc 404, exactement comme si elle n'existait pas (MT-02).
 *
 * <p>La règle reste exprimée dans un {@code @PostAuthorize} (BK-04) ; seul le <i>code de
 * réponse</i> du refus change, via {@code @HandleAuthorizationDenied(handlerClass = ...)}.
 */
@Component
public class NotFoundWhenDenied implements MethodAuthorizationDeniedHandler {

    @Override
    public Object handleDeniedInvocation(MethodInvocation invocation, AuthorizationResult result) {
        throw new AnnonceIntrouvableException(idOf(invocation));
    }

    @Override
    public Object handleDeniedInvocationResult(MethodInvocationResult result, AuthorizationResult authorization) {
        throw new AnnonceIntrouvableException(idOf(result.getMethodInvocation()));
    }

    private static UUID idOf(MethodInvocation invocation) {
        for (Object argument : invocation.getArguments()) {
            if (argument instanceof UUID id) {
                return id;
            }
        }
        return null;
    }
}
