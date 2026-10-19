package fr.petitesannonces.security;

// Classes du projet
import fr.petitesannonces.exception.AnnonceIntrouvableException;
// AOP Alliance (fournie avec Spring) : représente l'appel de méthode intercepté
import org.aopalliance.intercept.MethodInvocation;
// Spring Security : traitement personnalisé d'un refus d'autorisation
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.security.authorization.method.MethodInvocationResult;
// Spring Framework : déclaration de composant
import org.springframework.stereotype.Component;
// Java standard
import java.util.UUID;

/**
 * Transforme un refus d'autorisation en réponse <b>404</b> au lieu de 403.
 *
 * <p>Cas d'usage : {@code GET /api/annonces/{id}} sur une annonce en attente qui ne vous appartient
 * pas. Répondre 403 révélerait que l'annonce existe ; on répond 404, comme si elle n'existait pas.
 *
 * <p>Utilisation : sur une méthode protégée par {@code @PreAuthorize} ou {@code @PostAuthorize},
 * ajouter {@code @HandleAuthorizationDenied(handlerClass = NotFoundWhenDenied.class)}.
 * Utilisé par : {@code AnnonceService.consulter}.
 */
@Component
// implements MethodAuthorizationDeniedHandler (Spring Security 6.3+) : appelée à la place du
// comportement par défaut (403) quand une règle de sécurité de méthode est fausse.
public class NotFoundWhenDenied implements MethodAuthorizationDeniedHandler {

    /** Refus AVANT l'exécution de la méthode ({@code @PreAuthorize}). */
    @Override
    public Object handleDeniedInvocation(MethodInvocation invocation, AuthorizationResult result) {
        throw new AnnonceIntrouvableException(idOf(invocation));
    }

    /** Refus APRÈS l'exécution de la méthode ({@code @PostAuthorize}, cas de {@code consulter}). */
    @Override
    public Object handleDeniedInvocationResult(MethodInvocationResult result, AuthorizationResult authorization) {
        throw new AnnonceIntrouvableException(idOf(result.getMethodInvocation()));
    }

    // Retrouve l'identifiant de l'annonce parmi les arguments de la méthode appelée (pour le message).
    private static UUID idOf(MethodInvocation invocation) {
        for (Object argument : invocation.getArguments()) {
            // « instanceof UUID id » (Java 16+) : teste le type et crée la variable id en une fois.
            if (argument instanceof UUID id) {
                return id;
            }
        }
        return null;
    }
}
