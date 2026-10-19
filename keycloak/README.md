# Realm Keycloak `annonces`

Le fichier JSON ne peut pas contenir de commentaires : voici l'explication de `realm-annonces.json`.

## Quand est-il utilisé ?

Keycloak démarre avec `start-dev --import-realm` (voir `docker-compose.yml`) : il importe les
fichiers de `/opt/keycloak/data/import/`, **uniquement si le realm n'existe pas encore** dans sa
base. Modifier ce fichier n'a donc d'effet qu'après suppression du realm (console → Realm settings →
Action → Delete) ou de la base (`docker compose down -v`, qui efface aussi les annonces).

## Contenu (cahier des charges § 5.1 et § 6.4)

| Élément | Valeur | Rôle |
|---|---|---|
| `accessTokenLifespan` | `300` | access token valable 5 minutes |
| `sslRequired` | `external` | HTTP accepté depuis localhost / réseau privé, HTTPS exigé sinon |
| rôles | `USER`, `MODERATEUR` (contient USER), `ADMIN` (contient MODERATEUR) | rôles **composites** : un admin reçoit les 3 dans son token |
| client `annonces-front` | public, Standard flow seul, PKCE `S256` | flux Authorization Code + PKCE ; implicit et mot de passe direct interdits |
| `redirectUris` / `webOrigins` / `post.logout.redirect.uris` | `http://localhost:4200…` | retour après login, CORS de l'échange du code, retour après déconnexion |
| utilisateurs | `alice`, `bob` (USER), `modo` (MODERATEUR), `admin` (ADMIN) | email, prénom et nom renseignés : sinon Keycloak demande de compléter le profil |
| `rsa-annonces-fixe` | priorité 200, RS256 | **paire de clés fixe** qui signe les tokens (voir ci-dessous) |
| `hmac-generated`, `aes-generated` | générées par Keycloak | clés internes de Keycloak (jamais vues par Kong) |

## Pourquoi une paire de clés fixe ?

Le plugin `jwt` de Kong OSS ne sait pas lire les clés publiées par Keycloak (JWKS) : la clé
**publique** est recopiée dans `kong/kong.yml`. Si Keycloak générait lui-même sa clé, elle
changerait à chaque nouveau realm et Kong rejetterait tous les tokens (`401 Invalid signature`).
Avec une clé fournie dans ce fichier, elle reste la même après chaque recréation.

## Les secrets ne sont pas dans ce fichier

Les valeurs `${…}` sont remplacées par Keycloak, au moment de l'import, par les **variables
d'environnement** de son conteneur (fournies par `docker-compose.yml`, lues dans `.env`) :

| Placeholder | Contenu |
|---|---|
| `${REALM_PWD_ALICE}` … `${REALM_PWD_ADMIN}` | mots de passe des utilisateurs de test |
| `${REALM_RSA_PRIVATE_KEY}` | clé privée RSA (base64 sur une ligne, sans en-têtes PEM) |
| `${REALM_RSA_CERTIFICATE}` | certificat associé (base64 sur une ligne) |

`scripts/gen-keys.sh` génère une nouvelle paire, l'écrit dans `.env` et recopie la clé publique
dans `kong/kong.yml`.
