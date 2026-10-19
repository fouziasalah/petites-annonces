package fr.petitesannonces.security;

// Jakarta Servlet : requête, réponse et chaîne de filtres HTTP (fournies par Tomcat)
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// SLF4J : journalisation (logs), fournie par Spring Boot
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Spring Web
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
// Spring Security : utilisateur authentifié et contexte de sécurité
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
// Java standard
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Lit l'identité de l'utilisateur dans les en-têtes HTTP et la donne à Spring Security.
 *
 * <p>D'où viennent ces en-têtes ? La gateway Kong vérifie le token JWT émis par Keycloak, puis
 * ajoute à la requête :
 * <ul>
 *   <li>{@code X_USER_ID} : l'identifiant de l'utilisateur (claim {@code sub} du token) ;</li>
 *   <li>{@code X_ROLE_ID} : ses rôles séparés par des virgules, ex. {@code MODERATEUR,USER}.</li>
 * </ul>
 * Le backend ne lit jamais le token lui-même : il fait confiance à ces en-têtes, car seul la
 * gateway peut l'appeler (le backend n'est pas exposé publiquement).
 *
 * <p>Ce que fait le filtre, pour chaque requête :
 * <ol>
 *   <li>si {@code X_USER_ID} est présent : crée un utilisateur Spring Security (identifiant +
 *       autorités {@code ROLE_<rôle>}) et le place dans le contexte de sécurité ;</li>
 *   <li>sinon : ne fait rien ; la requête reste anonyme et Spring Security répondra 401 ;</li>
 *   <li>dans tous les cas, vide le contexte à la fin de la requête.</li>
 * </ol>
 *
 * <p>Utilisé par : {@code SecurityConfig}, qui l'ajoute à la chaîne de sécurité.
 */
// extends OncePerRequestFilter (Spring Web) : filtre HTTP garanti exécuté une seule fois par requête.
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    // Noms des en-têtes posés par la gateway.
    public static final String USER_ID_HEADER = "X_USER_ID";
    public static final String ROLES_HEADER = "X_ROLE_ID";
    // Préfixe attendu par hasRole('USER') : Spring Security cherche l'autorité « ROLE_USER ».
    private static final String ROLE_PREFIX = "ROLE_";

    // LoggerFactory.getLogger (SLF4J) : crée un logger nommé d'après cette classe. Son niveau se
    // règle dans application.yml (logging.level.fr.petitesannonces.security).
    private static final Logger log = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    // Accès au contexte de sécurité courant (lié au thread qui traite la requête).
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    /**
     * Méthode appelée par Spring pour chaque requête HTTP.
     *
     * @param chain suite de la chaîne : l'appeler fait continuer la requête vers les filtres
     *              suivants puis le contrôleur
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getHeader (Servlet) : valeur d'un en-tête HTTP, ou null s'il est absent.
        String userId = request.getHeader(USER_ID_HEADER);
        String roles = request.getHeader(ROLES_HEADER);

        // En niveau DEBUG, on trace ce que la gateway a transmis (utile pour vérifier que le
        // token n'arrive pas jusqu'ici : « Authorization présent=false »).
        if (log.isDebugEnabled()) {
            log.debug("{} {} | {}={} | {}={} | Authorization présent={}",
                    request.getMethod(), request.getRequestURI(),
                    USER_ID_HEADER, userId, ROLES_HEADER, roles,
                    request.getHeader(HttpHeaders.AUTHORIZATION) != null);
        }

        try {
            // StringUtils.hasText (Spring) : vrai si la chaîne n'est ni null, ni vide, ni que des espaces.
            if (StringUtils.hasText(userId)) {
                // Contexte de sécurité vide, à remplir avec l'utilisateur.
                SecurityContext context = contextHolder.createEmptyContext();
                // UsernamePasswordAuthenticationToken.authenticated (Spring Security) : utilisateur
                // déjà authentifié. Arguments : identifiant (le « principal »), mot de passe (aucun),
                // et liste des autorités (rôles).
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        userId.trim(), null, toAuthorities(roles)));
                // L'utilisateur devient visible pour Spring Security, @PreAuthorize,
                // @AuthenticationPrincipal… pendant toute la requête.
                contextHolder.setContext(context);
            }
            // Laisse la requête continuer (filtres suivants, puis contrôleur).
            chain.doFilter(request, response);
        } finally {
            // Toujours exécuté, même en cas d'erreur : un thread du serveur traite ensuite d'autres
            // requêtes, il ne doit pas garder l'identité de l'utilisateur précédent.
            contextHolder.clearContext();
        }
    }

    /**
     * Transforme l'en-tête des rôles en autorités Spring Security.
     * Exemple : {@code "MODERATEUR, USER"} → {@code [ROLE_MODERATEUR, ROLE_USER]} ;
     * en-tête absent ou vide → liste vide (l'utilisateur est identifié mais n'a aucun rôle).
     */
    static List<GrantedAuthority> toAuthorities(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        // split(",") découpe le texte ; chaque morceau est nettoyé, dédoublonné puis préfixé.
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                // SimpleGrantedAuthority (Spring Security) : autorité représentée par un simple texte.
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
