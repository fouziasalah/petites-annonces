/** Claims de l'access token Keycloak utilisés par le front. */
export interface AccessTokenClaims {
  sub: string;
  preferred_username?: string;
  exp: number;
  realm_access?: { roles?: string[] };
}

/**
 * Décode la charge utile d'un JWT (base64url → JSON).
 *
 * AUCUNE vérification de signature ici, et ce n'est pas nécessaire : le front ne prend
 * aucune décision de sécurité avec ces informations (affichage des menus seulement).
 * La vraie vérification est faite par Kong, et l'autorisation par le backend.
 */
export function decodeJwtPayload<T = AccessTokenClaims>(token: string): T | null {
  const payload = token.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as T;
  } catch {
    return null;
  }
}
