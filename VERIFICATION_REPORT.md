# GhostRunner CI/CD 파이프라인 독립 검증 리포트

> 검증자: 독립 리뷰어 (작업 미수행). 근거는 모두 `파일:라인`으로 표기.
> 대상 브랜치: `claude/zealous-goodall-4822c2` (base `dev`).
> 검증 방식: 읽기 전용 (파일 정독 + grep + git). 서버 실행/SSH 없음.

---

## 요약 판정

**조건부 통과 (Conditional Pass)** — 요구사항은 대부분 충족하나, **배포 안전성에 직결되는 결함 2건(Critical)** 과 정합성 결함 다수가 존재. 아래 Blocker 체크리스트를 해소하기 전에는 첫 자동 배포를 권장하지 않음.

### 심각도별 발견 개수

| 심각도 | 개수 |
|---|---|
| Critical | 2 |
| High | 5 |
| Medium | 6 |
| Low | 4 |
| Info | 3 |

핵심 요지:
1. **(Critical)** `wait_health`/외부 health가 실패하면 스크립트가 `set -e`로 죽어 **자동 롤백 로직이 실행되지 않는 경로**가 있고, health 게이트가 앱 전체 헬스(DB/Redis 집계)에 의존해 **인프라 블립만으로도 배포 실패 → 신규 인스턴스 leak** 가능.
2. **(Critical)** restart 폴백 모드는 **구버전을 먼저 정지시키지 않고 jar를 덮어쓴 뒤 `systemctl restart`** 하지만, restart 실패 시 **되돌릴 구 jar가 이미 소실**되어 복구 불가 상태가 됨.

---

## A. 요구사항 충족 여부 (PLAN.md §1 대조)

| # | 요구사항 | 판정 | 근거 |
|---|---|---|---|
| A1 | dev PR 시 테스트 | ✅ 충족 | `dev-ci.yml:3-7`(`pull_request: dev`), `:50-54`(`gradlew test`) |
| A2 | dev 병합 시 배포 | ✅ 충족 | `dev-cd.yml:3-7`(`push: dev` + `workflow_dispatch`) |
| A3 | 무중단(Blue-Green) | 🟡 부분충족 | `deploy.sh:86-131` 로직 존재하나 롤백/health 게이트에 결함(B/C 참조). 정상 경로는 무중단이나 실패 경로 안전성 미보장 |
| A4 | 배포 후 `https://api.ghostrun.io/actuator/health` 확인 | ✅ 충족 | `dev-cd.yml:18,89-96`. 도메인은 `ghostrun.io`로 정정됨(요구사항 오타 `ghostrunner.io` 반영) |
| A5 | Discord 성공/실패 알림 | ✅ 충족 | `dev-cd.yml:120-142` |
| A6 | 실패 시 로그 전송 | 🟡 부분충족 | `dev-cd.yml:98-118,136-142`. journalctl tail 수집·첨부하나 **1500바이트로 절단**(`:113`)되고, `-x test` 빌드라 CI 빌드 실패 로그는 Actions 링크로만 제공 |
| A7 | EB·CodeBuild 잔재 삭제 | ✅ 충족 | `git status`: `buildspec.yml`, `.platform/.../client_max_body_size.conf` deleted. `git ls-files`에 잔재 없음 |
| A8 | Docker 없이 plain jar | ✅ 충족 | `ghostrunner@.service:15-17`(java -jar), Dockerfile 부재 |

**소결**: 요구사항 기능 자체는 모두 배선됨. 다만 A3/A6은 실패 경로 품질에서 감점.

---

## B. 정합성·정확성 결함

### B1. (High) 외부 health 실패 시 신규 인스턴스가 서버에 leak — 서버측 미정리
- **근거**: `deploy.sh:112-131` — through-nginx 게이트까지 통과하면 스크립트는 성공으로 종료. 이후 **CI의 외부 health check**(`dev-cd.yml:89-96`)가 실패하면, 서버의 `deploy.sh`는 **이미 exit 0** 이고 `ACTIVE_PORT`도 신규로 갱신된 상태(`deploy.sh:121`). 즉 through-nginx는 통과했지만 DNS/외부 경로만 문제인 경우, **서버는 신버전으로 완전히 전환된 채** CI만 실패로 뜬다. Discord는 실패 알림을 보내지만 실제 서버는 신버전 서빙 중 → **알림과 실제 상태 불일치**. 반대로 through-nginx는 로컬 127.0.0.1:443이라 SSL/DNS 문제를 못 잡음.
- **권장**: 외부 health를 deploy.sh 성공 판정의 일부로 승격하거나, 최소한 실패 알림 문구에 "서버는 신버전 전환됨(수동 확인 요)"를 명시.

