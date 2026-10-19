package fr.petitesannonces.controller;

// Classes du projet
import fr.petitesannonces.dto.MeResponse;
// Spring Security : contrôle d'accès et utilisateur connecté
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
// Spring Web
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
// Java standard
import java.util.List;

/**
 * API REST {@code GET /api/me} : « qui suis-je ? ».
 *
 * <p>Rôle : renvoyer l'identifiant et les rôles de l'utilisateur tels que le backend les voit.
 * Pratique pour vérifier de bout en bout que l'identité transmise par la gateway arrive bien.
 *
 * <p>Exemple de réponse : {@code {"userId": "3f2a…", "roles": ["MODERATEUR", "USER"]}}.
 */
@RestController
public class MeController {

    // @GetMapping avec le chemin complet : cette classe n'a pas de @RequestMapping commun.
    @GetMapping("/api/me")
    // isAuthenticated() (Spring Security) : il suffit d'être identifié, quel que soit le rôle.
    @PreAuthorize("isAuthenticated()")
    // Authentication (Spring Security) : injecté automatiquement, c'est l'utilisateur connecté
    // construit par HeaderAuthenticationFilter.
    public MeResponse me(Authentication authentication) {
        // On transforme la liste des autorités ["ROLE_MODERATEUR", "ROLE_USER"]
        // en liste de rôles lisibles ["MODERATEUR", "USER"].
        List<String> roles = authentication.getAuthorities().stream()
                // GrantedAuthority::getAuthority : texte de l'autorité, ex. "ROLE_USER".
                .map(GrantedAuthority::getAuthority)
                // On ne garde que les autorités qui sont des rôles.
                .filter(authority -> authority.startsWith("ROLE_"))
                // On retire le préfixe "ROLE_".
                .map(authority -> authority.substring("ROLE_".length()))
                // Ordre alphabétique, pour une réponse stable.
                .sorted()
                // Collecte le résultat dans une liste (Java 16+).
                .toList();
        // authentication.getName() : identifiant de l'utilisateur (valeur de X_USER_ID).
        return new MeResponse(authentication.getName(), roles);
    }
}
