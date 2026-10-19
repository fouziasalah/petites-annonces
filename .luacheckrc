-- Configuration de luacheck (lint du plugin Kong).
std = "ngx_lua"
read_globals = { "kong" }
max_line_length = 120
include_files = { "kong/plugins" }
