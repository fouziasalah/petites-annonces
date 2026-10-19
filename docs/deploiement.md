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
| VM | Ubuntu 24.04, `72.60.188.97`, dossier `/opt/petitesannonces`, utilisateur `deploy` |

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
ssh -N -L 4200:localhost:4200 -L 8080:localhost:8080 -L 8180:localhost:8180 deploy@72.60.188.97
```

## 2. 🧑 Préparer la VM (une seule fois)

Connecté en `root` sur la VM.

**2.1 État des lieux** — envoie-moi la sortie, pour vérifier la RAM et les ports déjà occupés :
```bash
free -h && nproc && docker --version; docker compose version; ss -tlnp | grep -E ':(22|80|443|4200|8080|8180) '
```

**2.2 Docker Engine + plugin Compose** (si absent — dépôt officiel Docker) :
```bash
apt-get update && apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

**2.3 Utilisateur de déploiement** — GitHub Actions ne se connecte pas en root :
```bash
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
install -d -o deploy -g deploy /opt/petitesannonces
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
```
> Être dans le groupe `docker` équivaut à être root sur la machine : ce compte ne sert qu'au déploiement.

**2.4 Clé SSH dédiée au déploiement** — sur **ton PC** (Git Bash), sans passphrase car c'est un robot qui l'utilise :
```bash
ssh-keygen -t ed25519 -N "" -C "github-actions-petitesannonces" -f ~/.ssh/petitesannonces_deploy
cat ~/.ssh/petitesannonces_deploy.pub          # à copier sur la VM
```
Puis sur la **VM** (colle la ligne `ssh-ed25519 …` à la place de `<CLE_PUBLIQUE>`) :
```bash
echo "<CLE_PUBLIQUE>" >> /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
```
Test depuis ton PC : `ssh -i ~/.ssh/petitesannonces_deploy deploy@72.60.188.97 docker ps`

**2.5 Pare-feu**
```bash
ufw allow OpenSSH
ufw allow 80/tcp && ufw allow 443/tcp      # option B uniquement
ufw enable && ufw status
```
> ⚠️ Docker écrit ses propres règles iptables : un port publié en `0.0.0.0:xxxx` est joignable
> **même si ufw le bloque**. C'est pourquoi le compose publie sur `${BIND_ADDRESS}` = `127.0.0.1` :
> seuls le tunnel SSH (A) ou Caddy (B) y accèdent.

## 3. 🧑 Docker Hub

1. https://hub.docker.com → *Account settings* → *Personal access tokens* → **Generate new token**
   - description : `github-actions-petitesannonces`, permissions : **Read & Write**
2. Copie le token (affiché une seule fois) → il ira dans le secret `DOCKERHUB_TOKEN`.

Les dépôts d'images sont créés automatiquement (publics) au premier push.

## 4. 🧑 Générer les secrets de production

Sur ton PC, à la racine du projet :
```bash
scripts/init-prod-env.sh 72-60-188-97.sslip.io    # option B
# ou : scripts/init-prod-env.sh                   # option A
```
🤖 Le script crée **`.env.prod`** (ignoré par Git) : mots de passe aléatoires, **nouvelle** paire RSA
(différente de la clé de dev publique), URLs publiques. Conserve-le dans un gestionnaire de mots de
passe : il contient les identifiants admin Keycloak et ceux des comptes de test.

## 5. 🧑 Secrets GitHub

Dépôt → *Settings* → *Secrets and variables* → *Actions* → **New repository secret**.

| Secret | Valeur | Comment l'obtenir |
|---|---|---|
| `DOCKERHUB_USERNAME` | `fouziasalahhhh` | — |
| `DOCKERHUB_TOKEN` | token Docker Hub | étape 3 |
| `VM_HOST` | `72.60.188.97` | — |
| `VM_USER` | `deploy` | étape 2.3 |
| `VM_SSH_PORT` | `22` | — |
| `VM_SSH_KEY` | contenu **complet** de `~/.ssh/petitesannonces_deploy` (clé **privée**, lignes BEGIN/END comprises) | `cat ~/.ssh/petitesannonces_deploy` |
| `VM_KNOWN_HOSTS` | empreinte du serveur (évite de désactiver la vérification SSH) | `ssh-keyscan -p 22 72.60.188.97` |
| `ENV_FILE` | contenu complet de `.env.prod` | `cat .env.prod` |

Puis *Settings* → *Environments* → **New environment** `production` (tu peux y activer
*Required reviewers* pour valider chaque déploiement à la main).

## 6. 🤖 Ce que fait la pipeline

*(workflows écrits en phase 9 — cette section sera complétée)*

## 7. Rollback

Relancer le workflow `cd.yml` (*Run workflow*) avec le tag d'image précédent (SHA court du commit).

## 8. Points d'attention

- **Realm et `init.sql` ne s'appliquent qu'au premier démarrage** (volume vide). Changer
  `.env.prod` après le premier déploiement ne change ni les mots de passe en base ni le realm :
  il faut alors repartir d'un volume vide (`docker compose down -v`, **perte des données**).
- **Sauvegarde** : `docker compose exec postgres pg_dumpall -U postgres > sauvegarde.sql`.
