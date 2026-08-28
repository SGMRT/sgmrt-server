# GhostRunner CI/CD 파이프라인 독립 검증 리포트 R2 (2단계 리팩터)

> 검증자: 독립 리뷰어 (작업 미수행, 적대적 케이스 분석). 근거는 모두 `파일:라인`.
> 대상 브랜치: `claude/zealous-goodall-4822c2` (base `dev`). 읽기 전용(파일 정독 + grep + git).
> 초점: (1) 2단계 리팩터(switch→외부health→finalize/rollback)에 새로 생긴 결함, (2) 이전 Blocker(C1/B1/B2/B3/B9a) 해소 여부, (3) 남은 취약점.

---

## 요약 판정

**조건부 통과 (Conditional Pass) — 이전보다 개선되었으나, 2단계 리팩터가 새로운 Critical 결함 1건을 도입했다.**

핵심 3줄:
1. **(Critical, 신규)** `switch` 성공(PENDING 기록) 후 CI 러너가 죽거나 네트워크가 끊기면 finalize/rollback이 절대 호출되지 않는다. **stale PENDING이 남고, 다음 배포의 mode 추론이 `PENDING==ACTIVE` 비교에 의존하는데 이 비교는 다음 배포 시작 시점에는 참조되지 않아** 오염된 상태로 구버전+신버전이 동시에 nginx 뒤에 어정쩡하게 남거나 잘못된 슬롯을 덮어쓸 수 있다. stale PENDING을 감지·정리하는 로직이 어디에도 없다.
2. **(High, 신규)** 외부 health 게이트가 `%{http_code} == 200`만 본다(`dev-cd.yml:96-97`). nginx가 유지보수 페이지/리다이렉트/캐시된 정적 200을 반환하면 죽은 앱에도 finalize가 실행된다. 또한 12회×5s=60s는 신규 인스턴스 warming과 별개로 짧을 수 있다.
3. **(High, 신규)** liveness 게이트(`/actuator/health/liveness`) 노출이 `application-prod.yml`(시크릿)의 `exposure.include`에 **health가 포함돼야만** 동작하는데, 커밋된 `application.yml`은 `probes.enabled=true`만 설정하고 exposure는 시크릿에 있어 리포에서 검증 불가. exposure에서 health가 빠지거나 그룹 미노출이면 게이트가 **404를 받아 조용히 실패→불필요한 롤백**, 혹은 (curl 실패를 재시도로 흡수) 배포 지연.

### 심각도별 발견 개수

| 심각도 | 개수 |
|---|---|
| Critical | 1 |
| High | 4 |
| Medium | 5 |
| Low | 3 |
| Info | 2 |

---

## 1. 이전 Blocker 해소 여부 판정

| ID | 이전 지적 | 판정 | 근거 |
|---|---|---|---|
| **C1** | 롤백 중 `set_upstream "$ACTIVE"` 실패가 조용히 무시됨 | ✅ **해소** | `deploy.sh:128-130`(switch 내 롤백), `:210-212`(rollback 커맨드) 모두 `if ! set_upstream` → `die "CRITICAL..."` + IDLE 보존. 더 이상 거짓 "롤백 완료" 없음 |
| **B1** | 외부 health가 진짜 게이트가 아님 → 신규 인스턴스 leak | ✅ **해소(설계상)**, ⚠️ **새 사각지대** | 2단계로 분리됨(`dev-cd.yml:80-112`). 외부 200 통과해야 finalize. **단** switch 후 러너 사망 시 PENDING leak(신규 F1 참조), 외부 200 오탐(H2 참조)이 새 리스크로 대체됨 |
| **B2** | health 게이트가 DB/Redis 집계 헬스 의존 → 블립 오탐 | ✅ **부분 해소** | 전환 전 `health_full`(`:46`, 의존성 포함, 정당), 전환 후 `gate_through_nginx`가 `/actuator/health/liveness`(`:61`, 의존성 제외). 설계 의도 정확. **단** liveness 노출이 시크릿 exposure에 의존해 리포에서 미검증(H3 참조) |
| **B3** | restart 폴백이 구 jar 소실로 복구 불가 | ✅ **해소** | `switch_restart`(`:145-147`)에서 `cp -f ...jar.prev` 백업 후 교체, 실패 시 `:153-157` 원복. `cmd_rollback`(`:199-206`)도 `.prev` 원복. `cmd_finalize`(`:175`)가 `.prev` 정리 |
| **B9a** | `setup.sh:50` else 분기에서 `$BAK` 미정의 | ✅ **해소** | `setup.sh:35` `BAK=""` 선초기화, `:52` `if [ -n "$BAK" ]` 가드. 안전 |

