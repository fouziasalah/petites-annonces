import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { NotificationService } from '../notification/notification.service';
import { AuthService, Role } from './auth.service';

/**
 * Bloque l'accès à une route si l'utilisateur n'a pas le rôle (FR-03).
 *
 * CONFORT D'AFFICHAGE UNIQUEMENT : un utilisateur peut modifier le JavaScript ou appeler
 * l'API directement. La vraie règle est le @PreAuthorize du backend, qui répondrait 403.
 */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    if (inject(AuthService).hasRole(role)) {
      return true;
    }
    inject(NotificationService).show('Cette page est réservée aux utilisateurs habilités.', 'erreur');
    return inject(Router).createUrlTree(['/annonces']);
  };
}
