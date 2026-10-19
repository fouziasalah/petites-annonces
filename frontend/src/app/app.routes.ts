import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';

// Chargement paresseux : chaque écran est un fichier JavaScript séparé, téléchargé à la demande.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'annonces' },
  {
    path: 'annonces',
    loadComponent: () => import('./features/annonces/annonce-list').then((m) => m.AnnonceList),
  },
  {
    path: 'annonces/nouvelle',
    loadComponent: () => import('./features/annonces/annonce-form').then((m) => m.AnnonceForm),
  },
  {
    path: 'annonces/:id',
    loadComponent: () => import('./features/annonces/annonce-detail').then((m) => m.AnnonceDetail),
  },
  {
    path: 'annonces/:id/modifier',
    loadComponent: () => import('./features/annonces/annonce-form').then((m) => m.AnnonceForm),
  },
  {
    path: 'mes-annonces',
    loadComponent: () => import('./features/annonces/mes-annonces').then((m) => m.MesAnnonces),
  },
  {
    path: 'moderation',
    canActivate: [roleGuard('MODERATEUR')],
    loadComponent: () => import('./features/moderation/moderation').then((m) => m.Moderation),
  },
  {
    path: 'categories',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./features/categories/categories').then((m) => m.Categories),
  },
  { path: '**', redirectTo: 'annonces' },
];