**소결**: 이전 Blocker 5건은 모두 해소 또는 부분 해소. 그러나 2단계 리팩터가 상태머신 관점에서 **새로운 Critical(F1)** 을 도입했다.

---

## 2. 신규/잔존 결함 (적대적 케이스별)

### F1. (Critical, 신규) switch 후 러너 사망 → stale PENDING이 감지/정리되지 않음
**케이스/재현**:
1. `switch` 성공 → `deploy.sh:137` `echo "$IDLE" > PENDING_FILE`. 이 시점 nginx는 이미 IDLE(신버전)로 전환됨(`:120`), 구버전 ACTIVE는 아직 살아있음.
2. CI 러너가 이 직후 죽거나(`dev-cd.yml:88-91` 이후) SSH가 끊기면 **finalize도 rollback도 호출되지 않음**. 서버에는 `ACTIVE_PORT=8080`, `PENDING_PORT=8081`이 남고, nginx는 8081을 가리키며, 8080/8081 둘 다 systemd로 떠 있음(구버전 8080은 drain 안 됨).
3. **다음 배포**가 오면 `cmd_switch`(`:95`)는 `ACTIVE=$(active_port)` = **여전히 8080**을 읽는다(PENDING은 무시). `IDLE=idle_of(8080)=8081`. 즉 **현재 nginx가 실제 서빙 중인 8081(신버전)** 을 idle로 간주하고 `mv staging.jar app-8081.jar`(`:108`) → `systemctl restart ghostrunner@8081`(`:110`)로 **서빙 중인 인스턴스를 재시작**한다 → **다운타임 발생**(무중단 약속 위반). 게다가 8080(구버전)은 여전히 살아있어 메모리 겹침이 정리 안 된 상태에서 3중 상황이 될 수 있다.

**근거**: `deploy.sh:40`(`active_port`가 PENDING을 보지 않음), `:95`, `:108-110`, `:137`. stale PENDING 감지 로직은 `cmd_switch` 어디에도 없음(grep 확인).

**권장**: `cmd_switch` 진입부에서 `pending_port`가 비어있지 않으면 (a) 이전 배포가 미완결이라 판단하고 die로 중단하거나, (b) `PENDING==현재 nginx 라우팅`이면 그 PENDING을 새 ACTIVE로 승격(자동 finalize)한 뒤 진행. 최소한 stale PENDING 경고+중단.

---

### F2. (High, 신규) 동시성 — GitHub concurrency는 잡만 막고 서버측 상태 레이스는 무방비
**케이스**: `concurrency: dev-deploy`(`dev-cd.yml:10-12`)는 GitHub Actions 잡 중복만 직렬화한다. 그러나 (a) 운영자가 서버에서 수동으로 `deploy.sh`/`finalize`/`rollback`을 돌리거나(README §5가 명시적으로 권장, `README.md:135-150`), (b) `workflow_dispatch` 수동 실행과 `push:dev` 자동 실행이 서로 다른 워크플로우 run이 아닌 이상 겹칠 수 있다. `ACTIVE_PORT`/`PENDING_PORT` 파일 접근에 락(flock 등)이 없어 두 주체가 동시에 `cat`/`echo`하면 상태가 찢어진다.

