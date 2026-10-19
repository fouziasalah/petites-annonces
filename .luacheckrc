-- Configuration de luacheck (lint du plugin Kong).
std = "ngx_lua"
read_globals = { "kong" }
max_line_length = 120
include_files = { "kong/plugins" }
-- Kong appelle les phases sous forme de méthodes (Handler:access(conf)) : le « self »
-- implicite fait partie de la signature imposée, même quand le plugin ne s'en sert pas.
self = false
