# PROMPT — Projet « PetitesAnnonces »
### Keycloak · Kong OSS (DB-less + plugin Lua) · Spring Security · Angular · Docker Compose · CI/CD GitHub Actions → Docker Hub → VM

---

## 0. Ton rôle et ta façon de travailler

Tu es un architecte et développeur senior full stack (Angular 17+ / Spring Boot 3 / Java 21), expert en sécurité OAuth2/OIDC, Keycloak, Kong et DevOps (Docker, GitHub Actions).
Je suis développeur full stack Angular / Spring Boot. Je veux **livrer** ce projet et surtout **comprendre** chaque brique, notamment le flux d'authentification de bout en bout.

**Règles de travail (obligatoires) :**

1. **Avant tout code**, lis l'intégralité de ce prompt puis produis :
   - `docs/00-comprehension.md` : ta reformulation de l'exercice, le rôle de chaque composant, le flux d'authentification détaillé (avec diagramme de séquence Mermaid), les pièges identifiés.
   - `docs/01-plan.md` : le découpage en phases, l'ordre de réalisation, et pour chaque phase les critères d'acceptation (IDs de la section 9) couverts.
   - **Attends ma validation** avant de passer au code.
2. Travaille **phase par phase** (voir section 13). À la fin de chaque phase, donne :
   - la liste des fichiers créés ou modifiés ;
   - les commandes exactes pour vérifier (curl, `docker compose`, tests) ;
   - les critères d'acceptation désormais satisfaits ;
   - un message de commit au format Conventional Commits (`feat(gateway): ...`). **Pas de commit unique final**, des commits réguliers et explicites.
3. **Explique pendant que tu codes** : pour chaque fichier de config sécurité (realm Keycloak, `kong.yml`, plugin Lua, filtre Spring), ajoute des commentaires pédagogiques expliquant le *pourquoi*.
4. **N'invente rien** : si tu as un doute sur une option de configuration (version de Kong, de Keycloak, nom d'un champ), dis-le explicitement et propose une commande ou un test pour vérifier.
5. Respecte strictement les **interdits** de la section 2.
6. Aucun secret en clair dans le dépôt : tout passe par `.env` (ignoré par Git), documenté dans `.env.example` avec des valeurs factices.

---

## 1. Contexte et objectifs

Livrer **« PetitesAnnonces »**, une application web de gestion d'annonces dont **toute la sécurité d'accès est centralisée sur une API Gateway**. Tout démarre avec une seule commande : `docker compose up`.

Le fonctionnel est volontairement minimal. L'enjeu est technique : comprendre comment un jeton émis par un Identity Provider traverse une architecture distribuée, et comment chaque couche joue son rôle.

**Compétences visées :**
- configurer Keycloak comme Identity Provider (realm, clients, rôles, utilisateurs) ;
- implémenter le flux OAuth2 / OIDC **Authorization Code + PKCE** dans une SPA Angular ;
- valider un JWT sur une API Gateway **Kong** (signature, émetteur, expiration), l'étendre avec un **plugin Lua maison** et propager l'identité via des headers ;
- appliquer un **rate limiting de 3 appels/minute** par utilisateur ;
- reconstruire un contexte **Spring Security** à partir de headers et protéger les endpoints avec `@PreAuthorize` ;
- orchestrer 5 services avec Docker Compose et isoler le réseau interne ;
- **(mon ajout)** industrialiser : CI/CD GitHub Actions, images publiées sur Docker Hub, déploiement sur une VM via Docker Compose.

**Stack imposée :** Java 21, Spring Boot 3, Angular 17+, Kong 3.x OSS, Keycloak 25+, PostgreSQL 16, Docker / Docker Compose, Lua (plugin ~30 lignes).