**근거**: `deploy.sh` 전체에 flock/lockfile 부재. `README.md:149-150`이 수동 finalize/rollback을 권장 → CI와 경합 창.

**권장**: `deploy.sh` 진입부에 `flock $APP_DIR/.deploy.lock` (비블로킹, 획득 실패 시 die). 최소한 README에 "CI 배포 중 수동 조작 금지" 경고.

---

### H2. (High, 신규) 외부 health 200 오탐 — HTML/리다이렉트/캐시 200으로 죽은 앱 finalize
**케이스**: `dev-cd.yml:96` `curl -s -o /dev/null -w "%{http_code}"`는 **본문을 버리고 상태코드만** 본다. 신버전 앱이 죽어 nginx가 502를 내야 하지만, 만약 nginx에 `error_page`/유지보수 정적 페이지/캐시가 걸려 있거나 `/actuator/health`가 리다이렉트(3xx가 아니라 어떤 프록시가 200 HTML을 반환)하면 200으로 판정 → `finalize` 실행 → **구버전 drain**. 실제로는 앱이 죽었는데 무중단 약속이 깨진다.
- `%{http_code}`는 최종 응답 코드라 리다이렉트 자체는 따라가지 않으면 3xx로 잡히지만, nginx가 200으로 자체 응답하는 케이스는 못 거른다.

**근거**: `dev-cd.yml:96-97`. 본문/`{"status":"UP"}` 검증 없음.

**권장**: `curl -s "$HEALTH_URL"`로 본문을 받아 `grep -q '"status":"UP"'`까지 확인. 또는 `--fail`로 4xx/5xx만 거르지 말고 body assert 추가.

---

### H3. (High) liveness 게이트 노출이 시크릿 exposure에 의존 — 리포에서 검증 불가, 미노출 시 조용한 오작동
**케이스**: `gate_through_nginx`(`deploy.sh:61`)는 `/actuator/health/liveness`를 호출한다. Spring Boot에서 이 경로가 200을 주려면 **(1)** `management.endpoint.health.probes.enabled=true`(커밋됨, `application.yml:9-10`) **그리고 (2)** `management.endpoints.web.exposure.include`에 `health`가 포함돼야 한다. exposure는 커밋된 yml에 없고(`grep` 확인: "none in committed yml") `application-prod.yml`(=`APP_PROD_YML` 시크릿, PLAN.md:57에 `"prometheus,health,metrics"`로 기재)에 있다.
- 시크릿의 exposure에서 health가 빠지거나 오타가 나면 `/actuator/health/liveness`는 **404**. `curl -skf`(`:61`)는 404를 실패로 처리 → `GATE_RETRIES`(5회) 소진 후 `gate_through_nginx` 실패 → **불필요한 롤백**(정상 앱인데 롤백). 리포만으로는 이 리스크를 검증할 수 없다(시크릿 불투명).
- 반대 위험: `-skf`가 실패를 조용히 삼켜(`>/dev/null 2>&1`) 왜 실패했는지(404 vs 진짜 down) 구분 불가.

**근거**: `application.yml:6-10`(probes만), `src/main/resources`에 exposure 부재(grep), `deploy.sh:61`, PLAN.md:57.

**권장**: liveness 노출을 시크릿에 맡기지 말고 **커밋된 `application.yml`에 `management.endpoints.web.exposure.include`를 명시**(비민감)하거나, setup/README에 "APP_PROD_YML exposure에 health 필수" 체크리스트. 게이트 실패 시 http_code를 로그로 남겨 404/503 구분.

---

