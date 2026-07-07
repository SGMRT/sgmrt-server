#!/usr/bin/env bash
#
# GhostRunner Blue-Green 배포 스크립트 (EC2 서버에서 실행. CI가 SSH로 호출)
#
#   2단계 배포: switch → (CI가 외부 health 확인) → finalize | rollback
#     switch    (기본) 신버전을 idle 슬롯에 올리고 nginx 전환. 구버전은 유지(PENDING/MODE 기록).
#     finalize  외부 health 통과 후: 구버전 graceful drain + ACTIVE_PORT 확정.
#     rollback  외부 health 실패 시: nginx 원복 + 신버전 정리(구버전은 계속 서빙).
#
#   견고성:
#     - flock 으로 동시 실행 차단 (CI + 수동 조작 경합 방지)
#     - switch 진입 시 stale PENDING(이전 미완결 배포) 을 nginx 실제 라우팅 기준으로 자동 정리
#     - 상태(ACTIVE_PORT/PENDING_PORT/DEPLOY_MODE) 파일 + 포트 화이트리스트 검증
#     - health/게이트는 http 200 + 본문 "status":"UP" 확인. liveness 미노출(404) 시 root health 폴백
#
#   환경변수(선택):
#     STRATEGY=bluegreen|restart   HEALTH_TIMEOUT=120
#     GATE_RETRIES=5  GATE_INTERVAL=3  MEM_GUARD_MB=350  DISK_GUARD_MB=500
#
set -euo pipefail

APP_DIR=${APP_DIR:-/home/ubuntu/ghostrunner}
SERVICE=${SERVICE:-ghostrunner}
DOMAIN=${DOMAIN:-api.ghostrun.io}
NGINX_UPSTREAM_CONF=${NGINX_UPSTREAM_CONF:-/etc/nginx/conf.d/ghostrunner-upstream.conf}
STRATEGY=${STRATEGY:-bluegreen}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-120}
HEALTH_INTERVAL=${HEALTH_INTERVAL:-3}
GATE_RETRIES=${GATE_RETRIES:-5}
GATE_INTERVAL=${GATE_INTERVAL:-3}
MEM_GUARD_MB=${MEM_GUARD_MB:-350}
DISK_GUARD_MB=${DISK_GUARD_MB:-500}
PORT_A=8080
PORT_B=8081
ACTIVE_FILE="$APP_DIR/ACTIVE_PORT"
PENDING_FILE="$APP_DIR/PENDING_PORT"
MODE_FILE="$APP_DIR/DEPLOY_MODE"
LOCK_FILE="$APP_DIR/.deploy.lock"

CMD="${1:-switch}"
LAST_CODE=""

log() { echo "[deploy:$CMD $(date +%H:%M:%S)] $*"; }
die() { echo "[deploy:$CMD $(date +%H:%M:%S)] ERROR: $*" >&2; exit 1; }

# 동시 실행 차단 (비블로킹). switch/finalize/rollback/수동 조작이 겹치면 상태파일 레이스.
mkdir -p "$APP_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || die "다른 배포/조작이 진행 중(lock). 중단."

valid_port() { case "$1" in 8080|8081) return 0 ;; *) return 1 ;; esac; }

active_port() {
  local p; p=$(cat "$ACTIVE_FILE" 2>/dev/null || echo "")
  valid_port "$p" && echo "$p" || echo "$PORT_A"
}
pending_port() { cat "$PENDING_FILE" 2>/dev/null || echo ""; }
deploy_mode()  { cat "$MODE_FILE" 2>/dev/null || echo "bluegreen"; }
idle_of()      { [ "$1" = "$PORT_A" ] && echo "$PORT_B" || echo "$PORT_A"; }
avail_mb()     { free -m | awk '/^Mem:/{print $7}'; }
disk_avail_mb() { df -Pm "$APP_DIR" | awk 'NR==2{print $4}'; }

# nginx 가 '실제로' 가리키는 포트(진실 원천). upstream conf 에서 파싱.
nginx_current_port() {
  grep -oE '127\.0\.0\.1:[0-9]+' "$NGINX_UPSTREAM_CONF" 2>/dev/null | grep -oE '[0-9]+$' | head -1
}

