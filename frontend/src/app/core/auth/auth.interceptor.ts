import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';

/**
 * Ajoute « Authorization: Bearer <access_token> » UNIQUEMENT aux appels vers la gateway (FR-01).
 *
 * Envoyer le token ailleurs (Keycloak, un CDN, un site tiers) le ferait fuiter : la
 * comparaison se fait sur le préfixe exact de l'URL de la gateway (schéma + hôte + port + /).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const gatewayPrefix = `${inject(APP_CONFIG).gatewayUrl}/`;
  const token = inject(AuthService).getAccessToken();

  if (!token || !req.url.startsWith(gatewayPrefix)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
