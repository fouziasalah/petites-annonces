-- =============================================================================
-- Migration Flyway V1 : création des tables.
--
-- Exécutée automatiquement par Flyway au démarrage de l'application, UNE SEULE FOIS :
-- Flyway note les scripts appliqués dans la table flyway_schema_history.
-- Règle : ne jamais modifier ce fichier une fois appliqué ; créer plutôt V3__….sql.
-- Les entités Java correspondantes sont Categorie.java et Annonce.java.
-- =============================================================================

-- Table des catégories (entité Categorie).
CREATE TABLE categorie (
    -- Clé primaire auto-incrémentée par PostgreSQL (GenerationType.IDENTITY côté Java).
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    libelle VARCHAR(50) NOT NULL
);

-- Un libellé ne peut exister qu'une fois, sans tenir compte des majuscules
-- (« Emploi » et « emploi » sont considérés comme identiques).
CREATE UNIQUE INDEX uk_categorie_libelle ON categorie (lower(libelle));

-- Table des annonces (entité Annonce).
CREATE TABLE annonce (
    -- Identifiant UUID généré par le code Java (UUID.randomUUID()).
    id                UUID PRIMARY KEY,
    titre             VARCHAR(100)   NOT NULL,
    description       VARCHAR(2000)  NOT NULL,
    -- Montant : 12 chiffres dont 2 après la virgule ; jamais négatif.
    prix              NUMERIC(12, 2) NOT NULL CHECK (prix >= 0),
    -- Clé étrangère vers categorie : impossible de supprimer une catégorie encore utilisée.
    categorie_id      BIGINT         NOT NULL REFERENCES categorie (id),
    -- Seules ces trois valeurs sont acceptées (enum StatutAnnonce côté Java).
    statut            VARCHAR(20)    NOT NULL CHECK (statut IN ('EN_ATTENTE', 'PUBLIEE', 'REJETEE')),
    -- Identifiant Keycloak (sub) de l'auteur.
    auteur_id         VARCHAR(64)    NOT NULL,
    motif_rejet       VARCHAR(500),
    -- TIMESTAMPTZ : date et heure avec fuseau horaire (Instant côté Java).
    date_creation     TIMESTAMPTZ    NOT NULL,
    date_modification TIMESTAMPTZ    NOT NULL,
    -- Numéro de version pour le verrouillage optimiste (@Version côté Java).
    version           BIGINT         NOT NULL DEFAULT 0
);

-- Index : accélèrent les recherches les plus fréquentes.
-- Annonces par statut triées par date (liste publique, file de modération).
CREATE INDEX idx_annonce_statut_date ON annonce (statut, date_creation);
-- Annonces d'un auteur (« Mes annonces »).
CREATE INDEX idx_annonce_auteur ON annonce (auteur_id);
-- Annonces d'une catégorie (filtre, vérification avant suppression d'une catégorie).
CREATE INDEX idx_annonce_categorie ON annonce (categorie_id);