# http 200 + 본문 "status":"UP" 확인. LAST_CODE 에 http 코드 기록.
check_up() {
  local out code body
  out=$(curl -sk -m 5 -H "Host: $DOMAIN" -w $'\n%{http_code}' "$1" 2>/dev/null || true)
  code=${out##*$'\n'}; body=${out%$'\n'*}
  LAST_CODE=$code
  [ "$code" = "200" ] && printf '%s' "$body" | grep -q '"status":"UP"'
}

# 전환 '전' 검증: 앱 전체 헬스(DB/Redis 포함). 트래픽 넘기기 전 의존성 준비 확인.
health_full() { check_up "http://127.0.0.1:$1/actuator/health"; }
wait_health_full() {
  local port=$1 elapsed=0
  while [ "$elapsed" -lt "$HEALTH_TIMEOUT" ]; do
    if health_full "$port"; then log "port $port full-health UP (${elapsed}s)"; return 0; fi
    sleep "$HEALTH_INTERVAL"; elapsed=$((elapsed + HEALTH_INTERVAL))
  done
  return 1
}

# 전환 '후' 라우팅 게이트: liveness(앱 자체) + 재시도. liveness 미노출(404) 시 root health 폴백(H3).
gate_through_nginx() {
  local i
  for i in $(seq 1 "$GATE_RETRIES"); do
    if check_up "https://127.0.0.1/actuator/health/liveness"; then return 0; fi
    if [ "$LAST_CODE" = "404" ]; then
      log "liveness 미노출(404) → root health 폴백"
      check_up "https://127.0.0.1/actuator/health" && return 0
    fi
    log "게이트 시도 $i/$GATE_RETRIES 실패 (http=$LAST_CODE)"
    sleep "$GATE_INTERVAL"
  done
  return 1
}

# nginx upstream 을 지정 포트로 재작성 + 검증 + reload. 백업/원복으로 디스크풀·부분쓰기 방어(M3).
set_upstream() {
  local port=$1 bak="${NGINX_UPSTREAM_CONF}.bak.$$"
  sudo cp -f "$NGINX_UPSTREAM_CONF" "$bak" 2>/dev/null || true
  if ! echo "upstream ghostrunner_backend { server 127.0.0.1:${port}; }" | sudo tee "$NGINX_UPSTREAM_CONF" >/dev/null; then
    [ -f "$bak" ] && sudo mv -f "$bak" "$NGINX_UPSTREAM_CONF"
    return 1
  fi
  if ! sudo nginx -t >/dev/null 2>&1; then
    [ -f "$bak" ] && sudo mv -f "$bak" "$NGINX_UPSTREAM_CONF"
    return 1
  fi
  sudo rm -f "$bak" 2>/dev/null || true
  sudo systemctl reload nginx || return 1
  log "nginx upstream -> 127.0.0.1:${port}"
}

dump_logs() {
  log "----- journalctl ${SERVICE}@$1 (tail) -----"
  sudo journalctl -u "${SERVICE}@$1" -n 50 --no-pager 2>/dev/null || true
}

warn_if_port_busy() {
  if ss -ltn 2>/dev/null | grep -q ":$1 "; then
    log "WARNING: 포트 $1 에 리스너 잔존(최초 cutover 시 기존 nohup 추정)."
    log "         메모리 확보: pkill -f 'ghostrunner-.*SNAPSHOT.jar'  (또는 해당 PID kill)"
  fi
}

# switch 진입 시: 이전 배포가 finalize/rollback 없이 멈춘(stale PENDING) 상태를
# nginx 의 실제 라우팅을 진실로 삼아 정리한다(F1).
reconcile_stale_pending() {
  local prev ngx old
  prev=$(pending_port); [ -n "$prev" ] || return 0
  ngx=$(nginx_current_port); old=$(active_port)
  log "stale PENDING=$prev 감지(이전 배포 미완결). nginx 라우팅=${ngx:-?} 기준 정리."
  if [ "$ngx" = "$prev" ]; then
    # nginx 가 신버전을 서빙 중 → 사실상 성공했던 배포. 승격(finalize 상당).
    if [ -n "$old" ] && [ "$old" != "$prev" ]; then
      log "  → 구버전 $old 정리, ACTIVE_PORT=$prev 승격"
      sudo systemctl stop "${SERVICE}@${old}" || log "  WARNING: 구버전 $old stop 실패"
      warn_if_port_busy "$old"
    fi
    echo "$prev" > "$ACTIVE_FILE"
  else
    # nginx 가 구버전 유지 → 신버전은 orphan. 정리(rollback 상당). ACTIVE_FILE 유지.
    log "  → orphan 신버전 $prev 정리(nginx 는 구버전 유지)"
    sudo systemctl stop "${SERVICE}@${prev}" || log "  WARNING: orphan $prev stop 실패"
  fi
  rm -f "$PENDING_FILE" "$MODE_FILE"
  rm -f "$APP_DIR"/app-*.jar.prev 2>/dev/null || true
}

# =========================================================================
# switch
# =========================================================================
cmd_switch() {
  [ -f "$APP_DIR/staging.jar" ] || die "staging.jar 없음 ($APP_DIR/staging.jar). CI 업로드 확인."

  local DISK; DISK=$(disk_avail_mb)
  [ "$DISK" -ge "$DISK_GUARD_MB" ] || die "디스크 여유 ${DISK}MB < ${DISK_GUARD_MB}MB. 배포 중단."

  reconcile_stale_pending

  local ACTIVE IDLE
  ACTIVE=$(active_port); IDLE=$(idle_of "$ACTIVE")
  log "strategy=$STRATEGY  active=$ACTIVE  idle=$IDLE  (disk ${DISK}MB)"

  if [ "$STRATEGY" = "restart" ]; then
    switch_restart "$ACTIVE"; return
  fi

  # ---- bluegreen ----
  local AVAIL; AVAIL=$(avail_mb)
  [ "$AVAIL" -ge "$MEM_GUARD_MB" ] \
    || die "available ${AVAIL}MB < guard ${MEM_GUARD_MB}MB. 메모리 부족 중단(STRATEGY=restart 폴백/증설 검토)."
  log "메모리 가드 통과: available ${AVAIL}MB"

  mv -f "$APP_DIR/staging.jar" "$APP_DIR/app-${IDLE}.jar"
  log "idle 포트 $IDLE 기동..."
  sudo systemctl restart "${SERVICE}@${IDLE}"

  if ! wait_health_full "$IDLE"; then
    dump_logs "$IDLE"
    sudo systemctl stop "${SERVICE}@${IDLE}" || true
    die "신규($IDLE) health 실패(http=$LAST_CODE). 구버전($ACTIVE) 그대로 서빙(다운타임 없음)."
  fi

  if ! set_upstream "$IDLE"; then
    sudo systemctl stop "${SERVICE}@${IDLE}" || true
    die "nginx 전환 실패. 구버전($ACTIVE) 그대로 서빙."
  fi

  if ! gate_through_nginx; then
    log "through-nginx 게이트 실패(http=$LAST_CODE) → $ACTIVE 로 롤백 시도"
    if ! set_upstream "$ACTIVE"; then
      die "CRITICAL: 롤백(nginx->$ACTIVE) 실패. 수동 개입 필요. IDLE($IDLE)는 stop하지 않음."
    fi
    sudo systemctl stop "${SERVICE}@${IDLE}" || log "WARNING: IDLE($IDLE) stop 실패"
    dump_logs "$IDLE"
    die "전환 후 게이트 실패 → $ACTIVE 롤백 완료(구버전 서빙, 다운타임 없음)."
  fi

  echo "bluegreen" > "$MODE_FILE"
  echo "$IDLE" > "$PENDING_FILE"
  log "switch 완료: nginx->$IDLE, 구버전 $ACTIVE 유지. (CI 외부 health -> finalize/rollback)"
}

# restart 폴백: 현재 포트 인플레이스 교체(다운타임 있음). .prev 백업으로 롤백 가능(B3).
switch_restart() {
  local ACTIVE=$1
  log "restart 모드: 포트 $ACTIVE 인플레이스 교체(수 초 다운타임)"
  if [ -f "$APP_DIR/app-${ACTIVE}.jar" ]; then
    cp -f "$APP_DIR/app-${ACTIVE}.jar" "$APP_DIR/app-${ACTIVE}.jar.prev"
    log "구 jar 백업: app-${ACTIVE}.jar.prev"
  fi
  mv -f "$APP_DIR/staging.jar" "$APP_DIR/app-${ACTIVE}.jar"
  sudo systemctl restart "${SERVICE}@${ACTIVE}"
  if ! wait_health_full "$ACTIVE"; then
    dump_logs "$ACTIVE"
    if [ -f "$APP_DIR/app-${ACTIVE}.jar.prev" ]; then
      log "복구: .prev 로 원복 후 재기동"
      mv -f "$APP_DIR/app-${ACTIVE}.jar.prev" "$APP_DIR/app-${ACTIVE}.jar"
      sudo systemctl restart "${SERVICE}@${ACTIVE}" || true
    fi
    die "restart 후 health 실패(http=$LAST_CODE). 구 jar 로 원복 시도함."
  fi
  set_upstream "$ACTIVE" || log "WARNING: set_upstream 실패(같은 포트라 영향 적음)"
  echo "restart" > "$MODE_FILE"
  echo "$ACTIVE" > "$PENDING_FILE"
  log "restart switch 완료: 포트 $ACTIVE (CI 외부 health -> finalize/rollback)"
}

# =========================================================================
# finalize (외부 health 통과)
# =========================================================================
cmd_finalize() {
  local PENDING ACTIVE MODE
  PENDING=$(pending_port); [ -n "$PENDING" ] || die "PENDING 없음. 먼저 switch 필요."
  ACTIVE=$(active_port); MODE=$(deploy_mode)

  if [ "$MODE" = "restart" ]; then
    rm -f "$APP_DIR/app-${ACTIVE}.jar.prev"
  else
    log "구버전 $ACTIVE graceful drain..."
    sudo systemctl stop "${SERVICE}@${ACTIVE}" || log "WARNING: 구버전($ACTIVE) stop 실패"
    warn_if_port_busy "$ACTIVE"
  fi

  echo "$PENDING" > "$ACTIVE_FILE"
  rm -f "$PENDING_FILE" "$MODE_FILE"
  log "✅ finalize 완료: ACTIVE_PORT=$PENDING (mode=$MODE)"
}

# =========================================================================
# rollback (외부 health 실패)
# =========================================================================
cmd_rollback() {
  local PENDING ACTIVE MODE
  PENDING=$(pending_port); [ -n "$PENDING" ] || die "PENDING 없음. 롤백 대상 없음(switch 미수행?)."
  ACTIVE=$(active_port); MODE=$(deploy_mode)

  if [ "$MODE" = "restart" ]; then
    if [ -f "$APP_DIR/app-${ACTIVE}.jar.prev" ]; then
      log "restart rollback: .prev 원복 후 재기동"
      mv -f "$APP_DIR/app-${ACTIVE}.jar.prev" "$APP_DIR/app-${ACTIVE}.jar"
      sudo systemctl restart "${SERVICE}@${ACTIVE}" || true
      wait_health_full "$ACTIVE" || log "WARNING: 원복 후 health 실패(http=$LAST_CODE) — 수동 점검"
    else
      log "WARNING: .prev 백업 없음 — 원복할 구 jar 없음"
    fi
    set_upstream "$ACTIVE" || die "CRITICAL: rollback nginx 실패. 수동 개입 필요."
  else
    # bluegreen: nginx 를 구버전으로 되돌리고 idle 정리. 롤백 nginx 실패는 치명(C1).
    if ! set_upstream "$ACTIVE"; then
      die "CRITICAL: rollback(nginx->$ACTIVE) 실패. 수동 개입 필요. IDLE($PENDING) stop하지 않음."
    fi
    sudo systemctl stop "${SERVICE}@${PENDING}" || log "WARNING: IDLE($PENDING) stop 실패"
  fi

  rm -f "$PENDING_FILE" "$MODE_FILE"
  log "rollback 완료 → 구버전 $ACTIVE 서빙"
}

# =========================================================================
case "$CMD" in
  switch)   cmd_switch ;;
  finalize) cmd_finalize ;;
  rollback) cmd_rollback ;;
  *) die "알 수 없는 커맨드: $CMD (switch|finalize|rollback)" ;;
esac
