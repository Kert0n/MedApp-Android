#!/usr/bin/env bash
# Заводит двух пробных пользователей ContractProbe на сервере из local.properties — один раз.
#
# Учётки дописываются в local.properties и на экран не выводятся: ключ выдаётся сервером
# единожды, а файл в git не попадает (PLAN G, AGENTS «Связь с сервером»). Уже заведённого
# пользователя скрипт не трогает, поэтому повторный запуск новых учёток не плодит.
set -euo pipefail
cd "$(dirname "$0")/.."

props=local.properties
prop() { grep -E "^$1=" "$props" | head -1 | cut -d= -f2- || true; }
field() { python3 -c "import json, sys; print(json.load(sys.stdin)['$1'])"; }

base=$(prop MEDAPP_BASE_URL)
base=${base:-https://medapp.ru.net}
token=$(prop MEDAPP_REGISTRATION_TOKEN)
if [ -z "$token" ]; then
    echo "MEDAPP_REGISTRATION_TOKEN не задан в $props" >&2
    exit 1
fi

# Дописываемая строка не должна прилипнуть к последней строке файла.
[ -n "$(tail -c1 "$props")" ] && echo >> "$props"

for user in A B; do
    if [ -n "$(prop "MEDAPP_PROBE_${user}_LOGIN")" ]; then
        echo "Пробный пользователь $user уже заведён."
        continue
    fi
    # Токен уходит curl через stdin, а не аргументом: аргументы видны в списке процессов
    # любому, кто в этот момент смотрит, а токен открывает регистрацию.
    body=$(printf 'header = "X-Registration-Token: %s"\n' "$token" |
        curl -sS --fail-with-body -X POST "$base/v1/auth/register" --config -)
    login=$(printf '%s' "$body" | field login)
    key=$(printf '%s' "$body" | field key)
    printf 'MEDAPP_PROBE_%s_LOGIN=%s\nMEDAPP_PROBE_%s_KEY=%s\n' \
        "$user" "$login" "$user" "$key" >> "$props"
    echo "Пробный пользователь $user заведён на $base."
done
