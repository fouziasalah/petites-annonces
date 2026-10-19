-- =============================================================================
-- Plugin Kong « identity-headers »
--
-- Rôle : transformer un JWT DÉJÀ VALIDÉ en headers d'identité pour le backend,
-- puis retirer le token. Le backend ne voit ainsi jamais de JWT.
--
--   avant (client → Kong)            après (Kong → backend)
--   Authorization: Bearer eyJ…       X_USER_ID: 3f2a…            (claim sub)
--   X_USER_ID: <forgé>               X_ROLE_ID: MODERATEUR,USER  (realm_access.roles filtrés)
--                                    (plus d'Authorization)
-- =============================================================================

-- Décodeur JWT fourni par le plugin jwt officiel : on réutilise le parseur de
-- Kong au lieu d'en réécrire un.
local jwt_decoder = require "kong.plugins.jwt.jwt_parser"

local USER_ID_HEADER = "X_USER_ID"
local ROLES_HEADER = "X_ROLE_ID"

-- Headers que le client pourrait forger.
-- ATTENTION : Kong configure nginx avec « underscores_in_headers on » ; un
-- X_USER_ID envoyé par le client ARRIVE donc bien jusqu'ici. De plus, nginx
-- considère X-User-Id et X_USER_ID comme le même header pour $http_x_user_id,
-- que le plugin rate-limiting utilise : sans purger aussi la variante à tirets,
-- un client pourrait fausser la clé de son compteur et contourner le quota.
local SPOOFABLE_HEADERS = { "X_USER_ID", "X_ROLE_ID", "X-User-Id", "X-Role-Id" }

local IdentityHeadersHandler = {
    -- Kong exécute les plugins par priorité DÉCROISSANTE.
    --   jwt = 1450 > 1000 : le token est déjà vérifié quand on le lit ;
    --   1000 > 910 = rate-limiting : le compteur voit notre X_USER_ID.
    PRIORITY = 1000,
    VERSION = "1.0.0",
}

-- Refus explicite : en sécurité, en cas de doute on bloque (« fail closed »).
local function unauthorized(reason)
    kong.log.warn("identity-headers : requête refusée (", reason, ")")
    return kong.response.exit(401, { message = "Unauthorized" })
end

-- Construit "ADMIN,MODERATEUR,USER" à partir de realm_access.roles,
-- en ne gardant que les rôles autorisés par la configuration.
local function business_roles(claims, allowed_roles)
    local allowed = {}
    for _, role in ipairs(allowed_roles) do
        allowed[role] = true
    end

    local roles = {}
    local realm_access = claims.realm_access
    if type(realm_access) == "table" and type(realm_access.roles) == "table" then
        for _, role in ipairs(realm_access.roles) do
            if allowed[role] then
                roles[#roles + 1] = role
            end
        end
    end
    table.sort(roles) -- ordre stable : plus simple à lire dans les logs et à tester
    return table.concat(roles, ",")
end

-- Phase « access » : exécutée avant l'appel au backend.
function IdentityHeadersHandler:access(conf)
    -- 1. Anti-usurpation : on supprime TOUT ce que le client a pu envoyer,
    --    AVANT toute autre opération (même si on refuse ensuite la requête).
    for _, name in ipairs(SPOOFABLE_HEADERS) do
        kong.service.request.clear_header(name)
    end

    -- 2. Le token que le plugin jwt vient de valider (signature, iss, exp).
    --    Pas besoin de revérifier la signature : jwt (priorité 1450) l'a fait,
    --    et aurait déjà répondu 401 en cas d'échec.
    local token = kong.ctx.shared.authenticated_jwt_token
    if not token then
        -- Ne devrait pas arriver si jwt est actif sur la route ; ce cas signale
        -- une erreur de configuration : on refuse plutôt que laisser passer.
        return unauthorized("aucun token validé dans le contexte")
    end

    local jwt, err = jwt_decoder:new(token)
    if err then
        return unauthorized("token illisible : " .. tostring(err))
    end
    local claims = jwt.claims

    -- 3. Identité : le « sub » Keycloak, identifiant stable et unique de l'utilisateur.
    if type(claims.sub) ~= "string" or claims.sub == "" then
        return unauthorized("claim sub absent")
    end

    -- 4. (bonus) Le token a-t-il été délivré à NOTRE client ?
    if conf.expected_azp and claims.azp ~= conf.expected_azp then
        return unauthorized("azp inattendu : " .. tostring(claims.azp))
    end

    -- 5. Headers d'identité vers le backend.
    kong.service.request.set_header(USER_ID_HEADER, claims.sub)
    local roles = business_roles(claims, conf.allowed_roles)
    if roles ~= "" then
        kong.service.request.set_header(ROLES_HEADER, roles)
    end
    -- Sans rôle métier : pas de X_ROLE_ID ; le backend authentifie l'utilisateur
    -- sans autorité et les @PreAuthorize répondront 403.

    -- 6. Le token ne dépasse jamais la gateway.
    kong.service.request.clear_header("Authorization")
end

return IdentityHeadersHandler
