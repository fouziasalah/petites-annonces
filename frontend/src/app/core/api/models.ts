/** Contrats JSON de l'API (miroir des DTO du backend). */

export type StatutAnnonce = 'EN_ATTENTE' | 'PUBLIEE' | 'REJETEE';

export interface Annonce {
  id: string;
  titre: string;
  description: string;
  prix: number;
  categorieId: number;
  statut: StatutAnnonce;
  auteurId: string;
  motifRejet?: string;
  dateCreation: string;
  dateModification: string;
}

/** Pas d'auteurId : c'est le backend qui le déduit de l'identité (X_USER_ID). */
export interface AnnonceRequest {
  titre: string;
  description: string;
  prix: number;
  categorieId: number;
}

export interface Categorie {
  id: number;
  libelle: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Erreur RFC 9457 renvoyée par le backend. */
export interface ProblemDetail {
  status: number;
  detail?: string;
  errors?: { champ: string; message: string }[];
}
