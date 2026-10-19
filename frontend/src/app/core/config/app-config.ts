import { InjectionToken } from '@angular/core';

/**
 * Configuration lue AU DÉMARRAGE dans /config.json, pas compilée dans le bundle.
 *
 * Pourquoi ? Une même image Docker sert dans tous les environnements : seules les URLs
 * changent. D'où vient le fichier :
 *  - en développement (ng serve) : public/config.json ;
 *  - dans Docker : généré au démarrage du conteneur à partir des variables d'environnement
 *    KEYCLOAK_URL, KEYCLOAK_REALM, KEYCLOAK_CLIENT_ID et GATEWAY_URL
 *    (voir docker/40-generer-config.sh et le Dockerfile).
 */
export interface AppConfig {
  /** URL publique de Keycloak, vue par le navigateur (ex. http://localhost:8180). */
  keycloakUrl: string;
  realm: string;
  clientId: string;
  /** URL publique de la gateway Kong : seule destination qui reçoit le Bearer. */
  gatewayUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export async function loadAppConfig(): Promise<AppConfig> {
  // no-store : après un redéploiement, le navigateur ne doit pas garder d'anciennes URLs.
  const response = await fetch('/config.json', { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`Impossible de charger /config.json (HTTP ${response.status})`);
  }
  return (await response.json()) as AppConfig;
}
