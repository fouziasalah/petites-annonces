# Critères d'acceptation — suivi

Légende : ⬜ à faire · 🟡 en cours · ✅ démontré

## Déploiement (DEP)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| DEP-01 | `.env` copié de `.env.example`, `up -d --build` → 5 conteneurs `healthy` < 3 min | `docker compose ps` | ⬜ |
| DEP-02 | 8081 et 5432 injoignables depuis l'hôte | `curl localhost:8081`, `nc -z localhost 5432` | ✅ |
| DEP-03 | Realm `annonces` importé (clients, rôles, users) | console `http://localhost:8180/admin` | ✅ |
| DEP-04 | Données et config Keycloak conservées après `down`/`up` | créer une annonce, `down`, `up`, relire | ⬜ |
| DEP-05 | Aucun secret commité | `git grep`, revue de `.env.example` | ⬜ |

## Keycloak et PKCE (KC)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| KC-01 | Non connecté → login du realm | navigateur | ✅ |
| KC-02 | URL d'autorisation avec `code_challenge` S256 | DevTools réseau | ✅ |
| KC-03 | Échange du code avec `code_verifier`, aucun token dans l'URL | DevTools réseau | ✅ |
| KC-04 | `grant_type=password` refusé | `curl` → `unauthorized_client` | ✅ |
| KC-05 | Token de `modo` : `MODERATEUR` et `USER` | `scripts/get-token.sh modo` | ✅ |
| KC-06 | Déconnexion ferme la session SSO | navigateur | ✅ |

## Frontend (FR)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| FR-01 | Bearer uniquement vers la gateway | DevTools réseau | ✅ |
| FR-02 | Aucun token en localStorage | DevTools Application | ✅ |
| FR-03 | `alice` ne voit pas Modération/Catégories, `/moderation` redirige | navigateur | ✅ |
| FR-04 | Token renouvelé après 5 min sans login | navigateur (attendre) | ⬜ |
| FR-05 | 429 → message avec `Retry-After` | navigateur | ✅ |
| FR-06 | 403 → « accès refusé » sans déconnexion | navigateur | ✅ |

## Gateway (GW)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| GW-01 | Sans `Authorization` → 401, backend non atteint | `test-gateway.sh` + logs | ✅ |
| GW-02 | Signature altérée → 401 | `test-gateway.sh` | ✅ |
| GW-03 | Token expiré → 401 | `test-gateway.sh` | ✅ |
| GW-04 | Autre émetteur → 401 | `test-gateway.sh` | ✅ |
| GW-05 | `alice` sur `/api/me` → son `sub`, `["USER"]` | `test-gateway.sh` | ✅ |
| GW-06 | `X_USER_ID` forgé → `sub` d'alice | `test-gateway.sh` | ✅ |
| GW-07 | `X_ROLE_ID: ADMIN` forgé → 403 | `test-gateway.sh` | ✅ |
| GW-08 | Backend : `X_*` présents, pas d'`Authorization` | logs backend | ✅ |
| GW-09 | Preflight `http://evil.local` → pas d'ACAO | `test-gateway.sh` | ✅ |
| GW-10 | 8001 injoignable | `curl localhost:8001` | ✅ |
| GW-11 | Config DB-less dans `kong/kong.yml`, plugins OSS | revue | ✅ |

## Rate limiting (RL)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| RL-01 | 3 appels < 1 min OK | `test-gateway.sh` | ✅ |
| RL-02 | 4ᵉ → 429 + `Retry-After` | `test-gateway.sh` | ✅ |
| RL-03 | Après le délai → OK | `test-gateway.sh` | ✅ |
| RL-04 | Compteur par utilisateur | `test-gateway.sh` | ✅ |
| RL-05 | Preflight non décompté | `test-gateway.sh` | ✅ |

## Backend (BK)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| BK-01 | Aucune dépendance OAuth2/JWT/Keycloak | `pom.xml` | ✅ |
| BK-02 | Sans `X_USER_ID` → 401 | test d'intégration | ✅ |
| BK-03 | Principal et autorités reconstruits | test d'intégration | ✅ |
| BK-04 | Tout via `@PreAuthorize` | revue | ✅ |
| BK-05 | Pas de fuite de contexte, pas de session | test d'intégration | ✅ |

## Règles métier (MT)
| ID | Critère | Démonstration | Statut |
|---|---|---|---|
| MT-01 | Création → 201, `EN_ATTENTE`, `auteurId` = `sub` | test d'intégration | ✅ |
| MT-02 | `bob` sur annonce en attente d'alice → 404 | test d'intégration | ✅ |
| MT-03 | `bob` PUT/DELETE annonce d'alice → 403 | test d'intégration | ✅ |
| MT-04 | Modification → retour `EN_ATTENTE` | test d'intégration | ✅ |
| MT-05 | USER sur `/publier` → 403 | test d'intégration | ✅ |
| MT-06 | `modo` publie → visible | test d'intégration | ✅ |
| MT-07 | Rejet sans motif → 400 | test d'intégration | ✅ |
| MT-08 | `modo` POST catégorie → 403, `admin` → 201 | test d'intégration | ✅ |
| MT-09 | Catégorie utilisée → 409 | test d'intégration | ✅ |
| MT-10 | Titre court / prix négatif → 400 avec le champ | test d'intégration | ✅ |
