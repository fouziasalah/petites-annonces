import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategorieApi } from '../../core/api/categorie-api.service';
import { Categorie } from '../../core/api/models';
import { appliquerErreursServeur } from '../../core/api/problem-detail';
import { NotificationService } from '../../core/notification/notification.service';

/** Gestion des catégories (ADMIN). */
@Component({
  selector: 'app-categories',
  imports: [ReactiveFormsModule],
  template: `
    <h1>Catégories</h1>

    <form class="card toolbar" [formGroup]="form" (ngSubmit)="ajouter()">
      <input placeholder="Nouvelle catégorie (2 à 50 caractères)" formControlName="libelle" maxlength="50" style="flex: 1" />
      <button type="submit" [disabled]="form.invalid">Ajouter</button>
      @if (form.controls.libelle.errors?.['serveur']; as message) {
        <div class="error" style="width: 100%">{{ message }}</div>
      }
    </form>

    @for (c of categories(); track c.id) {
      <div class="card toolbar">
        <span style="flex: 1">{{ c.libelle }}</span>
        <button type="button" class="danger" (click)="supprimer(c)">Supprimer</button>
      </div>
    }
  `,
})
export class Categories implements OnInit {
  private readonly api = inject(CategorieApi);
  private readonly notifications = inject(NotificationService);

  protected readonly categories = signal<Categorie[]>([]);
  protected readonly form = inject(NonNullableFormBuilder).group({
    libelle: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
  });

  ngOnInit(): void {
    this.charger();
  }

  protected ajouter(): void {
    this.api.creer(this.form.getRawValue().libelle.trim()).subscribe({
      next: (c) => {
        this.categories.update((list) => [...list, c].sort((a, b) => a.libelle.localeCompare(b.libelle)));
        this.form.reset();
        this.notifications.show('Catégorie créée.', 'succes');
      },
      error: (e: unknown) => {
        if (!appliquerErreursServeur(e, this.form) && e instanceof HttpErrorResponse && e.status === 409) {
          this.notifications.show('Cette catégorie existe déjà.', 'erreur');
        }
      },
    });
  }

  protected supprimer(c: Categorie): void {
    this.api.supprimer(c.id).subscribe({
      next: () => {
        this.categories.update((list) => list.filter((x) => x.id !== c.id));
        this.notifications.show('Catégorie supprimée.', 'succes');
      },
      error: (e: unknown) => {
        if (e instanceof HttpErrorResponse && e.status === 409) {
          this.notifications.show(`« ${c.libelle} » est utilisée par des annonces : suppression impossible.`, 'erreur');
        }
      },
    });
  }

  private charger(): void {
    this.api.lister().subscribe((c) => this.categories.set(c));
  }
}
