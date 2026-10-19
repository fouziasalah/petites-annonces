package fr.petitesannonces.security;

// JUnit 5
import org.junit.jupiter.api.Test;
// Spring Security
import org.springframework.security.core.GrantedAuthority;

// AssertJ (fourni par spring-boot-starter-test) : vérifications lisibles « assertThat(…).… »
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitaire de {@link HeaderAuthenticationFilter#toAuthorities(String)} : pas de Spring,
 * pas de base, juste la transformation de l'en-tête des rôles. Lancement : {@code ./mvnw test}.
 * Placé dans le même package que le filtre pour accéder à la méthode (non publique).
 */
class HeaderAuthenticationFilterTest {

    // @Test (JUnit) : méthode exécutée comme un test.
    @Test
    void transformeLesRolesEnAutorites() {
        // Espaces, valeur vide et doublon doivent être ignorés.
        assertThat(HeaderAuthenticationFilter.toAuthorities(" MODERATEUR, USER,,USER "))
                // extracting : on compare le texte de chaque autorité.
                .extracting(GrantedAuthority::getAuthority)
                // containsExactly : exactement ces valeurs, dans cet ordre.
                .containsExactly("ROLE_MODERATEUR", "ROLE_USER");
    }

    @Test
    void headerAbsentOuVideDonneAucuneAutorite() {
        assertThat(HeaderAuthenticationFilter.toAuthorities(null)).isEmpty();
        assertThat(HeaderAuthenticationFilter.toAuthorities(" ")).isEmpty();
    }
}
