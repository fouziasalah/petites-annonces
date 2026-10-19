# Déploiement : Docker Compose · VM · GitHub Actions

Ce guide liste **ce que tu fais toi-même** (🧑) et **ce que le dépôt fait automatiquement** (🤖).
Aucun secret n'est écrit dans ce document : seulement leur nom et où les saisir.

## 0. Vue d'ensemble

```
git push main ──► GitHub Actions
                   ├─ ci.yml : tests backend, front, Kong, e2e gateway
                   └─ cd.yml : build → push Docker Hub (fouziasalahhhh/petitesannonces-*)
                                 └─► SSH sur la VM : docker compose pull && up -d
```

| Élément | Valeur |
|---|---|
| Dépôt GitHub | `github.com/fouziasalah/petites-annonces` (public) |
| Images Docker Hub | `fouziasalahhhh/petitesannonces-backend`, `fouziasalahhhh/petitesannonces-frontend` |
| VM | Ubuntu 24.04, `72.60.188.97`, dossier `/opt/petitesannonces`, connexion du robot en `root` |

## 1. Le problème « localhost » et les deux options

Toute la spec est écrite pour `localhost`. Sur une VM accédée par `http://72.60.188.97` :
- le navigateur n'est pas dans un *contexte sécurisé* → pas de Web Crypto → PKCE S256 impossible ;
- Keycloak (`sslRequired: external`) refuse le HTTP depuis une IP publique.

