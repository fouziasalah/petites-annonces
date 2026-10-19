import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AnnonceApi } from '../../core/api/annonce-api.service';
import { CategorieApi } from '../../core/api/categorie-api.service';
import { Categorie } from '../../core/api/models';
import { appliquerErreursServeur } from '../../core/api/problem-detail';
import { NotificationService } from '../../core/notification/notification.service';

/**
 * Création (/annonces/nouvelle) et modification (/annonces/:id/modifier).
 * Les règles de validation reprennent celles du backend (titre 5-100, description ≤ 2000,
 * prix ≥ 0) pour un retour immédiat ; les erreurs 400 du serveur sont aussi affichées.
 */
@Component({
  selector: 'app-annonce-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <h1>{{ id() ? 'Modifier l’annonce' : 'Nouvelle annonce' }}</h1>
    @if (id()) {
      <p class="muted">Après modification, l'annonce repassera en attente de modération.</p>
    }

    <form class="card" [formGroup]="form" (ngSubmit)="enregistrer()">
      <div class="field">
        <label for="titre">Titre</label>
        <input id="titre" formControlName="titre" maxlength="100" />
        @if (erreur('titre'); as e) {
          <div class="error">{{ e }}</div>
        }
      </div>

      <div class="field">
        <label for="description">Description</label>
        <textarea id="description" formControlName="description" rows="6" maxlength="2000"></textarea>
        @if (erreur('description'); as e) {
          <div class="error">{{ e }}</div>
        }
      </div>

      <div class="field">
        <label for="prix">Prix (€)</label>
        <input id="prix" type="number" min="0" step="0.01" formControlName="prix" />
        @if (erreur('prix'); as e) {
          <div class="error">{{ e }}</div>
        }
      </div>

      <div class="field">
        <label for="categorieId">Catégorie</label>
        <select id="categorieId" formControlName="categorieId">
          <option [ngValue]="null" disabled>Choisir…</option>
          @for (c of categories(); track c.id) {
            <option [ngValue]="c.id">{{ c.libelle }}</option>
          }
        </select>
        @if (erreur('categorieId'); as e) {
          <div class="error">{{ e }}</div>
        }
      </div>

      <div class="toolbar">
        <button type="submit" [disabled]="envoi()">Enregistrer</button>
        <a routerLink="/mes-annonces">Annuler</a>
      </div>
    </form>
  `,
})
export class AnnonceForm implements OnInit {
  /** Absent en création, présent en modification (paramètre de route :id). */
  readonly id = input<string>();

  private readonly api = inject(AnnonceApi);
  private readonly categorieApi = inject(CategorieApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly categories = signal<Categorie[]>([]);
  protected readonly envoi = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    titre: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(100)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    prix: [0, [Validators.required, Validators.min(0)]],
    categorieId: [null as number | null, Validators.required],
  });

  ngOnInit(): void {
    this.categorieApi.lister().subscribe((c) => this.categories.set(c));
    const id = this.id();
    if (id) {
      this.api.consulter(id).subscribe((a) =>
        this.form.setValue({ titre: a.titre, description: a.description, prix: a.prix, categorieId: a.categorieId }),
      );
    }
  }

  protected erreur(champ: keyof typeof this.form.controls): string | null {
    const control = this.form.controls[champ];
    if (!control.touched || !control.errors) {
      return null;
    }
    const e = control.errors;
    if (e['serveur']) return e['serveur'] as string;
    if (e['required']) return 'Champ obligatoire.';
    if (e['minlength']) return `Au moins ${(e['minlength'] as { requiredLength: number }).requiredLength} caractères.`;
    if (e['maxlength']) return `Au plus ${(e['maxlength'] as { requiredLength: number }).requiredLength} caractères.`;
    if (e['min']) return 'Le prix ne peut pas être négatif.';
    return 'Valeur invalide.';
  }

  protected enregistrer(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const valeur = this.form.getRawValue();
    const request = { ...valeur, categorieId: valeur.categorieId as number };
    const id = this.id();
    const appel = id ? this.api.modifier(id, request) : this.api.creer(request);

    this.envoi.set(true);
    appel.subscribe({
      next: (annonce) => {
        this.notifications.show('Annonce enregistrée : elle sera visible après modération.', 'succes');
        void this.router.navigate(['/annonces', annonce.id]);
      },
      error: (error: unknown) => {
        this.envoi.set(false);
        appliquerErreursServeur(error, this.form);
      },
    });
  }
}
