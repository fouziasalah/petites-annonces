# 01 — Plan de réalisation

Les critères obligatoires (**GW-01 à GW-07, BK-01 à BK-03**) sont traités en priorité.
Chaque phase se termine par un commit Conventional Commits et la mise à jour de [`criteres.md`](criteres.md).

## Décisions techniques

| Sujet | Décision | Raison |
|---|---|---|
| Kong | `kong:3.9.4` | Dernière image OSS publiée sur Docker Hub (vérifié) |
| Keycloak | `quay.io/keycloak/keycloak:26.8.1` | Dernier patch 26.x (vérifié sur quay.io) |
| PostgreSQL | `postgres:16.15-alpine` | Exigé : 16 ; image légère |
| Backend | Java 21 Temurin, Spring Boot 3.5.x, Maven Wrapper | Pas de Maven requis sur le poste |
| Front | Angular + `angular-oauth2-oidc` | OIDC standard, flux explicite, sessionStorage, refresh par refresh token |
| Clé RSA | paire **DEV** dans `.env.example`, paire de prod générée par `scripts/gen-keys.sh` | DEP-01 sans action manuelle ; aucune clé de prod dans Git |
| Secrets du realm | placeholders `${VAR}` substitués par Keycloak à l'import | Le JSON versionné ne contient aucun secret |
| Ports | publiés sur `${BIND_ADDRESS:-127.0.0.1}` | Sur la VM, Docker contourne ufw ; le tunnel SSH suffit |
| VM | Option A (tunnel SSH) d'abord, option B (Caddy + HTTPS) en override | A = zéro changement de config |
| Tests gateway | bash + curl + openssl : émulation PKCE + JWT signés à la main | Pas de grant `password`, pas de client affaibli |

## Phases

| # | Phase | Contenu | Critères |
|---|---|---|---|
| 1 | Socle | Git, `.gitignore`, `.gitattributes`, `.editorconfig`, docs, README squelette | — |
| 2 | PostgreSQL + Keycloak | `init.sql`, realm (rôles composites, client PKCE, users, clé RSA fixe, AT 5 min), `gen-keys.sh`, script d'obtention de token PKCE, compose partiel | DEP-03, KC-04, KC-05 |
| 3 | Backend | Entités, Flyway, filtre headers, SecurityConfig, `@PreAuthorize`, ProblemDetail, tests Testcontainers, Dockerfile | BK-01..05, MT-01..10 |
| 4 | Kong | `kong.yml` (service, route, cors, jwt, consumer), extraction de la clé publique | GW-01..04, GW-09..11 |
| 5 | Plugin `identity-headers` + rate limiting | `handler.lua`, `schema.lua`, plugin `rate-limiting` | GW-05..08, RL-01..05 |
| 6 | Frontend | OIDC PKCE, interceptor, guards, écrans, erreurs 401/403/429, config runtime, Dockerfile | KC-01..03, KC-06, FR-01..06 |
| 7 | Compose complet | healthchecks, `depends_on`, réseaux, persistance | DEP-01, 02, 04, 05 |
| 8 | Tests transverses | `scripts/test-gateway.sh`, collection Bruno | GW, RL, MT |
| 9 | CI/CD + VM | `ci.yml`, `cd.yml`, Docker Hub, `docs/deploiement.md` | — |
| 10 | Bonus + soutenance | `azp`, correlation-id, scénario de démo | — |
