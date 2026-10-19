package fr.petitesannonces.controller;

import fr.petitesannonces.dto.MeResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Renvoie l'identité telle que le backend la voit : utile pour démontrer de bout en bout que le
 * {@code sub} du token est arrivé jusqu'ici via Kong (GW-05, GW-06).
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new MeResponse(authentication.getName(), roles);
    }
}
