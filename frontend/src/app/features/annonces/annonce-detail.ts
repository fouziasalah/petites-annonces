import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AnnonceApi } from '../../core/api/annonce-api.service';
import { CategorieApi } from '../../core/api/categorie-api.service';
import { Annonce, Categorie } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notification/notification.service';

@Component({
  selector: 'app-annonce-detail',
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    @if (annonce(); as a) {
      <article class="card">
        <h1>{{ a.titre }}</h1>
        <p><span class="badge" [class]="a.statut">{{ a.statut }}</span></p>
        <p class="prix">{{ a.prix | currency: 'EUR' }}</p>
        <p class="muted">{{ libelle(a.categorieId) }} · publiée le {{ a.dateCreation | date: 'dd/MM/yyyy HH:mm' }}</p>
        <p class="description">{{ a.description }}</p>
        @if (a.motifRejet) {
          <p><strong>Motif du rejet :</strong> {{ a.motifRejet }}</p>
        }
        <div class="toolbar">
          @if (estAuteur()) {
            <a class="button" [routerLink]="['/annonces', a.id, 'modifier']">Modifier</a>
          }
          @if (estAuteur() || auth.hasRole('MODERATEUR')) {
            <button type="button" class="danger" (click)="supprimer(a)">Supprimer</button>
          }
        </div>
      </article>
    } @else if (introuvable()) {
      <p>Cette annonce n'existe pas ou n'est pas visible.</p>
    }
    <a routerLink="/annonces">← Retour aux annonces</a>
  `,
  styles: `
    .prix {
      font-size: 1.3rem;
      font-weight: 700;
    }
    .description {
      white-space: pre-line;
    }
  `,
})
export class AnnonceDetail implements OnInit {
  /** Paramètre de route :id (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(AnnonceApi);
  private readonly categorieApi = inject(CategorieApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly annonce = signal<Annonce | null>(null);
  protected readonly introuvable = signal(false);
  private readonly categories = signal<Categorie[]>([]);
  protected readonly estAuteur = computed(() => this.annonce()?.auteurId === this.auth.userId());

  ngOnInit(): void {
    this.categorieApi.lister().subscribe((c) => this.categories.set(c));
    this.api.consulter(this.id()).subscribe({
      next: (a) => this.annonce.set(a),
      // 404 : le backend ne distingue volontairement pas « inexistante » et « non visible ».
      error: (e: HttpErrorResponse) => this.introuvable.set(e.status === 404),
    });
  }

  protected libelle(id: number): string {
    return this.categories().find((c) => c.id === id)?.libelle ?? '';
  }

  protected supprimer(a: Annonce): void {
    if (!confirm(`Supprimer « ${a.titre} » ?`)) {
      return;
    }
    this.api.supprimer(a.id).subscribe(() => {
      this.notifications.show('Annonce supprimée.', 'succes');
      void this.router.navigate(['/mes-annonces']);
    });
  }
}
