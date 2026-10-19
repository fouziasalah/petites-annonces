-- =============================================================================
-- Schéma de configuration du plugin identity-headers.
-- Kong valide la section « config » de kong.yml contre ce schéma au démarrage :
-- une faute de frappe dans la config fait échouer le démarrage, au lieu d'un
-- comportement silencieusement faux.
-- =============================================================================
local typedefs = require "kong.db.schema.typedefs"

return {
    name = "identity-headers",
    fields = {
        -- Le plugin n'a de sens que sur du trafic HTTP(S).
        { protocols = typedefs.protocols_http },
        -- Il s'applique à une route/un service, pas à un consumer particulier.
        { consumer = typedefs.no_consumer },
        { config = {
            type = "record",
            fields = {
                -- Rôles métier transmis au backend. Les autres rôles du token
                -- (default-roles-annonces, offline_access…) sont ignorés.
                { allowed_roles = {
                    type = "set",
                    elements = { type = "string" },
                    required = true,
                    default = { "USER", "MODERATEUR", "ADMIN" },
                } },
                -- Si renseigné, le claim « azp » (client qui a obtenu le token)
                -- doit avoir cette valeur. Absent = pas de vérification.
                { expected_azp = {
                    type = "string",
                    required = false,
                } },
            },
        } },
    },
}