### B2. (Critical) health 게이트가 앱 전체 헬스에 의존 → 인프라 블립으로 오탐, 신규 인스턴스 미정리 위험
- **근거**: `health_ok()`(`deploy.sh:35`)와 through-nginx(`:57`)는 `/actuator/health`를 호출. `application-prod.yml`은 `management...exposure.include="prometheus,health,metrics"`만 노출(PLAN.md:57)하고 **health group/probe 설정이 소스에 없음**(`grep` 결과 `management.endpoint.health.*` 부재). Spring Boot 기본 health는 **DB(datasource)·Redis·디스크 등을 집계**하므로, RDS/ElastiCache 순간 지연이나 Redisson 커넥션 블립만으로 health가 `DOWN`이 되어 `wait_health` 타임아웃(`:37-44`) → `:99-103`에서 신규 stop 후 die. 이 자체는 안전(구버전 유지)하지만, **through-nginx 통과 후**(`:112`) 외부에서 같은 블립이 나면 롤백은 되어도 신규 인스턴스만 stop되고 상태가 어정쩡해짐.
- **부가**: liveness/readiness 분리가 없어 "앱은 살아있지만 의존성 blip"을 구분 못 함.
- **권장**: 배포 게이트 전용으로 `/actuator/health/liveness`(probe 활성화) 사용하거나, health group을 정의해 DB 의존성을 게이트에서 제외. 최소한 재시도/유예를 둘 것.

### B3. (Critical) restart 폴백 모드 — 실패 시 구버전 복구 불가
- **근거**: `deploy.sh:75-84`. `mv -f staging.jar app-${ACTIVE}.jar`(`:77`)로 **현재 서빙 중인 포트의 jar를 덮어쓴 뒤** `systemctl restart`(`:78`). 신버전이 기동/health 실패하면(`:79`) die하지만, **구 jar는 이미 덮어써져 소실**. 되돌릴 산출물이 없어 롤백 불가. bluegreen 모드는 idle 슬롯을 쓰므로 안전하지만(`:94`), 폴백 경로가 오히려 더 위험.
- **추가**: restart 모드는 `mem_guard`·nginx 원복 로직도 없이 단순 교체. `set_upstream "$ACTIVE"`(`:80`)는 same-port라 사실상 no-op에 가까움.
- **권장**: restart 모드에서도 `cp` 백업(`app-${ACTIVE}.jar.prev`) 후 실패 시 원복하도록 수정.

### B4. (High) JDK toolchain fromEnv 라벨 하드코딩 — setup-java 산출 변수와 어긋날 위험
- **근거**: `dev-cd.yml:55`, `dev-ci.yml:53`이 `-Dorg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64`를 하드코딩. `actions/setup-java`는 마지막 설치 JDK를 `JAVA_HOME`으로, 각 버전을 `JAVA_HOME_<major>_<arch>`로 노출하는데 **아키텍처 suffix가 `X64`가 아닐 수 있고**(러너 변경 시), 또한 fromEnv가 없어도 setup-java가 이미 toolchain을 auto-detect함. 현재 ubuntu-latest(x64)에선 동작하나 **암묵적 러너 아키텍처 의존**. arm 러너로 바뀌면 조용히 깨짐.
- **권장**: 그냥 fromEnv 제거(setup-java의 다중 JDK는 `~/.m2`/toolchains 없이도 Gradle auto-detect 대상). 남긴다면 주석으로 러너 아키텍처 의존성 명시.

### B5. (Medium) `push: dev` 직접 푸시는 테스트를 건너뜀
- **근거**: `dev-cd.yml:3-6`은 `push: dev`에서 배포. CD 빌드는 `-x test`(`:49-54`). 테스트는 `dev-ci.yml`의 `pull_request: dev`에서만 실행. **PR 없이 dev에 직접 push/merge하면 테스트 없이 배포**됨. PLAN은 "PR 병합 시 배포"를 전제하나 push 트리거는 그보다 넓음.
- **권장**: 브랜치 보호로 PR 강제 + dev-cd 앞단에 test job 의존을 두거나, 최소한 README에 "dev는 PR로만 병합" 명시(현재 README §CI에 이 규칙 없음).