| | Option A — tunnel SSH | Option B — HTTPS public |
|---|---|---|
| URLs | `http://localhost:4200`, `:8080`, `:8180` (via le tunnel) | `https://app.72-60-188-97.sslip.io`, `https://api.…`, `https://auth.…` |
| Config | aucune modification | URLs dans `.env`, Caddy (certificats Let's Encrypt automatiques) |
| Accès Keycloak par URL | seulement avec le tunnel ouvert | oui, depuis n'importe où |
| Ports ouverts sur la VM | 22 | 22, 80, 443 |

`sslip.io` est un DNS public gratuit : `app.72-60-188-97.sslip.io` résout vers `72.60.188.97`,
ce qui permet d'obtenir un vrai certificat HTTPS sans acheter de domaine.

**Tunnel (option A)**, depuis ton PC :
```bash
ssh -N -L 4200:localhost:4200 -L 8080:localhost:8080 -L 8180:localhost:8180 root@72.60.188.97
```

## 2. 🤖 La VM est préparée par la pipeline

Aucune commande à taper sur la VM. Avant chaque déploiement, le CD exécute
`scripts/vm-bootstrap.sh` par SSH (idempotent) :

- installe Docker Engine + plugin Compose depuis le dépôt officiel Docker s'ils manquent ;
- crée `/opt/petitesannonces` ;
- si ufw est actif, autorise 80/tcp et 443/tcp (Let's Encrypt doit joindre le port 80) —
  il n'active jamais ufw lui-même, pour ne pas risquer de couper SSH ;
- s'arrête avec un message clair si 80/443 sont déjà occupés par un autre service.

> Le robot se connecte en `root` (nécessaire pour installer Docker sans intervention).
> Durcissement possible ensuite : utilisateur `deploy` dans le groupe `docker`, et `VM_USER=deploy`.
>
> ⚠️ Docker écrit ses propres règles iptables : un port publié en `0.0.0.0:xxxx` est joignable
> **même si ufw le bloque**. C'est pourquoi le compose publie 4200/8080/8180 sur `127.0.0.1` :
> seul Caddy (80/443) est exposé.

## 3. 🧑 Ce que tu fais (une seule fois)

**3.1 Préparer les fichiers de secrets** — sur ton PC, à la racine du projet :
```bash
scripts/prepare-secrets.sh
```
🤖 Crée la clé SSH du robot (`~/.ssh/petitesannonces_deploy`), `.env.prod` (mots de passe
aléatoires, **nouvelle** paire RSA, URLs `https://…sslip.io`) et le dossier **`.secrets/`** avec un
fichier par secret GitHub. `.env.prod` et `.secrets/` sont ignorés par Git. Conserve `.env.prod`
dans un gestionnaire de mots de passe : il contient les identifiants admin Keycloak.

**3.2 Autoriser la clé du robot sur la VM** — depuis ton PC (demande la passphrase de TA clé) :
```bash
ssh-copy-id -i ~/.ssh/petitesannonces_deploy.pub root@72.60.188.97
ssh -i ~/.ssh/petitesannonces_deploy root@72.60.188.97 "echo OK"     # doit afficher OK
```

**3.3 Token Docker Hub** — https://hub.docker.com → *Account settings* → *Personal access tokens*
→ **Generate new token** (`github-actions-petitesannonces`, **Read & Write**). Colle-le dans
`.secrets/DOCKERHUB_TOKEN` (ou directement dans GitHub à l'étape suivante).

**3.4 Secrets GitHub** — https://github.com/fouziasalah/petites-annonces/settings/secrets/actions
→ **New repository secret**, une fois par fichier de `.secrets/` : *Name* = nom du fichier,
*Secret* = son contenu (ouvre-le dans ton éditeur, copie tout).

| Secret | Fichier | Contenu |
|---|---|---|
| `DOCKERHUB_USERNAME` | `.secrets/DOCKERHUB_USERNAME` | `fouziasalahhhh` |
| `DOCKERHUB_TOKEN` | `.secrets/DOCKERHUB_TOKEN` | token de l'étape 3.3 |
| `VM_HOST` | `.secrets/VM_HOST` | `72.60.188.97` |
| `VM_USER` | `.secrets/VM_USER` | `root` |
| `VM_SSH_PORT` | `.secrets/VM_SSH_PORT` | `22` |
| `VM_SSH_KEY` | `.secrets/VM_SSH_KEY` | clé **privée** du robot (lignes BEGIN/END comprises) |
| `VM_KNOWN_HOSTS` | `.secrets/VM_KNOWN_HOSTS` | empreinte du serveur (`ssh-keyscan`) : évite de désactiver la vérification SSH |
| `ENV_FILE` | `.secrets/ENV_FILE` | copie de `.env.prod` |

**3.5 Environnement et interrupteur**
- *Settings* → *Environments* → **New environment** : `production` (option : *Required reviewers*
  pour valider chaque déploiement à la main) ;
- *Settings* → *Secrets and variables* → *Actions* → onglet **Variables** → **New repository
  variable** : `DEPLOY_ENABLED` = `true`. Tant qu'elle n'existe pas, le CD est ignoré.

**3.6 Premier déploiement** — *Actions* → **CD** → *Run workflow* (champ `image_tag` vide).
Ensuite, chaque push sur `main` dont la CI est verte est déployé automatiquement.

## 4. 🤖 Ce que fait la pipeline

**`ci.yml`** — à chaque push sur `main` et chaque pull request :

| Job | Ce qu'il vérifie |
|---|---|
| backend | `./mvnw verify` : tests unitaires + intégration (Testcontainers PostgreSQL) |
| frontend | `ng lint`, `ng test`, `ng build --configuration production` |
| gateway | `kong config parse` (Kong valide `kong.yml` et le schéma du plugin) + `luacheck` |
| compose | `docker compose config` (local et surcharge VM) |
| e2e | les 5 services démarrés, puis `scripts/test-gateway.sh` (22 vérifications GW/RL) |

**`cd.yml`** — quand la CI réussit sur `main` :

1. **build-push** : construit les images backend et frontend, les pousse sur Docker Hub avec deux
   tags : le SHA court du commit (immuable, sert au rollback) et `latest`.
2. **deploy** (environnement `production`, un seul déploiement à la fois) :
   - SSH avec la clé dédiée, empreinte du serveur vérifiée (`VM_KNOWN_HOSTS`) ;
   - copie de `docker-compose*.yml`, `kong/`, `keycloak/`, `postgres/`, `caddy/` dans
     `/opt/petitesannonces` (pas de code source sur la VM : il est dans les images) ;
   - écriture du `.env` (droits 600) depuis `ENV_FILE`, avec `IMAGE_TAG=<sha>` ;
   - `docker compose pull` puis `up -d --no-build --remove-orphans --wait` ;
   - smoke tests HTTPS : `config.json` du front, `issuer` de Keycloak, `401` de la gateway sans token ;
   - `docker image prune -f`.

## 5. Rollback

*Actions* → *CD* → *Run workflow* → renseigner `image_tag` avec le SHA court d'un déploiement
précédent (visible dans l'historique des runs ou sur Docker Hub). Le job de build est sauté et
la VM redémarre sur ces images.

## 6. Points d'attention

- **Realm et `init.sql` ne s'appliquent qu'au premier démarrage** (volume vide). Changer
  `.env.prod` après le premier déploiement ne change ni les mots de passe en base ni le realm :
  il faut alors repartir d'un volume vide (`docker compose down -v`, **perte des données**).
- **Sauvegarde** : `docker compose exec postgres pg_dumpall -U postgres > sauvegarde.sql`.
