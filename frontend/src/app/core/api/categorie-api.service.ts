import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { Categorie } from './models';

/**
 * Les catégories changent rarement : elles sont chargées UNE fois puis mises en cache.
 * Avec un quota de 3 appels/minute, chaque requête économisée compte.
 */
@Injectable({ providedIn: 'root' })
export class CategorieApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(APP_CONFIG).gatewayUrl}/api/categories`;
  private cache$?: Observable<Categorie[]>;

  lister(): Observable<Categorie[]> {
    // shareReplay({ refCount: false }) : la réponse est rejouée aux abonnés suivants
    // sans nouvel appel HTTP ; en cas d'erreur, le cache est vidé pour pouvoir réessayer.
    this.cache$ ??= this.http.get<Categorie[]>(this.url).pipe(
      tap({ error: () => (this.cache$ = undefined) }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.cache$;
  }

  creer(libelle: string): Observable<Categorie> {
    return this.http.post<Categorie>(this.url, { libelle }).pipe(tap(() => (this.cache$ = undefined)));
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(tap(() => (this.cache$ = undefined)));
  }
}