### B6. (Medium) jar 경로/이름 정합 — 통과하나 취약
- **근거**: `build.gradle:24` version `0.0.1-SNAPSHOT`, group `soma`. bootJar 이름은 `ghostrunner-0.0.1-SNAPSHOT.jar`. `dev-cd.yml:60` `ls build/libs/ghostrunner-*.jar | grep -v plain | head -1` — 현재는 정확히 1개라 OK. archiveBaseName 커스터마이즈가 build.gradle에 없어 프로젝트명 기반 `ghostrunner`가 됨(정합). `head -1`은 다중 매칭 시 오선택 가능하나 현 상태 안전.
- **권장**: 견고화하려면 `bootJar { archiveFileName = "app.jar" }` 고정.

### B7. (Medium) 최초 cutover 포트 충돌 자동 방지 없음 — 수동 절차 의존
- **근거**: `deploy.sh:125-129`는 nohup 잔존을 **경고만** 하고 kill하지 않음. README §3(`README.md:73-79`)에서 사용자에게 수동 `pkill`을 지시. 2번을 건너뛰면 "다음 배포가 8080 타깃 시 포트 충돌로 기동 실패"라고 README:79가 명시. 즉 **알려진 함정을 문서로만 방어**.
- **권장**: 최초 cutover 후 nohup 자동 종료(안전 조건 하) 또는 setup.sh에 nohup 종료 옵션 제공.

### B8. (Low) systemd graceful 정합 — 대체로 양호
- **근거**: `ghostrunner@.service:24` `TimeoutStopSec=130` > app drain 120s(PLAN.md:56), `SuccessExitStatus=143`(`:22`), `KillSignal=SIGTERM`(`:20`) 정합. `-XX:+ExitOnOutOfMemoryError`(`:15`)로 OOM 시 프로세스 종료→`Restart=on-failure`(`:26`) 재기동. 힙 `-Xms256m -Xmx512m` PLAN과 일치.
- **참고**: 단 drain이 실제 120s 걸리는 in-flight(OpenAI 장시간 요청)면 겹침 창이 130s까지 늘어 메모리 피크 지속. Info로 재언급(C2).

### B9. (Low) nginx 전환 안전성 — 양호
- **근거**: upstream을 별도 conf.d 파일로 분리(`nginx-ghostrunner.conf`), `set_upstream`이 `nginx -t` 후 reload(`deploy.sh:47-53`). SSL/Certbot 블록은 `sites-available/default`에 있고 setup.sh는 `proxy_pass` 한 줄만 sed 교체(`setup.sh:41`) + 백업(`:38-39`) + `nginx -t`(`:49`). SSL 블록 불변 설계 정합.
- **주의(B9a, Medium)**: `setup.sh:50`의 실패 안내가 `$BAK` 변수를 참조하나, `proxy_pass localhost:8080` 라인을 못 찾은 else 분기(`:43-46`)에서는 `BAK`가 **미설정**. 이 경우 `nginx -t` 실패 시 `:50`에서 `$BAK`가 빈 문자열 → 원복 안내가 깨짐. `set -u`이나 `${BAK}`가 echo 문자열 안이라 즉시 죽지는 않고 잘못된 안내만 출력.

---

## C. 취약점·리스크 (보안·운영)

### C1. (High) health 실패 시 자동 롤백이 `set -e`와 상호작용하는 사각지대
- **근거**: `deploy.sh:14` `set -euo pipefail`. 롤백 블록(`:112-118`)은 through-nginx 실패를 `if !`로 감싸 안전. 그러나 `set_upstream`이 롤백 중 실패하면 `|| true`(`:114`)로 무시되고 die로 넘어감 → **롤백 자체가 실패해도 그냥 종료**. 이 경우 nginx는 신규(죽은) 포트를 가리킨 채 남아 **다운타임 발생 가능**. "구버전이 정말 살아있는지"는 stop 전(`:123`)이라 프로세스는 살아있지만 nginx가 죽은 포트를 봄.
- **권장**: 롤백의 `set_upstream "$ACTIVE"` 실패를 치명으로 처리하고 재시도/명시 알림.