### H4. (High) 최초 cutover nohup 자동 종료 없음 — 여전히 문서 의존 (B7 미해소)
**케이스**: `setup.sh`는 `ACTIVE_PORT=8080`(nohup)으로 초기화(`:63`). 첫 배포 switch는 idle=8081에 올리고 nginx 전환, `warn_if_port_busy`(`deploy.sh:82-87`)는 **경고만**. finalize는 `systemctl stop ghostrunner@8080`(`:179`)을 실행하지만 8080은 systemd가 아닌 nohup이라 **no-op**. 즉 nohup은 계속 8080을 점유한다.
- 운영자가 README §3(`README.md:103-109`)의 `pkill`을 건너뛰면, **두 번째 배포**에서 active=8081→idle=8080 타깃으로 `systemctl restart ghostrunner@8080`(`:110`)이 nohup이 점유한 포트에 바인딩 시도 → `Address already in use`로 기동 실패 → `wait_health_full` 타임아웃(최대 120s) → 중단. 다운타임은 없지만 배포 실패.

**근거**: `deploy.sh:82-87`(경고만), `:179`(no-op stop), `README.md:109`(알려진 함정 문서화).

**판정**: **B7 미해소**(문서 방어만 유지). 리팩터가 이 부분은 건드리지 않음.

**권장**: finalize에서 구 ACTIVE stop 후 `warn_if_port_busy` 대신, 최초 cutover 한정으로 nohup PID를 안전조건(jar명 매칭) 하에 자동 kill 옵션 제공. 또는 setup.sh에 `--kill-nohup` 플래그.

---

### M1. (Medium) finalize/rollback의 mode 추론(`PENDING==ACTIVE`)이 상태파일 손상/부재 시 오판
**케이스**: `cmd_finalize`(`deploy.sh:173`)와 `cmd_rollback`(`:197`)은 `PENDING==ACTIVE`로 restart(in-place) vs bluegreen을 구분한다.
- **최초 cutover**: switch가 active=8080, idle=8081 → PENDING=8081. finalize에서 PENDING(8081)≠ACTIVE(8080) → bluegreen 분기 → `systemctl stop @8080`(nohup, no-op) → `ACTIVE_PORT=8081`. **정상 동작**(우연히 맞음).
- **상태파일 부재/손상**: `active_port`(`:40`)는 파일 없으면 `PORT_A=8080` 폴백. `pending_port`(`:41`)는 없으면 빈 문자열 → finalize/rollback은 `die "PENDING 없음"`(`:170`,`:195`). 손상돼 8080 이외 쓰레기 값이면 `idle_of`가 `PORT_A` 반환 등 예측 불가. 상태파일 무결성 검증(숫자·8080/8081 화이트리스트) 없음.
- **restart 모드에서 ACTIVE 파일이 우연히 PENDING과 달라지면**(F2 레이스로 ACTIVE_PORT가 토글되면) restart로 올린 걸 bluegreen으로 오판해 살아있는 포트를 stop.

**근거**: `deploy.sh:40-41`(폴백만, 검증 없음), `:173`, `:197`.

**권장**: 상태파일 값 검증(`[[ "$P" =~ ^(8080|8081)$ ]]`). mode를 추론하지 말고 switch가 `PENDING_FILE` 옆에 `MODE` 파일을 명시적으로 기록.

---

### M2. (Medium) switch 실패 시점별 staging.jar/idle 슬롯 상태
**케이스**: `mv -f staging.jar app-${IDLE}.jar`(`:108`) 후 `systemctl restart`(`:110`) 실패 → `wait_health_full` 실패 → `:115` idle stop + die. 이때 **app-${IDLE}.jar는 신버전으로 덮어써진 채** 남는다(구 idle jar 소실). bluegreen이라 서빙 중인 ACTIVE는 무사하므로 다운타임은 없다. 그러나 이 idle 슬롯의 "구버전 jar"는 사라져 **수동 롤백(README:138 `systemctl start ghostrunner@$PREV`) 시 신버전 jar가 기동**된다 → 롤백이 사실상 무의미. `.prev` 백업은 restart 모드에만 있고 bluegreen idle 슬롯엔 없음.

**근거**: `deploy.sh:108`(mv -f, 백업 없음), `README.md:138`.

**권장**: bluegreen에서도 `mv` 전에 `cp app-${IDLE}.jar app-${IDLE}.jar.prev` 백업(디스크 여유 시). 또는 README 수동 롤백에서 "idle jar는 직전 배포 산출물일 수 있음" 경고.

