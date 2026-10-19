package fr.petitesannonces.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Reconstruit l'identité Spring Security à partir des headers posés par Kong.
 *
 * <p><b>Pourquoi peut-on faire confiance à ces headers ?</b> Le backend n'est joignable que sur le
 * réseau Docker {@code internal}, par la gateway. Kong a validé le JWT (signature, émetteur,
 * expiration), écrasé toute valeur envoyée par le client, puis posé :
 * <ul>
 *   <li>{@code X_USER_ID} : le claim {@code sub} du token ;</li>
 *   <li>{@code X_ROLE_ID} : les rôles métier, séparés par des virgules (ex. {@code MODERATEUR,USER}).</li>
 * </ul>
 *
 * <p><b>Ce que fait le filtre :</b> si {@code X_USER_ID} est présent, il place dans le
 * {@link SecurityContextHolder} une authentification dont le principal est l'identifiant et les
 * autorités {@code ROLE_<rôle>} (préfixe attendu par {@code hasRole(...)}). Sinon il ne fait rien :
 * la requête reste anonyme et la règle {@code anyRequest().authenticated()} déclenche le 401
 * (via l'{@code AuthenticationEntryPoint}). Le filtre ne renvoie donc jamais lui-même d'erreur :
 * chaque responsabilité reste à sa place.
 *
 * <p>Le contexte est <b>toujours</b> vidé en fin de requête : un thread du pool Tomcat ne doit
 * jamais emporter l'identité d'un utilisateur vers la requête suivante (BK-05).
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X_USER_ID";
    public static final String ROLES_HEADER = "X_ROLE_ID";
    private static final String ROLE_PREFIX = "ROLE_";

    private static final Logger log = LoggerFactory.getLogger(HeaderAuthenticationFilter.class);

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        String roles = request.getHeader(ROLES_HEADER);

        // GW-08 : preuve que Kong transmet l'identité en headers et retire le token.
        if (log.isDebugEnabled()) {
            log.debug("{} {} | {}={} | {}={} | Authorization présent={}",
                    request.getMethod(), request.getRequestURI(),
                    USER_ID_HEADER, userId, ROLES_HEADER, roles,
                    request.getHeader(HttpHeaders.AUTHORIZATION) != null);
        }

        try {
            if (StringUtils.hasText(userId)) {
                SecurityContext context = contextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        userId.trim(), null, toAuthorities(roles)));
                contextHolder.setContext(context);
            }
            chain.doFilter(request, response);
        } finally {
            contextHolder.clearContext();
        }
    }

    /** "MODERATEUR, USER" → [ROLE_MODERATEUR, ROLE_USER] ; header absent ou vide → aucune autorité. */
    static List<GrantedAuthority> toAuthorities(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
