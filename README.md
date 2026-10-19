# PetitesAnnonces

Application de gestion d'annonces dont **toute la sécurité d'accès est centralisée sur une API Gateway**.

- **Keycloak authentifie** (émet les tokens, flux Authorization Code + PKCE)
- **Kong contrôle** (valide le JWT, applique le rate limit, propage l'identité en headers)
- **Le backend autorise** (`@PreAuthorize`, sans jamais lire de JWT)

> Comprendre le projet : [`docs/00-comprehension.md`](docs/00-comprehension.md) ·
> Plan : [`docs/01-plan.md`](docs/01-plan.md) ·
> Critères : [`docs/criteres.md`](docs/criteres.md)

## Démarrage rapide

Prérequis : Docker et Docker Compose v2.

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps        # tous les services doivent être "healthy"
```

| URL | Service |
|---|---|
| http://localhost:4200 | Application (front) |
| http://localhost:8080/api | API (via Kong) |
| http://localhost:8180 | Keycloak — console d'admin : http://localhost:8180/admin |

## Comptes de test (realm `annonces`)

Les mots de passe sont définis dans `.env` (valeurs de dev dans `.env.example`).

| Utilisateur | Rôle(s) effectifs |
|---|---|
| `alice`, `bob` | USER |
| `modo` | MODERATEUR, USER |
| `admin` | ADMIN, MODERATEUR, USER |

Le compte d'administration de Keycloak (realm `master`) est défini par
`KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`.

## Architecture

```
Navigateur ──(4200)──> frontend (Nginx/Angular)
     ├──(8180)──> keycloak ──────────────┐ internal
     └──(8080)──> gateway (Kong) ────────┼──> backend:8081 ──> postgres:5432
                                         └── keycloak ──> postgres
```

## Sections à venir
- Clé RSA du realm et extraction de la clé publique
- Tests
- CI/CD et déploiement sur VM