### C2. (Medium) 2GB RAM 겹침 OOM/스왑 리스크 — 가드는 있으나 불완전
- **근거**: `MEM_GUARD_MB=350`(`deploy.sh:23`) 가드가 bluegreen 기동 전 `available` 검사(`:87-90`). 그러나 (a) **가드 임계 350MB는 신버전 warming 피크(~650MB, PLAN.md:81)보다 작아** 통과해도 실제 겹침 시 스왑 접촉 불가피(PLAN도 인정). (b) restart 모드에는 가드 없음(B3). (c) `avail_mb`는 `free -m ... $7`(available)로 정확. STRATEGY 폴백은 수동 주입만 가능(자동 폴백 없음). PLAN.md:85-86과 일치(설계상 수용).
- **권장**: 겹침 실패(신규 기동 중 OOM) 시 자동 restart 폴백 고려는 스코프 밖이나 문서화됨. 수용 가능.

### C3. (Medium) `curl -k`(`-skf`) 사용 — through-nginx 게이트에서 TLS 검증 우회
- **근거**: `deploy.sh:57` `curl -skf -H "Host: $DOMAIN" https://127.0.0.1/...`. `-k`는 127.0.0.1에 대한 self-loop이라 인증서 SAN 불일치 회피 목적이며 위험 낮음(로컬 루프백). 다만 **인증서 만료/잘못된 체인을 게이트가 못 잡음** → 외부 사용자에겐 SSL 오류인데 배포는 통과. 외부 health(`dev-cd.yml:92`)는 `-k` 없이 실제 TLS 검증하므로 이중 안전망은 있음.
- **권장**: 수용 가능. 다만 through-nginx가 TLS 문제를 못 잡는 점을 인지.

### C4. (Medium) SSH host key 검증 `accept-new` (TOFU)
- **근거**: `dev-cd.yml:75,77,85,107` 모두 `StrictHostKeyChecking=accept-new`. 첫 연결 시 호스트키를 무검증 수락(TOFU). MITM 리스크는 낮으나 존재. EC2_HOST가 도메인이면 DNS 하이재킹 시 위험.
- **권장**: 알려진 호스트키를 시크릿으로 관리하여 `known_hosts` 사전 주입(`ssh-keyscan` 고정)이 이상적. 스코프 대비 Low~Medium.

### C5. (Low) 시크릿 노출 — 대체로 안전
- **근거**: `APP_PROD_YML`/`FIREBASE`는 `printf '%s' "$VAR" > file`(`dev-cd.yml:46-47`)로 로그 미출력. `application-prod.yml`은 `.gitignore:42`로 커밋 차단, `firebase-private-key.json`도 `.gitignore:49`. Discord webhook은 `secrets.*`로 마스킹. journalctl tail(`dev-cd.yml:107-113`)이 **로그에 시크릿이 찍혔다면 Discord로 유출** 가능(로그가 민감정보 포함 시). 앱 로깅 정책에 의존.
- **권장**: journalctl tail 전송 전 마스킹은 과하나, 앱이 커넥션 문자열/토큰을 로그에 안 남기는지 확인 권장.

### C6. (Low) `|| true` 남용 — 정리 실패를 조용히 삼킴
- **근거**: `deploy.sh:101,107,115,123`의 `systemctl stop ... || true`. 정리(cleanup) 맥락이라 대체로 타당하나, `:123`(구버전 drain stop)이 실패하면 **구버전이 계속 떠서 포트/메모리 점유**한 채 성공 로그(`:131`)를 출력 → 다음 배포 때 idle 슬롯 충돌 가능.
- **권장**: `:123` stop 실패는 경고 로그로 표면화.

### C7. (Info) passwordless sudo·User=ubuntu 가정
- **근거**: `deploy.sh` 전반의 `sudo`(nginx/journalctl/systemctl), `ghostrunner@.service:10` `User=ubuntu`. PLAN.md:65가 명시적으로 가정. 운영 관례상 수용.

---

## D. 놓친 것 / 개선 제안

### D1. (High) 구 jar 정리(디스크 leak) 없음
- **근거**: `deploy.sh:94` `mv staging.jar app-${IDLE}.jar`로 매 배포 idle jar를 덮어씀. 2개 슬롯(app-8080/8081)만 유지되어 무한 누적은 아니나, **staging.jar가 실패로 남거나** 산출물이 커지면 디스크 압박. 로그·jar 정리 스텝 없음. 2GB 박스라 `/` 여유 확인 필요.
- **권장**: 배포 전 디스크 가드(`df`) 1줄 추가, staging.jar는 성공 후 삭제.

