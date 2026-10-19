import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnnonceApi } from '../../core/api/annonce-api.service';
import { Annonce, Page } from '../../core/api/models';

/** Toutes les annonces de l'utilisateur courant, quel que soit leur statut. */
@Component({
  selector: 'app-mes-annonces',
  imports: [RouterLink, CurrencyPipe],
  template: `
    <div class="toolbar">
      <h1 style="flex: 1; margin: 0">Mes annonces</h1>
      <a class="button" routerLink="/annonces/nouvelle">Nouvelle annonce</a>
    </div>

    @if (page(); as p) {
      @for (a of p.content; track a.id) {
        <div class="card ligne">
          <a [routerLink]="['/annonces', a.id]">{{ a.titre }}</a>
          <span>{{ a.prix | currency: 'EUR' }}</span>
          <span class="badge" [class]="a.statut">{{ a.statut }}</span>
          <a [routerLink]="['/annonces', a.id, 'modifier']">Modifier</a>
          @if (a.motifRejet) {
            <span class="muted motif">Rejetée : {{ a.motifRejet }}</span>
          }
        </div>
      } @empty {
        <p class="muted">Vous n'avez encore publié aucune annonce.</p>
      }
      @if (p.totalPages > 1) {
        <div class="toolbar">
          <button type="button" class="secondary" [disabled]="p.page === 0" (click)="charger(p.page - 1)">Précédent</button>
          <span>Page {{ p.page + 1 }} / {{ p.totalPages }}</span>
          <button type="button" class="secondary" [disabled]="p.page + 1 >= p.totalPages" (click)="charger(p.page + 1)">
            Suivant
          </button>
        </div>
      }
    }
  `,
  styles: `
    .ligne {
      display: grid;
      grid-template-columns: 1fr auto auto auto;
      gap: 1rem;
      align-items: center;
    }
    .motif {
      grid-column: 1 / -1;
    }
  `,
})
export class MesAnnonces implements OnInit {
  private readonly api = inject(AnnonceApi);
  protected readonly page = signal<Page<Annonce> | null>(null);

  ngOnInit(): void {
    this.charger(0);
  }

  protected charger(page: number): void {
    this.api.mesAnnonces(page, 10).subscribe((p) => this.page.set(p));
  }
}
