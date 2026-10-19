package fr.petitesannonces.entity;

/**
 * Statut d'une annonce dans son cycle de vie.
 *
 * <pre>
 *   EN_ATTENTE ──publier()──► PUBLIEE
 *        └──────rejeter()───► REJETEE
 *   (toute modification par l'auteur ramène l'annonce à EN_ATTENTE)
 * </pre>
 *
 * <p>Stocké en base sous forme de texte (voir {@code @Enumerated(EnumType.STRING)} dans {@link Annonce}).
 */
// enum (Java) : type qui ne peut prendre qu'une des valeurs listées.
public enum StatutAnnonce {
    // Créée ou modifiée, en attente de décision d'un modérateur ; visible par l'auteur et les modérateurs.
    EN_ATTENTE,
    // Validée par un modérateur : visible par tous les utilisateurs.
    PUBLIEE,
    // Refusée par un modérateur, avec un motif.
    REJETEE
}
