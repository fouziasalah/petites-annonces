# PetitesAnnonces

[![CI](https://github.com/fouziasalah/petites-annonces/actions/workflows/ci.yml/badge.svg)](https://github.com/fouziasalah/petites-annonces/actions/workflows/ci.yml)

Application de gestion d'annonces dont **toute la sécurité d'accès est centralisée sur une API Gateway**.

- **Keycloak authentifie** : émet les tokens (flux Authorization Code + PKCE)
- **Kong contrôle** : valide le JWT, applique le rate limit, propage l'identité en headers
- **Le backend autorise** : `@PreAuthorize`, sans jamais lire de JWT

> Documentation : [compréhension](docs/00-comprehension.md) · [flux d'authentification](docs/02-flux-authentification.md) · [pipeline CI/CD](docs/03-pipeline-ci-cd.md) · [plan](docs/01-plan.md) ·
> [critères (48/48)](docs/criteres.md) · [déploiement VM et CI/CD](docs/deploiement.md)

## Démarrage en une commande

Prérequis : Docker et Docker Compose v2.

```bash
cp .env.example .env
docker compose up -d --build --wait     # ≈ 2 min, 5 services « healthy »
```

| URL | Service |
|---|---|
| http://localhost:4200 | Application |
| http://localhost:8080/api | API (via Kong uniquement) |
| http://localhost:8180/admin | Console d'administration Keycloak (`kcadmin` / voir `.env`) |

Backend (8081) et PostgreSQL (5432) ne sont **pas** publiés ; l'Admin API de Kong (8001) non plus.

## Comptes de test (realm `annonces`)

| Utilisateur | Mot de passe (dev) | Rôles effectifs |
|---|---|---|
| `alice`, `bob` | `alice-dev`, `bob-dev` | USER |
| `modo` | `modo-dev` | MODERATEUR, USER |
| `admin` | `admin-dev` | ADMIN, MODERATEUR, USER |

Rôles composites : `ADMIN` ⊃ `MODERATEUR` ⊃ `USER`.

## Architecture

```
Navigateur ──(4200)──> frontend (Nginx + Angular)
     ├──(8180)──> keycloak ─────────────────────────────┐
     └──(8080)──> gateway Kong ──> backend:8081 ──> postgres:5432
                  cors → jwt → identity-headers → rate-limiting
                                     réseau « internal » (internal: true)
```

| Composant | Technologie |
|---|---|
| Front | Angular 22, angular-oauth2-oidc, Nginx non-root |
| Gateway | Kong 3.9.4 OSS, DB-less, plugin Lua `identity-headers` |
| IdP | Keycloak 26.8.1 |
| Backend | Java 21, Spring Boot 3.5, Spring Security, JPA, Flyway |
| Base | PostgreSQL 16 |

## Choix techniques justifiés

| Choix | Pourquoi |
|---|---|
| `angular-oauth2-oidc` plutôt que `keycloak-angular` | OIDC standard (pas lié à Keycloak), paramètres du flux explicites, tokens en sessionStorage par défaut, refresh par refresh token |
| Une seule source de vérité : `.env` | La même clé RSA alimente Keycloak (privée) et Kong (publique) ; URLs identiques partout |
| `kong.yml` rendu au démarrage (`kong/render-config.sh`) | Même configuration en local et sur la VM, seules les variables changent |
| Origine CORS en regex ancrée | Avec une origine littérale unique, Kong renvoie toujours `Access-Control-Allow-Origin` ; en regex il compare réellement l'`Origin` (GW-09) |
| `uri_param_names: []` sur le plugin jwt | Sinon `?jwt=<token>` est accepté et le token atteint le backend dans l'URL |
| Purge de `X-User-Id` en plus de `X_USER_ID` | Kong active `underscores_in_headers` et nginx confond `-`/`_` pour le rate limiting : sans ça, contournement du quota |
| `@PostAuthorize` + `@HandleAuthorizationDenied` | 404 (et non 403) sur une annonce non visible, sans `if` sur les rôles |
| Client Keycloak sans *full scope* | Le token ne contient que les rôles utiles à l'application |
| Ports publiés sur `127.0.0.1` | Sur une VM, Docker contourne ufw |

## Clé RSA du realm

Le plugin `jwt` de Kong OSS ne lit pas le JWKS : la clé publique doit lui être fournie, donc la
paire doit être **fixe**.

- Développement : paire de test dans `.env.example` (publique, à ne jamais utiliser ailleurs).
- Nouvelle paire : `scripts/gen-keys.sh [.env]` → `REALM_RSA_PRIVATE_KEY`, `REALM_RSA_CERTIFICATE`
  (Keycloak) et `REALM_RSA_PUBLIC_KEY` (Kong).
- **Extraire la clé publique** publiée par Keycloak au format PEM et vérifier qu'elle est celle
  de Kong : `scripts/extract-public-key.sh`
  (manuellement : `GET http://localhost:8180/realms/annonces` → champ `public_key`, à encadrer par
  `-----BEGIN PUBLIC KEY-----` / `-----END PUBLIC KEY-----` en lignes de 64 caractères).

## Tests

| Commande | Couvre |
|---|---|
| `cd backend && ./mvnw verify` | 29 tests : filtre, BK-01..05, MT-01..10 (Testcontainers) |
| `cd frontend && npx ng test --watch=false` | Intercepteurs (Bearer, 401/403/429), guard, JWT |
| `scripts/test-gateway.sh` | 22 vérifications GW et RL sur la stack démarrée |
| `scripts/get-token.sh alice \| scripts/decode-jwt.sh` | Token réel par émulation du flux PKCE |

## CI/CD et déploiement

- `ci.yml` : tests backend, front, validation Kong + luacheck, Compose, e2e gateway.
- `cd.yml` : images sur Docker Hub (`fouziasalahhhh/petitesannonces-*`), déploiement SSH sur la VM
  avec Caddy en HTTPS (`https://app|api|auth.72-60-188-97.sslip.io`), smoke tests, rollback par tag.

Procédure complète (préparation VM, secrets GitHub) : [docs/deploiement.md](docs/deploiement.md).

## Pièges connus

1. Kong OSS ne lit pas le JWKS → paire RSA fixe.
2. `iss` = URL vue par le navigateur → `KC_HOSTNAME` et `key` du credential Kong identiques au caractère près.
3. Le plugin `openid-connect` de Kong est Enterprise → interdit ici.
4. Kong accepte les headers avec `_` (`underscores_in_headers on`) → la purge par le plugin est indispensable.
5. CORS : `Content-Type` autorisé, `Retry-After` et `Location` exposés.
6. Keycloak 25+ : `sub` est porté par le scope `basic`.
7. HTTP refusé par Keycloak hors localhost/IP privée → HTTPS obligatoire sur la VM.
8. Ordre des plugins : cors (2000) → jwt (1450) → identity-headers (1000) → rate-limiting (910).
9. Realm et `init.sql` ne s'appliquent qu'au premier démarrage (volume vide).
10. Git sous Windows perd le bit exécutable des scripts → `git update-index --chmod=+x`.
