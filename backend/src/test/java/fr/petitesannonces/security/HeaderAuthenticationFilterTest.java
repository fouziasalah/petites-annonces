package fr.petitesannonces.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthenticationFilterTest {

    @Test
    void transformeLesRolesEnAutorites() {
        assertThat(HeaderAuthenticationFilter.toAuthorities(" MODERATEUR, USER,,USER "))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MODERATEUR", "ROLE_USER");
    }

    @Test
    void headerAbsentOuVideDonneAucuneAutorite() {
        assertThat(HeaderAuthenticationFilter.toAuthorities(null)).isEmpty();
        assertThat(HeaderAuthenticationFilter.toAuthorities(" ")).isEmpty();
    }
}
