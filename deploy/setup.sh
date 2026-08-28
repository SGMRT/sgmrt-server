#!/usr/bin/env bash
#
# GhostRunner 서버 1회성 부트스트랩 (멱등). EC2에서 sudo 가능한 사용자로 실행.
#   실행: cd <repo>/deploy && bash setup.sh
#
#   하는 일:
#     - $APP_DIR 생성
#     - systemd 템플릿 유닛 설치
#     - nginx upstream 스니펫 설치
#     - sites-available/default 의 proxy_pass 를 upstream 참조로 한 줄 교체(백업+검증+원복)
#     - ACTIVE_PORT 초기화(8080 = 현재 nohup)
#
#   실행 중 downtime 없음: nginx 는 여전히 8080(현재 nohup)로 라우팅됨.
#   최초 cutover(nohup -> systemd) 는 README 참고 — 첫 배포로 수행.
#
set -euo pipefail

APP_DIR=${APP_DIR:-/home/ubuntu/ghostrunner}
SERVICE_DST=/etc/systemd/system/ghostrunner@.service
NGINX_UPSTREAM=/etc/nginx/conf.d/ghostrunner-upstream.conf
SITE=${SITE:-/etc/nginx/sites-available/default}
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "[setup] APP_DIR=$APP_DIR"
mkdir -p "$APP_DIR"

echo "[setup] systemd 유닛 설치"
sudo cp "$HERE/ghostrunner@.service" "$SERVICE_DST"
sudo systemctl daemon-reload

echo "[setup] nginx upstream 스니펫 설치"
sudo cp "$HERE/nginx-ghostrunner.conf" "$NGINX_UPSTREAM"

echo "[setup] sites-available/default proxy_pass 확인/수정"
BAK=""   # else 분기에서도 참조되므로 미리 초기화 (set -u 대비)
if sudo grep -q 'proxy_pass http://ghostrunner_backend;' "$SITE"; then
  echo "[setup]   이미 upstream 참조로 설정됨 (skip)"
elif sudo grep -q 'proxy_pass http://localhost:8080;' "$SITE"; then
  BAK="${SITE}.bak.$(date +%s)"
  sudo cp "$SITE" "$BAK"
  echo "[setup]   백업: $BAK"
  sudo sed -i 's#proxy_pass http://localhost:8080;#proxy_pass http://ghostrunner_backend;#' "$SITE"
  echo "[setup]   proxy_pass -> http://ghostrunner_backend 로 교체"
else
  echo "[setup]   WARNING: 예상한 'proxy_pass http://localhost:8080;' 라인을 못 찾음."
  echo "[setup]            $SITE 의 location / 안 proxy_pass 를 수동으로 http://ghostrunner_backend; 로 바꾸세요."
fi

echo "[setup] nginx 검증 + reload"
if ! sudo nginx -t; then
  echo "[setup] ERROR: nginx -t 실패."
  if [ -n "$BAK" ]; then
    echo "[setup]        백업으로 원복: sudo cp $BAK $SITE && sudo systemctl reload nginx"
  else
    echo "[setup]        $SITE 및 $NGINX_UPSTREAM 설정을 점검하세요."
  fi
  exit 1
fi
sudo systemctl reload nginx

echo "[setup] ACTIVE_PORT 초기화"
if [ ! -f "$APP_DIR/ACTIVE_PORT" ]; then
  echo 8080 > "$APP_DIR/ACTIVE_PORT"
  echo "[setup]   ACTIVE_PORT=8080 (현재 nohup)"
else
  echo "[setup]   ACTIVE_PORT 이미 존재: $(cat "$APP_DIR/ACTIVE_PORT") (skip)"
fi

cat <<'DONE'

[setup] 완료 ✅
  다음 단계:
    1) GitHub Secrets 6종 + firebase 등록 확인 (README 참고)
    2) 첫 배포는 workflow_dispatch 로 수동 실행 권장
    3) 첫 배포 성공(포트 8081 전환) 후, 기존 nohup 종료로 메모리 확보:
         pkill -f 'ghostrunner-.*SNAPSHOT.jar'
       (이후 두 번째 배포부터는 8080<->8081 정상 토글)
DONE
