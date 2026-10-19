-- =============================================================================
-- Initialisation de PostgreSQL : une base et un utilisateur dédié par service.
--
-- POURQUOI deux bases et deux utilisateurs ?
--   Principe du moindre privilège : Keycloak ne peut pas lire les annonces et le
--   backend ne peut pas lire les comptes Keycloak (hashs de mots de passe, clés).
--
-- QUAND ce script s'exécute-t-il ?
--   L'image officielle exécute /docker-entrypoint-initdb.d/* UNIQUEMENT si le
--   volume de données est vide (premier démarrage). Changer ce fichier ensuite
--   n'a aucun effet tant que le volume existe (docker compose down -v pour repartir de zéro).
--
-- POURQUOI \getenv ?
--   Un fichier .sql ne sait pas lire les variables d'environnement, mais psql (≥ 15)
--   le peut via \getenv. Les mots de passe restent donc dans .env, pas dans Git.
-- =============================================================================

\set ON_ERROR_STOP on

\getenv keycloak_db_password KEYCLOAK_DB_PASSWORD
\getenv annonces_db_password ANNONCES_DB_PASSWORD

-- :'variable' insère la valeur correctement échappée comme littéral SQL.
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_db_password';
CREATE ROLE annonces LOGIN PASSWORD :'annonces_db_password';

-- Le propriétaire de la base possède aussi le schéma public (PostgreSQL ≥ 15),
-- il peut donc y créer ses tables (Liquibase pour Keycloak, Flyway pour le backend).
CREATE DATABASE keycloak OWNER keycloak ENCODING 'UTF8';
CREATE DATABASE annonces OWNER annonces ENCODING 'UTF8';

-- Personne d'autre que le propriétaire ne peut se connecter à ces bases.
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
REVOKE ALL ON DATABASE annonces FROM PUBLIC;
