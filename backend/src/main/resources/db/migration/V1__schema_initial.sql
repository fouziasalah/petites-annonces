-- Schéma initial de la base « annonces » (géré exclusivement par Flyway).

CREATE TABLE categorie (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    libelle VARCHAR(50) NOT NULL
);

-- Unicité insensible à la casse : « Emploi » et « emploi » sont la même catégorie.
CREATE UNIQUE INDEX uk_categorie_libelle ON categorie (lower(libelle));

CREATE TABLE annonce (
    id                UUID PRIMARY KEY,
    titre             VARCHAR(100)   NOT NULL,
    description       VARCHAR(2000)  NOT NULL,
    prix              NUMERIC(12, 2) NOT NULL CHECK (prix >= 0),
    -- ON DELETE RESTRICT (défaut) : une catégorie utilisée ne peut pas disparaître (→ 409).
    categorie_id      BIGINT         NOT NULL REFERENCES categorie (id),
    statut            VARCHAR(20)    NOT NULL CHECK (statut IN ('EN_ATTENTE', 'PUBLIEE', 'REJETEE')),
    -- « sub » Keycloak de l'auteur, reçu de Kong dans X_USER_ID (jamais du client).
    auteur_id         VARCHAR(64)    NOT NULL,
    motif_rejet       VARCHAR(500),
    date_creation     TIMESTAMPTZ    NOT NULL,
    date_modification TIMESTAMPTZ    NOT NULL,
    -- Verrouillage optimiste : deux modifications concurrentes ne s'écrasent pas.
    version           BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_annonce_statut_date ON annonce (statut, date_creation);
CREATE INDEX idx_annonce_auteur ON annonce (auteur_id);
CREATE INDEX idx_annonce_categorie ON annonce (categorie_id);