---

### M3. (Medium) 디스크 풀/`ss`·`jq` 부재 가정 미방어
**케이스**:
- **디스크 풀**: `mv staging.jar`(`:108`,`:149`), `tee` upstream conf(`:71`)가 디스크 풀 시 실패. `set -e`로 죽지만, `tee` 실패 후 upstream conf가 **빈 파일/절반**이 되면 `nginx -t`(`:72`) 실패 → 롤백 경로로. 배포 전 `df` 가드 없음(D1 미해소).
- **`ss` 부재**: `warn_if_port_busy`(`:83`)는 `ss ... 2>/dev/null`로 없으면 조용히 통과 → 최초 cutover 경고 소실. 치명은 아님.
- **`jq` 부재**: deploy.sh는 jq 미사용(OK). 러너의 Discord 알림(`dev-cd.yml:144,156`)만 jq 사용 → 러너엔 기본 설치라 OK.

**근거**: `deploy.sh:108,149,71,83`. df 가드 부재(grep 확인).

**권장**: switch 진입부 `df --output=avail` 1줄 가드. upstream conf는 임시파일 쓰고 `mv`로 원자적 교체.

---

### M4. (Medium) `vars.DEPLOY_STRATEGY`가 switch에만 전달, finalize/rollback엔 미전달 — mode 추론 의존
**케이스**: `dev-cd.yml:91`은 `STRATEGY=$STRATEGY ...switch`로 전략 주입. 그러나 `:103` finalize, `:106` rollback은 STRATEGY 없이 호출. finalize/rollback은 STRATEGY를 안 읽고 `PENDING==ACTIVE`로만 mode를 추론(M1). 즉 **switch가 restart로 돌았는데 finalize가 이를 알 방법은 오직 상태파일 비교뿐**. F1(stale PENDING)이나 M1(파일 손상)과 겹치면 restart로 올린 걸 bluegreen으로 처리해 살아있는 포트를 drain할 수 있다.

**근거**: `dev-cd.yml:86,91,103,106`, `deploy.sh:173,197`.

**권장**: switch가 MODE 파일 기록(M1과 동일 해법) → finalize/rollback이 그 파일을 읽음. STRATEGY 재전달보다 서버측 상태를 신뢰.

---

### M5. (Medium) journalctl tail이 Discord로 시크릿 유출 여지 + 1500바이트 절단
**케이스**: `dev-cd.yml:123-129`가 `journalctl -u ghostrunner@$p` tail을 수집해 `:156-157`에서 Discord로 전송. 앱이 부팅 실패 시 스택트레이스에 **DB URL/자격증명/토큰이 로그에 찍혔다면 그대로 유출**. 또한 `tail -c 1500`(`:129`)로 절단돼 정작 원인 라인이 잘릴 수 있다(A6 부분충족 유지).

**근거**: `dev-cd.yml:123-129,156-157`.

**권장**: 전송 전 마스킹(`sed`로 password=/token= 치환) 또는 Discord엔 "로그는 서버에서 확인" 링크만. 최소 README에 "CICD 웹훅 채널 접근 제한" 명시.

---

### L1. (Low) SSH `accept-new` TOFU — C4 잔존
`dev-cd.yml:75,77,88,123` 모두 `StrictHostKeyChecking=accept-new`. EC2_HOST가 도메인이면 DNS 하이재킹 시 첫 연결 MITM 여지. 리팩터가 건드리지 않음. 스코프 대비 수용 가능하나 `ssh-keyscan` 고정 known_hosts가 이상적.

### L2. (Low) `-x test` 직접 push 배포 무테스트 — B5 미해소(정책)
`dev-cd.yml:4-6`(`push:dev`) + `:54`(`-x test`). PR 없이 dev 직접 push하면 테스트 없이 배포. PLAN.md:168이 "브랜치 보호 미채택(사용자 판단)"으로 명시 → **의도적 수용**. README에 "dev는 PR로만" 규칙은 여전히 부재.

