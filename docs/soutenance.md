# Soutenance — 20 minutes

## Avant de commencer (J-1 et H-15 min)

```bash
docker compose down -v && cp .env.example .env && docker compose up -d --build --wait
docker compose ps                                  # 5 × healthy
docker compose logs -f backend | grep X_USER_ID    # terminal 2, laissé ouvert (GW-08)
```
Ouvrir : http://localhost:4200 (fenêtre privée), DevTools (onglets Réseau + Application),
http://localhost:8180/admin, et le dépôt GitHub (onglet Actions).

## Déroulé

| Min. | Ce que je montre | Comment | Critères |
|---|---|---|---|
| 0-2 | Le principe : **Keycloak authentifie, Kong contrôle, le backend autorise** | Schéma du README, diagramme de séquence (`docs/00-comprehension.md`) | — |
| 2-4 | Démarrage et isolement réseau | `docker compose ps` ; `curl localhost:8081` et `localhost:8001` échouent | DEP-01, DEP-02, GW-10 |
| 4-7 | Connexion PKCE | Fenêtre privée → redirection Keycloak ; dans Réseau : `code_challenge`, `S256`, retour `?code=`, `POST /token` avec `code_verifier` ; Application : rien en localStorage | KC-01..03, FR-02 |
| 7-9 | Le token | `scripts/get-token.sh modo \| scripts/decode-jwt.sh` : `iss`, `sub`, `azp`, `realm_access.roles`, `exp − iat = 300` | KC-05 |
| 9-11 | Ce que reçoit le backend | Terminal 2 : `X_USER_ID`, `X_ROLE_ID`, `Authorization présent=false` | GW-08 |
| 11-15 | Attaques | `scripts/test-gateway.sh` : 22 vérifications, commenter chaque ligne (forgé, expiré, autre émetteur, `X_USER_ID` usurpé, `?jwt=`, evil.local, quota, contournement `X-User-Id`) | GW-01..09, RL-01..05 |
| 15-17 | Rôles dans l'UI | alice : pas de menus Modération/Catégories, `/moderation` à la main → redirection ; modo publie une annonce ; alice modifie celle de bob → « Accès refusé » | FR-03, FR-06, MT-06 |
| 17-19 | Industrialisation | Onglet Actions (CI verte, CD), Docker Hub, `https://app.72-60-188-97.sslip.io` | — |
| 19-20 | Conclusion : ce que j'ai appris, limites, bonus | — | — |

## Questions probables

**Pourquoi PKCE si la SPA est en HTTPS ?** Un client public ne peut garder aucun secret. PKCE lie
le code d'autorisation au navigateur qui l'a demandé : un code intercepté (historique, logs,
extension) est inutilisable sans le `code_verifier`, qui n'a jamais quitté le navigateur.

**Pourquoi le backend fait-il confiance à des headers ?** Parce qu'il n'est joignable que sur le
réseau Docker `internal`, par Kong, qui purge ces headers et les reconstruit depuis un token
vérifié. Limite assumée : un attaquant présent sur le réseau interne pourrait les forger ; la
parade est un secret partagé ou du mTLS entre Kong et le backend (bonus).

**Que se passe-t-il si Keycloak change de clé ?** Kong rejette tous les tokens (401) : le plugin
`jwt` OSS ne lit pas le JWKS. D'où la paire fixe, une seule source (`.env`) et
`scripts/extract-public-key.sh` pour vérifier la cohérence.

**Pourquoi l'origine CORS est-elle une regex ?** Avec une seule origine littérale, Kong renvoie
toujours cet `Access-Control-Allow-Origin`, quelle que soit l'origine de la requête. Le navigateur
bloquerait quand même, mais en regex Kong compare réellement l'`Origin`.

**Nginx n'ignore-t-il pas déjà les headers avec `_` ?** Pas dans Kong : son template active
`underscores_in_headers on`. Un `X_USER_ID` forgé arrive bien jusqu'au plugin, qui le purge.

**Pourquoi purger aussi `X-User-Id` ?** Nginx expose `X_USER_ID` et `X-User-Id` sous la même
variable `$http_x_user_id`, lue par le rate limiting : sans purge, un client pourrait changer la
clé de son compteur à chaque appel et contourner le quota. Le test RL-02b le prouve.

**Pourquoi 404 et pas 403 sur une annonce en attente ?** Un 403 confirmerait qu'elle existe.
`@PostAuthorize` exprime la règle, `@HandleAuthorizationDenied` transforme le refus en 404.

**Pourquoi le token n'est-il pas en localStorage ?** sessionStorage disparaît à la fermeture de
l'onglet et n'est pas partagé entre onglets. Les deux restent lisibles par un script injecté :
la vraie protection contre le XSS est la CSP stricte servie par Nginx.

**Que contrôle le front ?** Rien de sécurisé : masquer un menu est un confort. La preuve :
alice qui force l'URL obtient quand même un 403 du backend.

**Le rate limiting fonctionne-t-il avec plusieurs instances de Kong ?** Non avec `policy: local`
(compteur par instance). Il faudrait `policy: redis` (bonus).

**Comment faire un rollback ?** Workflow CD manuel avec le tag SHA d'une image précédente :
les images sont immuables sur Docker Hub.
