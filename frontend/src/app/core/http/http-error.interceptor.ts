import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notification/notification.service';

/**
 * Traitement centralisé des erreurs de sécurité renvoyées par Kong ou le backend.
 *
 *  - 401 : plus d'identité valide (token expiré, révoqué…) → retour au login.
 *          Grâce à la session SSO Keycloak, c'est souvent transparent.
 *  - 403 : identifié mais non autorisé → message, SANS déconnexion (FR-06).
 *  - 429 : quota Kong dépassé → délai lu dans Retry-After (FR-05). Ce header n'est lisible
 *          en JavaScript que parce que Kong l'expose (cors.exposed_headers).
 *
 * L'erreur est toujours propagée : l'écran appelant peut compléter (ex. erreurs de champs 400).
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        switch (error.status) {
          case 401:
            auth.login();
            break;
          case 403:
            notifications.show('Accès refusé : vous n’avez pas les droits pour cette action.', 'erreur');
            break;
          case 429: {
            const retryAfter = error.headers.get('Retry-After');
            notifications.show(
              `Trop de requêtes (3 par minute maximum). Réessayez dans ${retryAfter ?? 'quelques'} secondes.`,
              'erreur',
            );
            break;
          }
        }
      }
      return throwError(() => error);
    }),
  );
};
