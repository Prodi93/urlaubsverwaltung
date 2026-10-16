#!/usr/bin/env bash
_cs_boot="${COMMUNITY_SCRIPTS_CORE_DIR:-$(dirname "${BASH_SOURCE[0]}")/../../core}/core/build.func"
source "$_cs_boot" 2>/dev/null || source <(curl -fsSL "${COMMUNITY_SCRIPTS_CORE_URL:-https://raw.githubusercontent.com/community-scripts/core/main}/core/build.func")
# Author: Johannes
# License: MIT | https://github.com/community-scripts/ProxmoxVE/raw/main/LICENSE
# Source: https://urlaubsverwaltung.cloud | Github: https://github.com/urlaubsverwaltung/urlaubsverwaltung
#
# Variante für den LI-Fork: Updates kommen aus den Releases von Prodi93/urlaubsverwaltung statt upstream.
# Das frühere custom/uv-holidays-li.sh entfällt, die liechtensteinischen Feiertage sind im Fork enthalten.

APP="Urlaubsverwaltung"
UV_REPO="${UV_REPO:-Prodi93/urlaubsverwaltung}"
var_tags="${var_tags:-hr;absence}"
var_cpu="${var_cpu:-2}"
var_ram="${var_ram:-2048}"
var_disk="${var_disk:-6}"
var_os="${var_os:-debian}"
var_version="${var_version:-13}"
var_unprivileged="${var_unprivileged:-1}"

header_info "$APP"
variables
color
catch_errors

function update_script() {
  header_info
  check_container_storage
  check_container_resources

  if [[ ! -f /opt/urlaubsverwaltung/urlaubsverwaltung ]]; then
    msg_error "No ${APP} Installation Found!"
    exit
  fi

  JAVA_VERSION="25" setup_java

  if check_for_gh_release "urlaubsverwaltung" "${UV_REPO}"; then
    msg_info "Stopping Service"
    systemctl stop urlaubsverwaltung
    msg_ok "Stopped Service"

    msg_info "Backing up Database and JAR"
    BACKUP_DIR="/opt/urlaubsverwaltung-backup"
    mkdir -p "$BACKUP_DIR"
    STAMP="$(date +%Y%m%d-%H%M%S)"
    sudo -u postgres pg_dump urlaubsverwaltung | gzip >"${BACKUP_DIR}/db-${STAMP}.sql.gz"
    cp /opt/urlaubsverwaltung/urlaubsverwaltung "${BACKUP_DIR}/urlaubsverwaltung-${STAMP}.jar"
    # keep the last 5 backups
    ls -1t "${BACKUP_DIR}"/db-*.sql.gz | tail -n +6 | xargs -r rm -f
    ls -1t "${BACKUP_DIR}"/urlaubsverwaltung-*.jar | tail -n +6 | xargs -r rm -f
    msg_ok "Backed up Database and JAR to ${BACKUP_DIR}"

    # singlefile overwrites only the JAR – application.yaml stays untouched
    fetch_and_deploy_gh_release "urlaubsverwaltung" "${UV_REPO}" "singlefile" "latest" "/opt/urlaubsverwaltung" "urlaubsverwaltung-*.jar"
    chown urlaubsverwaltung:urlaubsverwaltung /opt/urlaubsverwaltung/urlaubsverwaltung

    msg_info "Starting Service"
    systemctl start urlaubsverwaltung
    for _ in $(seq 1 60); do
      if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then
        break
      fi
      sleep 5
    done
    if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then
      msg_ok "Started Service"
      msg_ok "Updated successfully!"
    else
      msg_error "Service not healthy after update – check 'journalctl -u urlaubsverwaltung'. Rollback: restore ${BACKUP_DIR}/db-${STAMP}.sql.gz and ${BACKUP_DIR}/urlaubsverwaltung-${STAMP}.jar"
    fi
  fi
  exit
}

start
build_container
description

msg_ok "Completed successfully!\n"
echo -e "${CREATING}${GN}${APP} setup has been successfully initialized!${CL}"
echo -e "${INFO}${YW}Access it using the following URL:${CL}"
echo -e "${GATEWAY}${BGN}http://${IP}:8080${CL}"
echo -e "${INFO}${YW}Config: /opt/urlaubsverwaltung/application.yaml (service needs valid OIDC settings to start)${CL}"