**Principe directeur : chaque composant a une responsabilité unique.**
- **Keycloak authentifie** (émet les tokens).
- **La gateway contrôle** (valide le token, applique le rate limit, propage l'identité).
- **Le backend autorise** (règles métier et rôles via `@PreAuthorize`).

---

## 2. Contraintes non négociables (interdits)

- ❌ Le plugin Kong `openid-connect` (réservé à l'édition Enterprise). **Uniquement des plugins OSS.**
- ❌ Base de données pour Kong : mode **DB-less** obligatoire, config déclarative dans `kong/kong.yml`.
- ❌ Publier l'Admin API de Kong (8001) sur l'hôte.
- ❌ Dans le backend : toute dépendance `spring-boot-starter-oauth2-*`, `jjwt`, `nimbus`, Keycloak. Le backend **ne lit jamais de JWT** et **n'appelle jamais Keycloak**.
- ❌ Le token ne dépasse jamais la gateway : le backend ne reçoit **jamais** le header `Authorization`.
- ❌ Tokens en `localStorage` côté front (mémoire ou `sessionStorage` uniquement).
- ❌ Règles d'autorisation exprimées par des `if` sur les rôles dans le code métier : tout passe par `@PreAuthorize`.
- ❌ Une règle d'autorisation front qui remplace une règle serveur (le masquage des menus n'est qu'un confort).
- ❌ Backend et PostgreSQL exposés sur l'hôte.
- ❌ Flux implicit ou `password` (direct access grants) sur le client `annonces-front`.
- ❌ Secrets commités (hors `.env.example` avec valeurs factices).

---

## 3. Architecture cible

### 3.1 Services (5 conteneurs, 2 réseaux)

| Service  | Technologie                                     | Port hôte | Réseaux            |
|----------|-------------------------------------------------|-----------|--------------------|
| frontend | Angular 17+ servi par Nginx                     | 4200      | public             |
| gateway  | Kong 3.x OSS, DB-less (port interne 8000)       | 8080      | public, internal   |
| keycloak | Keycloak 25+                                    | 8180      | public, internal   |
| backend  | Spring Boot 3 (Java 21), port 8081              | aucun     | internal           |
| postgres | PostgreSQL 16                                   | aucun     | internal           |

### 3.2 Schéma

```
Navigateur ──(4200)──> frontend (Nginx/Angular)
     │
     ├──(8180)──> keycloak  ──┐
     │                         │ internal
     └──(8080)──> gateway ─────┼──> backend:8081 ──> postgres:5432
         (Kong: cors, jwt,     │                       (bases: keycloak, annonces)
          identity-headers,    │
          rate-limiting)       └── keycloak ──> postgres
```

Un seul point d'entrée pour l'API : la gateway. En aval de la gateway, l'identité circule **uniquement sous forme de headers** (`X_USER_ID`, `X_ROLE_ID`), sur un réseau inaccessible au client.

---

## 4. Flux complet d'authentification (à expliquer en détail dans `docs/00-comprehension.md`)

Produis un **diagramme de séquence Mermaid** et une explication pas à pas de ces étapes :

### 4.1 Connexion (Authorization Code + PKCE)
1. L'utilisateur ouvre `http://localhost:4200`. Angular constate qu'il n'a pas de token valide.
2. Angular génère un `code_verifier` aléatoire, en dérive `code_challenge = BASE64URL(SHA256(code_verifier))`, conserve le verifier côté client (sessionStorage) et redirige vers l'endpoint `/authorize` de Keycloak avec `response_type=code`, `client_id=annonces-front`, `redirect_uri`, `scope=openid ...`, `state`, `nonce`, `code_challenge`, `code_challenge_method=S256`.
3. L'utilisateur s'authentifie sur la page de login Keycloak du realm `annonces`.
4. Keycloak redirige vers `redirect_uri?code=...&state=...` (aucun token dans l'URL).
5. Angular appelle l'endpoint `/token` (POST) avec `grant_type=authorization_code`, le `code` et le `code_verifier`. Keycloak recalcule le hash, compare au challenge, puis renvoie `access_token` (5 min), `refresh_token`, `id_token`.
6. Explique **pourquoi PKCE** : un client public (SPA) ne peut pas garder de secret ; PKCE empêche qu'un code intercepté soit échangé par un tiers.

### 4.2 Appel d'API
7. Angular appelle la gateway avec `Authorization: Bearer <access_token>` (seulement vers la gateway, via un `HttpInterceptor`).
8. Le navigateur envoie d'abord un **preflight `OPTIONS`** (CORS) : traité par le plugin `cors` de Kong **avant** toute authentification et tout décompte de rate limit.
9. Le plugin `jwt` de Kong :
   - lit le claim `iss` du token et cherche un credential dont la `key` est égale à cette valeur (`key_claim_name: iss`) ;
   - vérifie la signature RS256 avec la `rsa_public_key` (PEM) du credential ;
   - vérifie `exp` (`claims_to_verify: [exp]`) ;
   - sinon : **401**, le backend n'est jamais appelé.
10. Le plugin Lua **`identity-headers`** (priorité entre 1450 et 910) :
    - supprime tout `X_USER_ID` / `X_ROLE_ID` envoyé par le client (anti-usurpation) ;
    - lit le token déjà validé, ajoute `X_USER_ID = sub` et `X_ROLE_ID = realm_access.roles` filtrés sur `USER`, `MODERATEUR`, `ADMIN`, séparés par des virgules ;
    - supprime `Authorization`.
11. Le plugin `rate-limiting` compte par valeur de `X_USER_ID` : au-delà de 3 appels/minute → **429** + `Retry-After`, backend non appelé.
12. Kong relaie vers `http://backend:8081/api/...`.
13. Côté backend, un `OncePerRequestFilter` lit les headers, construit un `Authentication` (principal = userId, autorités `ROLE_<role>`), le place dans le `SecurityContextHolder` ; headers absents → **401**.
14. `@PreAuthorize` autorise ou refuse (**403**), puis le service accède à PostgreSQL. Le contexte est vidé en fin de requête (stateless, aucune session).

### 4.3 Cycle de vie de la session
15. **Renouvellement** : avant expiration de l'access token (5 min), Angular utilise le refresh token sur `/token` (`grant_type=refresh_token`) sans retour à la page de login.
16. **Déconnexion** : appel de l'endpoint `end_session` de Keycloak (`id_token_hint`, `post_logout_redirect_uri`) → la session SSO est fermée, un retour sur l'app redemande le login.
17. **Gestion des erreurs côté front** : 401 → retour au login ; 403 → message « accès refusé » sans déconnexion ; 429 → message avec le délai lu dans `Retry-After`.

### 4.4 Cas d'attaque à expliquer
- Token absent, signature altérée, token expiré, token d'un autre realm/Keycloak → 401 à la gateway.
- Header `X_USER_ID` forgé → écrasé par le plugin (et Nginx ignore déjà par défaut les headers avec `_`, mais on ne s'appuie pas dessus).
- `X_ROLE_ID: ADMIN` forgé → ignoré, les rôles viennent du token → 403 sur une action ADMIN.
- Appel direct au backend → impossible, il n'est pas exposé.

---

## 5. Périmètre fonctionnel

Un utilisateur publie des annonces, un modérateur les valide avant mise en ligne, un administrateur gère les catégories. **Tout endpoint exige d'être authentifié.**

### 5.1 Rôles (hiérarchie portée par Keycloak via rôles composites)

| Rôle        | Peut faire |
|-------------|-----------|
| USER        | Consulter les annonces publiées, créer une annonce, modifier/supprimer **ses** annonces, lister ses annonces |
| MODERATEUR  | Tout USER en lecture + lister les annonces en attente, publier/rejeter, supprimer n'importe quelle annonce |
| ADMIN       | Tout MODERATEUR + créer et supprimer des catégories |

`MODERATEUR` est composite de `USER` ; `ADMIN` est composite de `MODERATEUR` (donc aussi de `USER`).

### 5.2 Modèle de données

**Annonce** : `id` (UUID), `titre`, `description`, `prix`, `categorieId`, `statut`, `auteurId`, `dateCreation`, `dateModification`
- titre 5 à 100 caractères ; description ≤ 2000 caractères ; prix ≥ 0 €
- `auteurId` = valeur de `X_USER_ID`, **jamais fournie par le client** (ignorer toute valeur du corps)

**Categorie** : `id`, `libelle`
- libellé unique, 2 à 50 caractères
- 4 catégories initiales (migration Flyway) : Immobilier, Véhicules, Emploi, Multimédia

**Cycle de vie** : `EN_ATTENTE` → `PUBLIEE` ou `REJETEE`. Toute modification par l'auteur repasse l'annonce en `EN_ATTENTE`.

### 5.3 Endpoints (exposés par la gateway sous `/api`)

| Méthode | Chemin | Rôle requis | Comportement |
|---------|--------|-------------|--------------|
| GET | `/api/me` | Authentifié | Renvoie `userId` et `roles` lus dans le contexte Spring Security |
| GET | `/api/annonces` | USER | Annonces `PUBLIEE`, paginées (`page`, `size` ≤ 50), filtre `categorieId` optionnel |
| GET | `/api/annonces/{id}` | USER | Annonce publiée ; ou non publiée si auteur ou MODERATEUR ; sinon **404** |
| GET | `/api/annonces/mes-annonces` | USER | Annonces de l'utilisateur courant, tous statuts |
| POST | `/api/annonces` | USER | Crée en `EN_ATTENTE`, renvoie **201 + `Location`** |
| PUT | `/api/annonces/{id}` | USER + auteur | Modifie, statut remis à `EN_ATTENTE` |
| DELETE | `/api/annonces/{id}` | Auteur, MODERATEUR ou ADMIN | Supprime, **204** |
| GET | `/api/moderation/annonces` | MODERATEUR | Annonces `EN_ATTENTE`, plus anciennes d'abord |
| PATCH | `/api/moderation/annonces/{id}/publier` | MODERATEUR | Passe en `PUBLIEE` |
| PATCH | `/api/moderation/annonces/{id}/rejeter` | MODERATEUR | Passe en `REJETEE`, corps `{ "motif": "…" }` obligatoire |
| GET | `/api/categories` | USER | Liste des catégories |
| POST | `/api/categories` | ADMIN | Crée une catégorie |
| DELETE | `/api/categories/{id}` | ADMIN | Supprime une catégorie sans annonce, sinon **409** |

⚠️ Attention à `/api/annonces/mes-annonces` vs `/api/annonces/{id}` : l'ordre/la résolution des mappings doit être correcte (et `{id}` est un UUID).

---

## 6. Travail demandé par composant

### 6.1 Arborescence attendue du dépôt

```
.
├── backend/                         # Spring Boot 3, Dockerfile multi-stage
├── frontend/                        # Angular 17+, Dockerfile multi-stage (npm build → nginx)
├── kong/
│   ├── kong.yml                     # config déclarative DB-less
│   └── plugins/identity-headers/
│       ├── handler.lua
│       └── schema.lua
├── keycloak/realm-annonces.json
├── postgres/init.sql
├── scripts/                         # tests gateway, extraction clé publique, etc.
├── tests/bruno/ (ou postman/)       # collection couvrant GW, RL, MT
├── docs/                            # compréhension, plan, flux, déploiement
├── .github/workflows/               # CI/CD (mon ajout)
├── docker-compose.yml
├── .env.example
├── .gitignore                       # contient .env
└── README.md
```

### 6.2 Docker Compose (`docker-compose.yml`)
- 5 services : `postgres`, `keycloak`, `gateway`, `backend`, `frontend`.
- 2 réseaux : `public` (frontend, gateway, keycloak) et `internal` (gateway, backend, postgres, keycloak). Envisager `internal: true` sur le réseau interne et l'expliquer.
- Ports publiés : `4200` (frontend), `8080:8000` (proxy Kong), `8180` (Keycloak). **Rien d'autre.**
- Chaque service a un `healthcheck` :
  - postgres : `pg_isready` ;
  - keycloak : activer `KC_HEALTH_ENABLED=true` ; l'image Keycloak n'a ni curl ni wget → utiliser un test via `bash` et `/dev/tcp` sur le port de management (9000) `/health/ready` ;
  - gateway : `kong health` ;
  - backend : Spring Boot Actuator `/actuator/health` (accessible sans headers, **non routé par Kong**) ;
  - frontend : wget sur Nginx.
- `depends_on` avec `condition: service_healthy`.
- Secrets et mots de passe dans `.env` (fourni en `.env.example`).
- Images applicatives (backend, frontend) construites par des **Dockerfile multi-stage** (Maven / npm puis image d'exécution légère, utilisateur non-root). Kong utilise l'image officielle **sans build** (plugin monté par volume).
- **Pour la CI/CD** : chaque service applicatif déclare à la fois `build:` et `image: ${DOCKERHUB_USERNAME}/petitesannonces-<service>:${IMAGE_TAG:-latest}` ; en local `docker compose up -d --build` construit, sur la VM on fait `pull` puis `up -d --no-build`.
- Volume nommé pour PostgreSQL.

### 6.3 PostgreSQL
- PostgreSQL 16, deux bases : `keycloak` et `annonces`, créées par `postgres/init.sql` (avec utilisateurs dédiés si pertinent). Rappel : les scripts d'init ne s'exécutent que sur un volume vide.
- Données dans un volume nommé (persistance après `down`/`up`).
- Schéma `annonces` géré par **Flyway** (migrations versionnées + 4 catégories initiales).

### 6.4 Keycloak (Identity Provider)
- Keycloak 25+ en `start-dev`, base PostgreSQL, realm importé au démarrage (`--import-realm`) depuis `keycloak/realm-annonces.json`.
- Realm `annonces` avec rôles de realm `USER`, `MODERATEUR`, `ADMIN` (composites, cf. 5.1).
- Client `annonces-front` :
  - public ;
  - **Standard flow seul** (implicit et direct access grants désactivés) ;
  - PKCE `S256` obligatoire (attribut `pkce.code.challenge.method`) ;
  - redirect URI `http://localhost:4200/*`, web origin `http://localhost:4200` ;
  - post logout redirect URI configurée (nécessaire pour `end_session`).
- Durée de vie de l'access token : **5 minutes** (`accessTokenLifespan: 300`).
- Utilisateurs de test : `alice` et `bob` (USER), `modo` (MODERATEUR), `admin` (ADMIN).
- `KC_HOSTNAME` fixé pour que `iss` = `http://localhost:8180/realms/annonces` (URL vue par le navigateur, pas `keycloak:8080`).
- **Paire de clés RSA fixe** embarquée dans le realm (composant key provider `rsa`), sinon Keycloak en génère une nouvelle et Kong rejette tous les tokens.
- Vérifier que l'access token contient bien `sub` (en Keycloak 25+, `sub` est porté par le client scope `basic` : s'assurer qu'il est présent dans un realm écrit à la main), `realm_access.roles` (scope `roles`) et `azp`.
- **Gestion des secrets du realm** : la clé privée RSA et les mots de passe des utilisateurs de test ne doivent pas être commités en clair. Propose une solution, par exemple : placeholders `${VAR}` dans le JSON (substitution de variables d'environnement à l'import, à vérifier pour la version de Keycloak choisie), valeurs dans `.env`, et un script `scripts/gen-keys.sh` qui génère la paire RSA, remplit `.env` et injecte la clé publique dans `kong/kong.yml`.

### 6.5 API Gateway — Kong OSS DB-less
Image officielle `kong:3.x` (épingle une version précise), `KONG_DATABASE=off`, `KONG_DECLARATIVE_CONFIG` → `kong/kong.yml`.

1. **Service** `annonces-backend` → `http://backend:8081` ; **route** `/api` (`strip_path: false`, car le backend expose ses endpoints sous `/api`).
2. **Plugin `cors`** : origine `http://localhost:4200` uniquement ; méthodes GET, POST, PUT, PATCH, DELETE (+ OPTIONS) ; headers autorisés `Authorization` **et `Content-Type`** (sinon les POST/PUT JSON échouent au preflight) ; headers exposés `Retry-After` **et `Location`**. Les preflight sont traités avant authentification et décompte. Vérifier que les en-têtes CORS sont aussi présents sur les réponses 401/403/429 (sinon le front ne peut pas lire l'erreur).
3. **Plugin `jwt`** sur la route :
   - `key_claim_name: iss`, `claims_to_verify: [exp]`, `run_on_preflight: false` ;
   - **consumer** `keycloak-annonces` avec un credential `jwt_secrets` : `key` = `http://localhost:8180/realms/annonces`, `algorithm: RS256`, `rsa_public_key` = clé publique PEM du realm ;
   - token absent, mal signé, expiré ou d'un autre émetteur → 401.
4. **Plugin maison `identity-headers`** (voir 6.6), chargé via `KONG_PLUGINS=bundled,identity-headers`, monté par volume (configurer `KONG_LUA_PACKAGE_PATH` pour que `kong.plugins.identity-headers.*` soit résolu).
5. **Plugin `rate-limiting`** : `minute: 3`, `policy: local`, `limit_by: header`, `header_name: X_USER_ID` → compteur par utilisateur sur tout `/api/**` ; au-delà : 429 + `Retry-After`.
   - ⚠️ Point à **prouver par un test** : le plugin rate-limiting lit bien le header `X_USER_ID` **injecté par `identity-headers`** (et non une valeur client). Si ce n'est pas le cas, l'expliquer et proposer une correction.
6. Admin API (8001) **non publiée** sur l'hôte.

### 6.6 Plugin Lua `identity-headers` (`handler.lua` + `schema.lua`)
- `PRIORITY` strictement entre 910 et 1450 (ex. 1000), à justifier.
- Phase `access` :
  - `kong.service.request.clear_header` sur `X_USER_ID` et `X_ROLE_ID` (anti-usurpation) ;
  - récupérer le token déjà validé par `jwt` (ex. `kong.ctx.shared.authenticated_jwt_token`, à vérifier dans la version de Kong) et le décoder (ex. `kong.plugins.jwt.jwt_parser`) — **pas de re-vérification de signature nécessaire**, le plugin `jwt` l'a faite ;
  - `X_USER_ID` = claim `sub` ;
  - `X_ROLE_ID` = `realm_access.roles` filtrés sur `USER`, `MODERATEUR`, `ADMIN`, joints par des virgules (ex. `MODERATEUR,USER`) ;
  - supprimer `Authorization`.
- Gérer proprement les cas limites (pas de token en contexte, pas de rôles) : décider et justifier (ex. 401 ou header vide).
- Fichier commenté ligne à ligne de façon pédagogique.
- Bonus possible : vérifier `azp == annonces-front`.

### 6.7 Backend Java (Spring Boot 3, Java 21)
- Dépendances : Spring Web, Spring Security, Spring Data JPA, Bean Validation, Flyway, PostgreSQL, Actuator, Testcontainers (test). **Aucune** dépendance OAuth2/JWT/Keycloak.
- `HeaderAuthenticationFilter extends OncePerRequestFilter` :
  - lit `X_USER_ID` et `X_ROLE_ID` ;
  - construit un `Authentication` (principal = userId, autorités = `ROLE_` + chaque rôle) → `SecurityContextHolder` ;
  - headers absents ou vides → 401 (via `AuthenticationEntryPoint`) ;
  - contexte toujours nettoyé en fin de requête.
- `SecurityFilterChain` : **stateless** (`SessionCreationPolicy.STATELESS`), pas de form login, pas de http basic, CSRF désactivé, `/actuator/health` en `permitAll`, tout le reste authentifié.
- `@EnableMethodSecurity` + `@PreAuthorize` sur toutes les règles (ex. `hasRole('MODERATEUR')`).
- Contrôle de propriété par expression : bean `annonceSecurity`, ex. `@PreAuthorize("@annonceSecurity.estAuteur(#id, authentication)")`.
- Cas délicat : `GET /api/annonces/{id}` doit renvoyer **404** (et non 403) si l'annonce n'est pas visible. Propose une solution qui respecte BK-04 (pas de `if` sur les rôles dans le métier) et explique-la.
- `DELETE /api/annonces/{id}` : `@PreAuthorize("hasRole('MODERATEUR') or @annonceSecurity.estAuteur(#id, authentication)")` (ADMIN hérite de MODERATEUR via le token).
- `auteurId` toujours pris dans le contexte de sécurité, jamais dans le DTO.
- Pagination : `size` ≤ 50 (validation → 400).
- Erreurs au format **`ProblemDetail` (RFC 9457)** : 400 (avec le champ en erreur), 401, 403, 404, 409 — y compris pour les erreurs émises par Spring Security (entry point et access denied handler personnalisés).
- Log de debug montrant les headers reçus (pour GW-08 : présence de `X_USER_ID`/`X_ROLE_ID`, absence d'`Authorization`).
- **Tests d'intégration** : `@SpringBootTest` + Testcontainers PostgreSQL, en injectant directement les headers (MockMvc ou client HTTP). Couvrir les critères BK et MT.

### 6.8 Frontend Angular
- Angular 17+ (standalone components, signals bienvenus), servi en production par Nginx (fallback SPA `try_files`).
- Auth : Authorization Code + PKCE avec `angular-oauth2-oidc` **ou** `keycloak-angular` (justifier le choix).
- Tokens en mémoire ou `sessionStorage`, **jamais** `localStorage`.
- `HttpInterceptor` ajoutant `Authorization: Bearer` **uniquement** aux appels vers la gateway.
- Renouvellement automatique avant expiration via refresh token.
- Déconnexion via `end_session` de Keycloak.
- Rôles lus dans l'**access token** (`realm_access.roles`).
- Écrans : liste des annonces, détail, mes annonces, formulaire création/modification, modération (MODERATEUR), catégories (ADMIN).
- Menus et routes masqués selon les rôles (guards `CanActivateFn`) — confort d'affichage uniquement.
- Gestion d'erreurs : 401 → login ; 403 → « accès refusé » sans déconnexion ; 429 → message avec le nombre de secondes lu dans `Retry-After`.
- **Configuration au runtime** (indispensable pour la CI/CD : une seule image pour tous les environnements) : URLs Keycloak / gateway lues depuis un `config.json` (ou `env.js`) généré au démarrage du conteneur Nginx par `envsubst`, pas compilées dans le bundle.

---

## 7. Pièges connus à anticiper (et à documenter dans le README)

1. Le plugin `jwt` de Kong OSS ne lit pas le JWKS : la clé publique est figée dans `kong.yml` → paire RSA **fixe** dans le realm.
2. `iss` = URL vue par le navigateur (`localhost:8180`), pas `keycloak:8080` → `KC_HOSTNAME` fixé et `key` du credential identique au caractère près.
3. `openid-connect` de Kong est Enterprise → interdit.
4. Nginx (sous Kong) ignore par défaut les headers client contenant `_`, mais le plugin doit tout de même les supprimer explicitement.
5. `Content-Type` doit être autorisé dans CORS ; `Retry-After` et `Location` exposés.
6. Keycloak 25+ : `sub` dans le scope `basic`.
7. Keycloak refuse le HTTP hors localhost/IP privée (`sslRequired: external`) et la Web Crypto API (utilisée pour PKCE S256) n'existe que dans un contexte sécurisé (HTTPS ou localhost) → impact direct sur le déploiement VM (cf. 8.2).
8. Ordre des plugins Kong : cors (2000) → jwt (1450) → identity-headers (~1000) → rate-limiting (910).

---

## 8. Déploiement sur VM + CI/CD GitHub Actions → Docker Hub (mon ajout)

### 8.1 Objectif
- Dépôt sur **mon GitHub**, images publiées sur **mon Docker Hub**.
- Déploiement sur une **VM Linux** avec **Docker Compose** (même `docker-compose.yml`).
- Pipeline : CI sur chaque push/PR, publication des images et déploiement automatique sur `main` (ou sur tag `v*`).

### 8.2 Le problème « localhost » sur une VM — propose et implémente une stratégie
Toute la spec est écrite pour `localhost` (iss, redirect URIs, CORS, credential Kong). Sur une VM, deux options, à expliquer dans `docs/deploiement.md` :

- **Option A — tunnel SSH (fidèle à la spec, zéro changement de config)** :
  `ssh -L 4200:localhost:4200 -L 8080:localhost:8080 -L 8180:localhost:8180 user@vm`
  → le navigateur reste sur `localhost`, contexte sécurisé, `iss` inchangé. Idéal pour la soutenance.
- **Option B — vraie exposition publique** : nom de domaine (ou nip.io/sslip.io) + HTTPS via un reverse proxy (ex. Caddy avec Let's Encrypt) dans un fichier **override** `docker-compose.vm.yml` (pour ne pas casser les 5 services de la spec). Tout ce qui dépend de l'URL publique devient paramétrable par `.env` : `PUBLIC_BASE_URL`, `KC_HOSTNAME`, redirect URIs et web origins du realm, origine CORS et `key` du credential dans `kong.yml` (générés depuis un template `kong/kong.yml.tpl` par `envsubst`, en gardant `kong/kong.yml` comme fichier chargé, cf. GW-11), config runtime du front.

### 8.3 Stratégie d'images
- Images publiées : `<DOCKERHUB_USERNAME>/petitesannonces-backend` et `<DOCKERHUB_USERNAME>/petitesannonces-frontend`.
- Tags : SHA court du commit + `latest` (+ version sémantique sur tag `v*`).
- Kong, Keycloak et PostgreSQL : images officielles (versions épinglées) ; les fichiers montés (`kong/`, `keycloak/`, `postgres/`) sont copiés sur la VM au déploiement.
- Builds avec `docker/setup-buildx-action`, `docker/metadata-action`, `docker/build-push-action` et cache GitHub Actions (`type=gha`).

### 8.4 Workflows à créer dans `.github/workflows/`

**`ci.yml`** — sur push et pull request :
- **backend** : JDK 21 (Temurin), cache Maven, `mvn -B verify` (Testcontainers fonctionne sur `ubuntu-latest`), publication des rapports de tests.
- **frontend** : Node LTS, cache npm, `npm ci`, lint, tests unitaires en headless, `ng build --configuration production`.
- **gateway** : validation de `kong.yml` (ex. `kong config parse` dans le conteneur officiel) et lint Lua (`luacheck`).
- **compose** : `docker compose config` pour valider le fichier.
- **e2e gateway** (job dédié) : `docker compose up -d --build --wait`, puis exécution du script `scripts/test-gateway.sh` couvrant GW et RL, puis `docker compose down -v`.
  - ⚠️ Le direct access grant étant interdit, le script ne peut pas obtenir de token avec `grant_type=password`. Propose : soit émuler le flux Authorization Code + PKCE en curl (récupérer le formulaire de login, poster les identifiants, suivre la redirection, échanger le code), soit — pour les tokens expirés/forgés/autre émetteur — signer des JWT de test avec la clé privée de test (openssl) ou une clé étrangère. **Ne pas** affaiblir la config du client `annonces-front` pour faciliter les tests.

**`cd.yml`** — sur push sur `main` (et/ou tag `v*`), après succès de la CI :
1. **build-push** : login Docker Hub, build et push des images backend et frontend (tags SHA + latest).
2. **deploy** (environment GitHub `production`, approbation manuelle optionnelle, `concurrency` pour éviter deux déploiements simultanés) :
   - copie sur la VM (scp) de `docker-compose.yml` (+ override VM), `kong/`, `keycloak/`, `postgres/` ;
   - écriture du `.env` sur la VM à partir d'un secret GitHub ;
   - via SSH : `docker compose pull` puis `docker compose up -d --no-build --remove-orphans` avec `IMAGE_TAG=<sha>` ;
   - **smoke tests** : healthchecks OK, `GET /api/annonces` sans token → 401 ;
   - nettoyage des anciennes images (`docker image prune -f`).
3. **Rollback** documenté : redéployer avec un `IMAGE_TAG` précédent.

### 8.5 Secrets GitHub à documenter dans le README
`DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` (access token Docker Hub, pas le mot de passe), `VM_HOST`, `VM_USER`, `VM_SSH_KEY` (clé privée dédiée au déploiement), `VM_SSH_PORT` (optionnel), `ENV_FILE` (contenu complet du `.env` de production : mots de passe Postgres/Keycloak, clé RSA du realm…).

### 8.6 Préparation de la VM (documenter dans `docs/deploiement.md`)
- Installation de Docker Engine + plugin Compose, utilisateur de déploiement dans le groupe `docker`, clé SSH dédiée.
- Pare-feu : n'ouvrir que 22 (+ 4200, 8080, 8180 en option B ou 80/443 avec reverse proxy). Rappeler que les ports publiés par Docker **contournent ufw** et comment le gérer.
- Dossier de déploiement (ex. `/opt/petitesannonces`), sauvegarde du volume PostgreSQL.

---

## 9. Critères d'acceptation (48) — checklist à maintenir à jour dans `docs/criteres.md`

Pour chaque critère, indique comment le démontrer (navigateur, curl, test automatisé) et le statut.

### 9.1 Déploiement (DEP)
- **DEP-01** : `.env` copié depuis `.env.example`, `docker compose up -d --build` → les 5 conteneurs `healthy` en < 3 min, sans action manuelle.
- **DEP-02** : `http://localhost:8081` et le port 5432 depuis l'hôte → connexion échoue.
- **DEP-03** : `http://localhost:8180` → realm `annonces` importé avec clients, rôles, utilisateurs.
- **DEP-04** : annonces créées, `down` puis `up` → annonces et config Keycloak conservées.
- **DEP-05** : aucun secret commité hors `.env.example` (valeurs factices).

### 9.2 Keycloak et PKCE (KC)
- **KC-01** : utilisateur non connecté sur `localhost:4200` → redirigé vers le login du realm `annonces`.
- **KC-02** : l'URL d'autorisation contient `response_type=code`, `code_challenge`, `code_challenge_method=S256`.
- **KC-03** : le front échange le `code` sur `/token` avec le `code_verifier` ; aucun token dans l'URL.
- **KC-04** : `grant_type=password` ou flux implicit sur `annonces-front` → refusé.
- **KC-05** : token de `modo` → `realm_access.roles` contient `MODERATEUR` et `USER`.
- **KC-06** : « Se déconnecter » → session Keycloak fermée, retour sur l'app redemande le login.

### 9.3 Frontend (FR)
- **FR-01** : chaque appel à la gateway porte `Authorization: Bearer…` ; aucun appel à Keycloak ne le porte inutilement.
- **FR-02** : aucun token dans le `localStorage`.
- **FR-03** : `alice` (USER) : pas d'entrées « Modération » ni « Catégories » ; `/moderation` saisi à la main → redirection.
- **FR-04** : connectée depuis plus de 5 min → token renouvelé sans retour au login.
- **FR-05** : réponse 429 → message avec le nombre de secondes lu dans `Retry-After`.
- **FR-06** : réponse 403 → « accès refusé », sans déconnexion.

### 9.4 Gateway : authentification (GW)
- **GW-01** : pas d'`Authorization` sur `GET /api/annonces` → 401, le backend n'est pas atteint (logs).
- **GW-02** : signature altérée → 401.
- **GW-03** : token expiré (> 5 min) → 401.
- **GW-04** : token valide d'un autre realm/Keycloak → 401 (`iss` sans credential Kong).
- **GW-05** : token d'`alice` sur `GET /api/me` → 200, `userId` = `sub` d'alice, `roles` = `["USER"]`.
- **GW-06** : token d'`alice` + `X_USER_ID` forgé avec le `sub` de bob → réponse avec le `sub` d'alice.
- **GW-07** : token d'`alice` + `X_ROLE_ID: ADMIN` forgé sur `POST /api/categories` → 403.
- **GW-08** : le backend reçoit `X_USER_ID` et `X_ROLE_ID` mais pas `Authorization` (log debug).
- **GW-09** : preflight depuis `http://evil.local` → pas d'`Access-Control-Allow-Origin`.
- **GW-10** : `http://localhost:8001` depuis l'hôte → connexion échoue.
- **GW-11** : config entièrement dans `kong/kong.yml` (DB-less), plugin dans `kong/plugins/`, uniquement plugins OSS.

### 9.5 Rate limiting (RL)
- **RL-01** : 3 appels en < 1 min → OK.
- **RL-02** : 4ᵉ appel sur n'importe quel `/api/**` → 429 + `Retry-After` en secondes ; backend non appelé.
- **RL-03** : après le délai indiqué → l'appel répond normalement.
- **RL-04** : `alice` bloquée, `bob` appelle → `bob` non bloqué (compteur par utilisateur).
- **RL-05** : `alice` bloquée, preflight `OPTIONS` → répond sans être décompté.

### 9.6 Backend (BK)
- **BK-01** : `pom.xml` sans `spring-boot-starter-oauth2-*`, `jjwt`, `nimbus`, Keycloak.
- **BK-02** : requête sans `X_USER_ID` (test d'intégration) → 401.
- **BK-03** : `X_USER_ID: u1` + `X_ROLE_ID: MODERATEUR,USER` → principal `u1`, autorités `ROLE_MODERATEUR`, `ROLE_USER`.
- **BK-04** : chaque règle d'accès via `@PreAuthorize`, pas de `if` sur les rôles dans le métier.
- **BK-05** : deux requêtes d'utilisateurs différents → aucune fuite de contexte, aucune session créée.

### 9.7 Règles métier (MT)
- **MT-01** : `alice` crée une annonce valide → 201, `EN_ATTENTE`, `auteurId` = son `sub` même si le corps en envoie un autre.
- **MT-02** : annonce `EN_ATTENTE` d'alice, `bob` fait `GET /api/annonces/{id}` → 404.
- **MT-03** : `bob` tente `PUT` ou `DELETE` sur une annonce d'alice → 403.
- **MT-04** : alice modifie son annonce `PUBLIEE` → 200, retour en `EN_ATTENTE`, disparaît de la liste publique.
- **MT-05** : alice (USER) sur `PATCH /api/moderation/annonces/{id}/publier` → 403.
- **MT-06** : `modo` publie une annonce en attente → 200, visible dans `GET /api/annonces`.
- **MT-07** : `modo` rejette sans `motif` → 400 `ProblemDetail`.
- **MT-08** : `modo` sur `POST /api/categories` → 403 ; `admin` → 201.
- **MT-09** : `admin` supprime une catégorie utilisée → 409.
- **MT-10** : titre de 3 caractères ou prix négatif → 400 détaillant le champ en erreur.

---

## 10. Livrables

- Dépôt Git avec l'arborescence de la section 6.1.
- `README.md` : démarrage en une commande, comptes de test, schéma d'architecture, choix techniques justifiés, **procédure d'extraction de la clé publique du realm** (au format PEM pour `kong.yml`), section CI/CD et déploiement VM.
- Collection **Bruno** ou Postman (ou script curl) couvrant GW, RL et MT.
- Tests automatisés du backend (BK et MT).
- Script de test de la gateway : token absent, expiré, forgé, rate limit.
- Workflows GitHub Actions (CI + CD) fonctionnels.
- `docs/` : compréhension, plan, flux d'authentification (Mermaid), déploiement, checklist des critères.
- Préparation d'une **soutenance de 20 min** : propose un scénario de démo en direct ordonné (ce que je montre, dans quel ordre, avec quelles commandes) et une liste de questions probables avec réponses.

---

## 11. Bonus (jusqu'à +10 points) — à traiter après le socle
- Rate limiting partagé entre 2 instances de Kong (`policy: redis` + conteneur Redis).
- Vérification du claim `azp` (= `annonces-front`) dans `identity-headers`.
- Secret partagé ou mTLS entre Kong et le backend (le backend refuse un appel ne venant pas de la gateway).
- Récupération automatique de la clé publique au démarrage (script qui lit le JWKS et génère `kong.yml`).
- Traçabilité : plugin `correlation-id` de Kong, identifiant repris dans les logs du backend (MDC).

---

## 12. Grille d'évaluation

| Domaine | Critères | Points |
|---------|----------|--------|
| Déploiement Docker Compose | DEP-01 à 05 | 15 |
| Keycloak et flux PKCE | KC-01 à 06 | 15 |
| Frontend Angular | FR-01 à 06 | 15 |
| Gateway : authentification et headers | GW-01 à 11 | 20 |
| Gateway : rate limiting | RL-01 à 05 | 10 |
| Backend : contexte Spring Security | BK-01 à 05 | 10 |
| Règles métier | MT-01 à 10 | 10 |
| Qualité : tests, README, soutenance | — | 5 |
| **Total** | | **100** |

Seuil : **70 points**, avec **GW-01 à GW-07 et BK-01 à BK-03 obligatoires**. Bonus : +10 max. Priorise donc ces critères obligatoires.

---

## 13. Plan de phases suggéré (à affiner dans `docs/01-plan.md`)

1. **Socle** : arborescence, `.gitignore`, `.env.example`, README squelette, `docs/00-comprehension.md`.
2. **PostgreSQL + Keycloak** : `init.sql`, realm complet (rôles composites, client PKCE, utilisateurs, clé RSA fixe, token 5 min), compose partiel ; vérif DEP-03, KC-04, KC-05.
3. **Backend** : entités, Flyway, filtre headers, SecurityConfig, `@PreAuthorize`, ProblemDetail, tests Testcontainers ; vérif BK-*, MT-*.
4. **Kong** : `kong.yml` (service, route, cors, jwt, consumer), extraction de la clé publique ; vérif GW-01 à GW-04.
5. **Plugin Lua `identity-headers`** + rate limiting ; vérif GW-05 à GW-11, RL-*.
6. **Frontend Angular** : OIDC PKCE, interceptor, guards, écrans, gestion 401/403/429, config runtime ; vérif KC-*, FR-*.
7. **Compose complet** : healthchecks, `depends_on`, réseaux, Dockerfiles multi-stage ; vérif DEP-*.
8. **Tests transverses** : script gateway, collection Bruno.
9. **CI/CD** : `ci.yml`, `cd.yml`, Docker Hub, déploiement VM, `docs/deploiement.md`.
10. **Bonus** puis **préparation soutenance**.

---

## 14. Ta première action

Ne code rien encore. Commence par :
1. me poser les questions bloquantes éventuelles (choix de librairie OIDC Angular, option A ou B pour la VM, versions précises à épingler, nom de mon compte Docker Hub) ;
2. rédiger `docs/00-comprehension.md` (reformulation, flux d'authentification détaillé avec diagramme de séquence Mermaid, rôle de chaque composant, pièges) ;
3. rédiger `docs/01-plan.md` ;
4. attendre ma validation.
