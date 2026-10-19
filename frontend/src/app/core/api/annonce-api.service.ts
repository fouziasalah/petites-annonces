import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { Annonce, AnnonceRequest, Page } from './models';

@Injectable({ providedIn: 'root' })
export class AnnonceApi {
  private readonly http = inject(HttpClient);
  private readonly api = `${inject(APP_CONFIG).gatewayUrl}/api`;

  publiees(page: number, size: number, categorieId?: number): Observable<Page<Annonce>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (categorieId) {
      params = params.set('categorieId', categorieId);
    }
    return this.http.get<Page<Annonce>>(`${this.api}/annonces`, { params });
  }

  mesAnnonces(page: number, size: number): Observable<Page<Annonce>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Annonce>>(`${this.api}/annonces/mes-annonces`, { params });
  }

  consulter(id: string): Observable<Annonce> {
    return this.http.get<Annonce>(`${this.api}/annonces/${id}`);
  }

  creer(request: AnnonceRequest): Observable<Annonce> {
    return this.http.post<Annonce>(`${this.api}/annonces`, request);
  }

  modifier(id: string, request: AnnonceRequest): Observable<Annonce> {
    return this.http.put<Annonce>(`${this.api}/annonces/${id}`, request);
  }

  supprimer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/annonces/${id}`);
  }

  enAttente(page: number, size: number): Observable<Page<Annonce>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Annonce>>(`${this.api}/moderation/annonces`, { params });
  }

  publier(id: string): Observable<Annonce> {
    return this.http.patch<Annonce>(`${this.api}/moderation/annonces/${id}/publier`, null);
  }

  rejeter(id: string, motif: string): Observable<Annonce> {
    return this.http.patch<Annonce>(`${this.api}/moderation/annonces/${id}/rejeter`, { motif });
  }
}