### L3. (Low) `|| true`/`|| log`로 삼키는 정리 실패
`deploy.sh:115,121,131`(idle stop), `:179`(구버전 stop), `:156,202`(원복 재기동). 대부분 cleanup 맥락이라 타당하나, `:179` 구버전 drain stop 실패는 `WARNING`으로 표면화됨(C6 개선). `:156,202`의 `systemctl restart ... || true`는 **원복 재기동 실패를 삼켜** rollback이 "완료"로 끝나지만 실제 앱은 죽어있을 수 있음 → `:203` `wait_health_full || log WARNING`이 있어 부분 방어. 수용 가능하나 rollback 최종 성공을 health로 재확인 후 exit code에 반영 권장.

### Info1. finalize 성공했으나 외부 health가 그 사이 다시 죽으면?
2단계는 "외부 200 → finalize"까지만 검증. finalize(구버전 drain) 도중/직후 신버전이 죽으면 이미 구버전은 stop 진행 → 다운타임. 창은 짧지만 존재. 설계상 수용(재롤백 없음).

### Info2. CONTEXT.md/ADR 부재 — 도메인 용어 검증 불필요
`CONTEXT.md`, `docs/adr/` 부재 확인. 배포 도메인은 인프라라 해당 없음.

---

## 3. 결론

### 이번에 반드시 고쳐야 할 것
1. **F1 (Critical)** — `cmd_switch` 진입부에 **stale PENDING 감지·정리**. 러너 사망/네트워크 단절로 switch만 되고 finalize/rollback이 안 온 상태에서 다음 배포가 살아있는 인스턴스를 재시작·오염시키는 것을 막아야 한다. (감지 후 die 또는 자동 finalize 승격)
2. **H2 (High)** — 외부 health를 http_code 200뿐 아니라 **본문 `"status":"UP"` 검증**으로 강화. 죽은 앱에 finalize되는 오탐 차단.
3. **H3 (High)** — liveness 게이트 노출을 **커밋된 `application.yml`에 exposure 명시**로 리포에서 검증 가능하게. 시크릿 exposure 누락 시 404→불필요 롤백을 방지. 최소 게이트 실패 시 http_code 로깅.
4. **F2 (High)** — `deploy.sh`에 `flock`. CI 배포와 수동 조작(README가 권장) 경합 시 상태파일 레이스 차단.

### 고칠 필요 없는(수용 가능) 것
- 이전 Blocker C1/B1(설계)/B2/B3/B9a 해소 확인 — 재작업 불필요.
- H4(B7, nohup 자동종료) — 문서 방어 유지, 최초 1회성 리스크라 수용 가능(단 README 경고는 이미 있음).
- L1(TOFU accept-new), L2(무테스트 직접 push, PLAN이 의도적 미채택), M5(마스킹은 과하나 채널 접근 제한으로 갈음), Info1/Info2 — 스코프 대비 수용.
- systemd graceful(`ghostrunner@.service:22-24`), nginx SSL 보존 설계, 시크릿 파일 write 마스킹(`dev-cd.yml:46-47`), 동시 배포 concurrency(잡 레벨) — 정상.

### 종합
2단계 리팩터는 이전 Critical(B3 비가역 restart, C1 롤백 실패 은폐)을 정확히 해소했고, 외부 health를 **finalize 전 진짜 게이트**로 승격한 설계는 무중단 약속을 강화했다. 그러나 상태머신을 2단계로 쪼개면서 **"switch와 finalize/rollback 사이의 원자성"** 이라는 새 취약면이 생겼다: 그 사이 러너가 죽으면(F1) stale PENDING을 아무도 정리하지 않고, 다음 배포가 이를 감지하지 못한다. 이것이 이번 리팩터의 핵심 회귀다. F1을 포함한 위 4건을 해소하면 `workflow_dispatch` 수동 첫 배포로 진행 가능하다.
