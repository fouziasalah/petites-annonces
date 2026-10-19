-- =============================================================================
-- Initialisation de PostgreSQL : une base et un utilisateur dédié par service.
--
-- Quand ce script s'exécute-t-il ?
--   L'image officielle postgres exécute les fichiers de /docker-entrypoint-initdb.d/
--   UNIQUEMENT au premier démarrage, quand le volume de données est vide. Modifier ce
--   fichier ensuite n'a aucun effet tant que le volume existe
--   (pour repartir de zéro : docker compose down -v, ce qui EFFACE les données).
--
-- Pourquoi deux bases et deux utilisateurs ?
--   Moindre privilège : Keycloak ne peut pas lire les annonces, et le backend ne peut
--   pas lire les comptes Keycloak (mots de passe hashés, clés).
--
-- D'où viennent les mots de passe ?
--   Des variables d'environnement du conteneur postgres (voir docker-compose.yml).
--   Un fichier .sql ne sait pas lire l'environnement, mais psql (l'outil qui exécute ce
--   script) le peut avec la commande \getenv. Aucun mot de passe n'est donc écrit ici.
-- =============================================================================

-- Arrête le script à la première erreur (sinon psql continue malgré l'échec).
\set ON_ERROR_STOP on

-- \getenv <variable psql> <variable d'environnement> : copie la valeur dans une variable psql.
\getenv keycloak_db_password KEYCLOAK_DB_PASSWORD
\getenv annonces_db_password ANNONCES_DB_PASSWORD

-- Création des utilisateurs (« rôles » dans PostgreSQL) autorisés à se connecter (LOGIN).
-- :'variable' insère la valeur correctement entourée d'apostrophes et échappée.
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_db_password';
CREATE ROLE annonces LOGIN PASSWORD :'annonces_db_password';

-- Création des bases, chacune appartenant à son utilisateur. Le propriétaire d'une base
-- possède aussi son schéma public : il peut y créer ses tables (Keycloak crée les siennes
-- seul au démarrage ; pour le backend, c'est Flyway).
CREATE DATABASE keycloak OWNER keycloak ENCODING 'UTF8';
CREATE DATABASE annonces OWNER annonces ENCODING 'UTF8';

-- Personne d'autre que le propriétaire ne peut se connecter à ces bases.
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
REVOKE ALL ON DATABASE annonces FROM PUBLIC;
