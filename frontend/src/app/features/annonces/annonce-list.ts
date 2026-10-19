import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnnonceApi } from '../../core/api/annonce-api.service';
import { CategorieApi } from '../../core/api/categorie-api.service';
import { Annonce, Categorie, Page } from '../../core/api/models';

/** Annonces publiées, filtrables par catégorie, paginées. */
@Component({
  selector: 'app-annonce-list',
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    <h1>Annonces publiées</h1>

    <div class="toolbar">
      <label for="categorie">Catégorie</label>
      <select id="categorie" style="max-width: 16rem" (change)="filtrer($any($event.target).value)">
        <option value="">Toutes</option>
        @for (c of categories(); track c.id) {
          <option [value]="c.id">{{ c.libelle }}</option>
        }
      </select>
    </div>

    @if (page(); as p) {
      @for (a of p.content; track a.id) {
        <a class="card annonce" [routerLink]="['/annonces', a.id]">
          <strong>{{ a.titre }}</strong>
          <span class="prix">{{ a.prix | currency: 'EUR' }}</span>
          <span class="muted">{{ libelle(a.categorieId) }} · {{ a.dateCreation | date: 'dd/MM/yyyy' }}</span>
        </a>
      } @empty {
        <p class="muted">Aucune annonce publiée pour le moment.</p>
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
    .annonce {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 0.25rem;
      color: inherit;
      text-decoration: none;
    }
    .prix {
      font-weight: 700;
    }
  `,
})
export class AnnonceList implements OnInit {
  private readonly annonces = inject(AnnonceApi);
  private readonly categorieApi = inject(CategorieApi);

  protected readonly page = signal<Page<Annonce> | null>(null);
  protected readonly categories = signal<Categorie[]>([]);
  private categorieId?: number;

  ngOnInit(): void {
    this.categorieApi.lister().subscribe((c) => this.categories.set(c));
    this.charger(0);
  }

  protected filtrer(value: string): void {
    this.categorieId = value ? Number(value) : undefined;
    this.charger(0);
  }

  protected charger(page: number): void {
    this.annonces.publiees(page, 10, this.categorieId).subscribe((p) => this.page.set(p));
  }

  protected libelle(id: number): string {
    return this.categories().find((c) => c.id === id)?.libelle ?? '';
  }
}