### D2. (Medium) 로그 로테이션 미설정
- **근거**: systemd journald가 기본 관리하나 무제한 성장 시 디스크 압박. `deploy/`에 journald size 제한/logrotate 설정 없음. logback CloudWatch appender(`logback-spring.xml:59`)가 있어 원격 전송은 되나 로컬 저널은 별개.
- **권장**: `journalctl --vacuum-size` 또는 유닛에 `LogRateLimit` 안내를 README에 추가.

### D3. (Medium) 배포 이력/버전 추적 부재
- **근거**: `ACTIVE_PORT`만 상태로 유지. 어떤 커밋 SHA가 어느 jar인지 서버측 기록 없음. 롤백 시 "구 jar가 남아있다면"(`README.md:107`)이라는 불확실 전제.
- **권장**: 배포 시 `app-${PORT}.jar` 옆에 `app-${PORT}.sha` 기록.

### D4. (Low) health 엔드포인트 노출은 확인됨 / show-details 미설정
- **근거**: PLAN.md:57 `exposure.include="...health..."` 확인. 단 `management.endpoint.health.show-details`가 소스에 없어 기본(never)일 가능성 → 게이트엔 200/503만 필요해 무관하나, 디버깅 시 상세 부족.
- **권장**: 게이트용은 현행 OK.

### D5. (Info) firebase 키 경로 정합 확인됨
- **근거**: CI가 `src/main/resources/firebase-private-key.json` write(`dev-cd.yml:47`), 앱은 `ClassPathResource("firebase-private-key.json")` 로드(`FirebaseConfig.java:18`) → 정합.

### D6. (Info) checkout 액션 버전 불일치
- **근거**: `dev-cd.yml:27` `checkout@v4`, `dev-ci.yml:20` `checkout@v3`. 기능 영향 없으나 일관성 위해 v4 통일 권장.

---

## 배포 전 반드시 고쳐야 할 것 (Blocker 체크리스트)

- [ ] **B3 (Critical)** — restart 폴백 모드에서 구 jar 백업 후 실패 시 원복. 현재는 실패 시 구버전 복구 불가.
- [ ] **B2 (Critical)** — 배포 health 게이트를 DB/Redis 집계 헬스가 아닌 liveness/전용 그룹으로 분리. 인프라 블립 오탐으로 인한 배포 실패·상태 불일치 방지.
- [ ] **C1 (High)** — 롤백 중 `set_upstream "$ACTIVE"` 실패(`deploy.sh:114`)를 치명 처리. 롤백 실패 시 다운타임 방지.
- [ ] **B1 (High)** — 외부 health 실패 시 서버가 이미 신버전 전환됨을 실패 알림에 명시하거나, 외부 health를 deploy.sh 판정에 포함.
- [ ] **B5 (Medium, 정책)** — dev 브랜치 보호 규칙(PR 필수)로 테스트 미실행 직접 push 배포 차단. README에 규칙 명문화.
- [ ] **B7 (Medium)** — 최초 cutover 시 nohup 자동/안내 종료를 강화(포트 충돌은 알려진 함정).
- [ ] **B9a (Low)** — `setup.sh:50` else 분기에서 `$BAK` 미정의 시 원복 안내가 깨지는 문제 수정.

### 배포해도 되는(수용 가능) 항목
- A1/A2/A4/A5/A7/A8 요구사항 충족, systemd graceful 정합(B8), nginx SSL 보존(B9), 시크릿 마스킹(C5), 메모리 가드 존재(C2, 첫 배포 실측 전제로 수용), 동시 배포 차단(`dev-cd.yml:10-12`) — 모두 정상.

---

## 종합

정상 경로(happy path)의 Blue-Green 설계와 요구사항 배선은 견고하고, PLAN.md의 결정들이 코드에 충실히 반영되어 있다. 감점은 전적으로 **실패 경로의 안전성**에 있다: restart 폴백의 비가역성(B3), health 게이트의 의존성 오탐(B2), 롤백 실패 처리(C1)가 "항상 살아있는 인스턴스 보장"이라는 무중단의 핵심 약속을 특정 조건에서 무너뜨린다. 위 Blocker 4건(Critical 2 + High 2)을 해소하면 자동 배포로 승격 가능하다. 그 전까지는 PLAN이 권고한 대로 `workflow_dispatch` 수동 첫 배포 + 서버 실측으로만 진행할 것.
