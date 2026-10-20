import { Injectable, computed, inject, signal } from '@angular/core';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';
import { filter } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { decodeJwtPayload } from './jwt';

export type Role = 'USER' | 'MODERATEUR' | 'ADMIN';

/**
 * Façade d'authentification au-dessus d'angular-oauth2-oidc.
 *
 * Flux : Authorization Code + PKCE (S256), client public « annonces-front ».
 *  - init() : récupère la configuration OIDC du realm, traite le retour de Keycloak
 *    (?code=…&state=…) en échangeant le code + code_verifier contre les tokens ;
 *    sans token valide → redirection vers la page de login (KC-01).
 *  - Les tokens sont dans sessionStorage (stockage par défaut de la librairie),
 *    JAMAIS dans localStorage (FR-02) : ils disparaissent à la fermeture de l'onglet.
 *  - Renouvellement automatique à 75 % de la durée de vie avec le refresh token (FR-04).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly config = inject(APP_CONFIG);

  private readonly accessToken = signal<string | null>(null);
  private readonly claims = computed(() => {
    const token = this.accessToken();
    return token ? decodeJwtPayload(token) : null;
  });

  readonly username = computed(() => this.claims()?.preferred_username ?? '');
  /** « sub » Keycloak : la même valeur que X_USER_ID côté backend, donc que auteurId. */
  readonly userId = computed(() => this.claims()?.sub ?? '');
  /** Rôles lus dans l'ACCESS token (realm_access.roles), comme le fait Kong. */
  readonly roles = computed(() => this.claims()?.realm_access?.roles ?? []);

  async init(): Promise<void> {
    const origin = window.location.origin;
    const authConfig: AuthConfig = {
      issuer: `${this.config.keycloakUrl}/realms/${this.config.realm}`,
      clientId: this.config.clientId,
      responseType: 'code', // Authorization Code ; PKCE est activé par défaut en mode code
      scope: 'openid profile',
      redirectUri: `${origin}/`,
      postLogoutRedirectUri: `${origin}/`,
      // HTTPS exigé, sauf pour localhost (développement et tunnel SSH).
      // requireHttps: 'remoteOnly',
      requireHttps:false,
      // Refresh à 75 % de la durée de vie de l'access token (5 min → ~3 min 45).
      timeoutFactor: 0.75,
      // Pas d'iframe de vérification de session : bloquée par les navigateurs (cookies tiers).
      sessionChecksEnabled: false,
    };
    this.oauth.configure(authConfig);

    // Toute réception ou rafraîchissement de token met à jour les signaux (et donc les menus).
    this.oauth.events
      .pipe(filter((e) => e.type === 'token_received' || e.type === 'token_refreshed'))
      .subscribe(() => this.accessToken.set(this.oauth.getAccessToken()));

    await this.oauth.loadDiscoveryDocumentAndTryLogin();
    this.nettoyerParametreIss(authConfig.issuer as string);

    if (!this.oauth.hasValidAccessToken()) {
      this.login();
      // La page va être quittée : on ne résout jamais la promesse pour ne pas afficher l'app.
      return new Promise<void>(() => undefined);
    }

    this.accessToken.set(this.oauth.getAccessToken());
    // En mode « code », la librairie utilise le refresh token (pas d'iframe silencieuse).
    this.oauth.setupAutomaticSilentRefresh();
  }

  login(): void {
    // Génère code_verifier + code_challenge, state et nonce, puis redirige vers /auth.
    this.oauth.initCodeFlow();
  }

  /** Ferme la session SSO côté Keycloak (end_session + id_token_hint) puis revient sur l'app. */
  logout(): void {
    this.oauth.logOut();
  }

  getAccessToken(): string | null {
    return this.oauth.getAccessToken();
  }

  hasRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  /**
   * Au retour du login, Keycloak ajoute ?iss=<émetteur> (RFC 9207) pour que le client
   * vérifie que la réponse vient bien du serveur auquel il s'est adressé. La librairie
   * retire code et state de l'URL mais pas iss : on le contrôle puis on le retire.
   */
  private nettoyerParametreIss(issuerAttendu: string): void {
    const url = new URL(window.location.href);
    const iss = url.searchParams.get('iss');
    if (iss === null) {
      return;
    }
    url.searchParams.delete('iss');
    history.replaceState(history.state, '', url.toString());
    if (iss !== issuerAttendu) {
      // Réponse d'un autre émetteur : on ne garde aucun token (purge locale, sans redirection).
      this.oauth.logOut(true);
      throw new Error(`Paramètre iss inattendu au retour de l'authentification : ${iss}`);
    }
  }
}
