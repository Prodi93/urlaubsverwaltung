#!/usr/bin/env bash
# Switches the Urlaubsverwaltung in this LXC to the LI fork, or updates it to the latest fork release.
# Run as root inside the container:
#
#   bash uv-fork-update.sh              # latest release of the fork
#   bash uv-fork-update.sh li-6.17.0-li.1   # a specific release tag
#
# What it does:
#   1. downloads the release jar of Prodi93/urlaubsverwaltung and verifies its sha256
#   2. stops the service, backs up the database (pg_dump) and the current jar
#   3. replaces the jar, removes the old Malta holiday hack (custom/uv-holidays-li.sh)
#   4. starts the service and waits until /actuator/health is UP
#      (on the first start Liquibase migrates the database, incl. region MALTA -> LIECHTENSTEIN_BANK_HOLIDAYS)
#   5. installs itself as /usr/local/bin/uv-fork-update for later updates
set -euo pipefail

REPO="${UV_REPO:-Prodi93/urlaubsverwaltung}"
TAG="${1:-latest}"
UV_DIR="/opt/urlaubsverwaltung"
JAR="${UV_DIR}/urlaubsverwaltung"
SERVICE="urlaubsverwaltung"
BACKUP_DIR="/opt/urlaubsverwaltung-backup"
HEALTH_URL="http://localhost:8080/actuator/health"
INFO_URL="http://localhost:8080/actuator/info"
STAMP="$(date +%Y%m%d-%H%M%S)"

