package fr.petitesannonces.entity;

/** Cycle de vie : EN_ATTENTE → PUBLIEE ou REJETEE ; toute modification par l'auteur → EN_ATTENTE. */
public enum StatutAnnonce {
    EN_ATTENTE,
    PUBLIEE,
    REJETEE
}
