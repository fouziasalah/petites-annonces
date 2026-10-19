-- =============================================================================
-- Migration Flyway V2 : les 4 catégories disponibles au premier démarrage.
-- Exécutée une seule fois, juste après V1. L'id de chaque ligne est attribué par la base
-- (Immobilier = 1, Véhicules = 2, Emploi = 3, Multimédia = 4).
-- =============================================================================
INSERT INTO categorie (libelle) VALUES
    ('Immobilier'),
    ('Véhicules'),
    ('Emploi'),
    ('Multimédia');