info() { echo -e "\e[1;34m==>\e[0m $*"; }
ok() { echo -e "\e[1;32m OK\e[0m $*"; }
fail() { echo -e "\e[1;31mERR\e[0m $*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || fail "please run as root"
[[ -f "$JAR" ]] || fail "no installation found at $JAR"
command -v curl >/dev/null || fail "curl is missing"
command -v python3 >/dev/null || fail "python3 is missing"
command -v sha256sum >/dev/null || fail "sha256sum is missing"
java -version 2>&1 | grep -q 'version "25' || fail "java 25 is required (found: $(java -version 2>&1 | head -1))"

# ---------------------------------------------------------------------------------------------
info "Looking up release '${TAG}' of ${REPO}"
if [[ "$TAG" == "latest" ]]; then
  API_URL="https://api.github.com/repos/${REPO}/releases/latest"
else
  API_URL="https://api.github.com/repos/${REPO}/releases/tags/${TAG}"
fi
RELEASE_JSON="$(curl -fsSL -H 'Accept: application/vnd.github+json' "$API_URL")" || fail "release not found: $API_URL"

read -r RELEASE_NAME JAR_URL SHA_URL < <(python3 -c '
import json, sys
release = json.loads(sys.stdin.read())
assets = {asset["name"]: asset["browser_download_url"] for asset in release.get("assets", [])}
jar = next((name for name in assets if name.startswith("urlaubsverwaltung-") and name.endswith(".jar")), None)
if jar is None:
    sys.exit("no jar in release")
print(release.get("tag_name", "?"), assets[jar], assets.get(jar + ".sha256", "-"))
' <<<"$RELEASE_JSON") || fail "release has no jar asset"
ok "release ${RELEASE_NAME}"

CURRENT_VERSION="$(curl -fs "$INFO_URL" 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin).get("app",{}).get("version","?"))' 2>/dev/null || echo "?")"
info "Installed version: ${CURRENT_VERSION}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
info "Downloading $(basename "$JAR_URL")"
curl -fsSL -o "${TMP_DIR}/new.jar" "$JAR_URL"
if [[ "$SHA_URL" != "-" ]]; then
  EXPECTED="$(curl -fsSL "$SHA_URL" | awk '{print $1}')"
  ACTUAL="$(sha256sum "${TMP_DIR}/new.jar" | awk '{print $1}')"
  [[ "$EXPECTED" == "$ACTUAL" ]] || fail "sha256 mismatch (expected $EXPECTED, got $ACTUAL)"
  ok "sha256 verified"
else
  echo "    (no sha256 file in release, skipping verification)"
fi

# ---------------------------------------------------------------------------------------------
info "Stopping service"
systemctl stop "$SERVICE"
ok "stopped"

info "Backing up database and jar to ${BACKUP_DIR}"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
runuser -u postgres -- pg_dump urlaubsverwaltung | gzip >"${BACKUP_DIR}/db-${STAMP}.sql.gz"
cp -p "$JAR" "${BACKUP_DIR}/urlaubsverwaltung-${STAMP}.jar"
# keep the last 5 backups
ls -1t "${BACKUP_DIR}"/db-*.sql.gz 2>/dev/null | tail -n +6 | xargs -r rm -f
ls -1t "${BACKUP_DIR}"/urlaubsverwaltung-*.jar 2>/dev/null | tail -n +6 | xargs -r rm -f
ok "db-${STAMP}.sql.gz, urlaubsverwaltung-${STAMP}.jar"

info "Installing new jar"
install -o urlaubsverwaltung -g urlaubsverwaltung -m 0644 "${TMP_DIR}/new.jar" "$JAR"
ok "installed"

if [[ -d "${UV_DIR}/custom" || -f "${JAR}.bak" ]]; then
  info "Removing the old Malta holiday hack (moved to ${BACKUP_DIR}/legacy-${STAMP})"
  mkdir -p "${BACKUP_DIR}/legacy-${STAMP}"
  [[ -d "${UV_DIR}/custom" ]] && mv "${UV_DIR}/custom" "${BACKUP_DIR}/legacy-${STAMP}/"
  [[ -f "${JAR}.bak" ]] && mv "${JAR}.bak" "${BACKUP_DIR}/legacy-${STAMP}/"
  ok "removed"
fi

# ---------------------------------------------------------------------------------------------
info "Starting service (the first start migrates the database, this can take a few minutes)"
systemctl start "$SERVICE"
HEALTH=""
for i in $(seq 1 120); do
  # any answer of the health endpoint means the application has started (503 = started but not healthy)
  HEALTH="$(curl -s --max-time 5 "$HEALTH_URL" 2>/dev/null || true)"
  [[ "$HEALTH" == *'"status"'* ]] && break
  if ! systemctl is-active --quiet "$SERVICE" && [[ $i -gt 6 ]]; then
    journalctl -u "$SERVICE" -n 80 --no-pager
    fail "service stopped, see the log above. Roll back with the snapshot or the backup in ${BACKUP_DIR}"
  fi
  sleep 5
done
if [[ "$HEALTH" != *'"status"'* ]]; then
  journalctl -u "$SERVICE" -n 80 --no-pager
  fail "service did not start within 10 minutes, see the log above"
fi
if [[ "$HEALTH" == *'"status":"UP"'* ]]; then
  ok "health UP"
else
  echo -e "\e[1;33mWARN\e[0m application started, but health is not UP: ${HEALTH}"
  echo "     (e.g. the mail server is not reachable) - check: curl -s ${HEALTH_URL}"
fi
NEW_VERSION="$(curl -fs "$INFO_URL" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("app",{}).get("version","?"))' 2>/dev/null || echo "?")"
ok "running version ${NEW_VERSION}"

info "Checking public holiday region"
runuser -u postgres -- psql -d urlaubsverwaltung -Atc \
  "SELECT 'settings: ' || coalesce(federal_state, '-') FROM settings
   UNION ALL
   SELECT 'person override: ' || federal_state_override || ' (' || count(*) || ')' FROM working_time
   WHERE federal_state_override IS NOT NULL GROUP BY federal_state_override" | sed 's/^/    /'

if [[ "$(readlink -f "$0" 2>/dev/null)" != "/usr/local/bin/uv-fork-update" && -f "$0" ]]; then
  install -m 0755 "$0" /usr/local/bin/uv-fork-update
  ok "installed as /usr/local/bin/uv-fork-update for later updates"
fi

cat <<EOF

Done: ${CURRENT_VERSION} -> ${NEW_VERSION}

Please check in the browser:
  - Settings > Public holidays: region "Liechtenstein - Liechtenstein (inkl. Bankfeiertage)"
  - calendar: 2.1., Fasnachtsdienstag and Karfreitag are public holidays
  - Settings > Absence types: activate "Teilzeit-Freitag" if needed
  - new menu entries "Anwesenheit" and "Import / Export"

Note: do NOT use the community-scripts 'update' command any more, it installs the upstream release
and drops the fork. Use 'uv-fork-update' instead.

Rollback: restore the Proxmox snapshot, or
  systemctl stop ${SERVICE}
  gunzip -c ${BACKUP_DIR}/db-${STAMP}.sql.gz | runuser -u postgres -- psql -d urlaubsverwaltung   # into an emptied database
  cp ${BACKUP_DIR}/urlaubsverwaltung-${STAMP}.jar ${JAR} && systemctl start ${SERVICE}
EOF
