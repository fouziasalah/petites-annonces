import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AnnonceApi } from '../../core/api/annonce-api.service';
import { Annonce } from '../../core/api/models';
import { NotificationService } from '../../core/notification/notification.service';

/** File de modération (MODERATEUR) : annonces en attente, les plus anciennes d'abord. */
@Component({
  selector: 'app-moderation',
  imports: [FormsModule, RouterLink, CurrencyPipe, DatePipe],
  template: `
    <h1>Modération</h1>
    @for (a of annonces(); track a.id) {
      <div class="card">
        <div class="toolbar">
          <a style="flex: 1" [routerLink]="['/annonces', a.id]"><strong>{{ a.titre }}</strong></a>
          <span>{{ a.prix | currency: 'EUR' }}</span>
          <span class="muted">déposée le {{ a.dateCreation | date: 'dd/MM/yyyy HH:mm' }}</span>
        </div>
        <p class="description">{{ a.description }}</p>
        <div class="toolbar">
          <button type="button" (click)="publier(a)">Publier</button>
          <input style="flex: 1" placeholder="Motif du rejet (obligatoire)" [(ngModel)]="motifs[a.id]" />
          <button type="button" class="danger" [disabled]="!motifs[a.id]?.trim()" (click)="rejeter(a)">Rejeter</button>
        </div>
      </div>
    } @empty {
      <p class="muted">Aucune annonce en attente.</p>
    }
  `,
  styles: `
    .description {
      white-space: pre-line;
    }
  `,
})
export class Moderation implements OnInit {
  private readonly api = inject(AnnonceApi);
  private readonly notifications = inject(NotificationService);

  protected readonly annonces = signal<Annonce[]>([]);
  protected motifs: Record<string, string> = {};

  ngOnInit(): void {
    this.api.enAttente(0, 50).subscribe((p) => this.annonces.set(p.content));
  }

  protected publier(a: Annonce): void {
    this.api.publier(a.id).subscribe(() => this.retirer(a, 'Annonce publiée.'));
  }

  protected rejeter(a: Annonce): void {
    this.api.rejeter(a.id, this.motifs[a.id].trim()).subscribe(() => this.retirer(a, 'Annonce rejetée.'));
  }

  /** Mise à jour locale de la liste : évite un rechargement (et un appel de quota). */
  private retirer(a: Annonce, message: string): void {
    this.annonces.update((list) => list.filter((x) => x.id !== a.id));
    this.notifications.show(message, 'succes');
  }
}
