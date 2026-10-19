import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { NotificationService } from './core/notification/notification.service';

/**
 * Coquille de l'application : navigation et notifications.
 * Les entrées « Modération » et « Catégories » ne sont affichées qu'aux rôles concernés
 * (FR-03) — simple confort, le backend reste seul juge.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly notifications = inject(NotificationService);
}
