# GhostRunner CI/CD 파이프라인 수립 — PLAN

> 이 문서는 컨텍스트 압축 후에도 세션 작업을 이어가기 위한 단일 진실 원천(SSOT)이다.
> 작업 브랜치: `claude/zealous-goodall-4822c2` (worktree). 대상 배포 브랜치: `dev`.
> 상태: **계획 확정 완료 / 구현 미착수** (2026-07-07 기준)

---

## 1. 원 요구사항 (사용자 제시)

**최종 목표: dev 브랜치 기준 CI/CD 파이프라인 새로 수립**
- dev에 PR 게시 시 테스트 수행 (기존 워크플로우 존재)
- dev에 PR 병합 시 배포 수행 — **downtime 없으면 best (가능 여부 확인 요청)**
- 배포 성공 시 `GET https://api.ghostrunner.io/actuator/health`로 서버 생존 확인
- 실행 후 Discord Webhook으로 성공/실패 전송 (**실패 시 로그도 전송**)

**지침**
1. 모호함 사라질 때까지 사용자와 대화로 계획 구체화 → 구현은 계획 확정 후.
2. 계획 확정 후 Task List로 관리.
3. 세션 내내 지시 준수.
4. 사용자가 작업 종료 선언 시 이 worktree 삭제.

---

## 2. 인프라 현황 (코드베이스 탐색 + 사용자 확인으로 확정)

- **단일 Ubuntu EC2, RAM 1.9GB + Swap 2.0GB.** 이 박스는 사실상 앱 전용.
  - 현재 `free`: used 1.2G, **available 664M**, swap used 187M(→ swap-free 1.8G).
  - 현재 java 단일 인스턴스 RSS **912MB** (heap 기본 ~25%≈512m + off-heap 큼: WebFlux/Netty·Redisson·S3·Firebase·OpenAI SDK).
  - **MySQL·Redis는 외부** (RDS `*.rds.amazonaws.com`, ElastiCache `*.serverless.apn2.cache.amazonaws.com`). 이 박스에 없음.
- **nginx가 앱 앞단**에 있음. HTTPS(Certbot).
  - **도메인 확정: `api.ghostrun.io`** (목표 텍스트의 `api.ghostrunner.io`는 오타). health check = `https://api.ghostrun.io/actuator/health`.
  - 앱 server 블록 위치: `sites-available/default` (sites-enabled 심링크). `proxy_pass http://localhost:8080;` 하드코딩.
  - `client_max_body_size 50M`이 **nginx.conf http 블록에 이미 전역 설정**됨 → `.platform` body-size는 중복, 이전 불필요(그냥 삭제).
  - `nginx.conf`가 `conf.d/*.conf`를 `sites-enabled/*`보다 먼저 include → upstream을 conf.d에 두면 참조 순서 OK.
- 현재 앱 실행: **nohup** `java -jar -Dspring.profiles.active=prod ghostrunner-0.0.1-SNAPSHOT.jar` (PID 8492, USER ubuntu, 포트 기본 8080).
- **CI/CD 없음.** dev 머지 후 손수 jar 빌드 → FTP로 교체하는 수동 방식.
- **Docker 미사용.** plain jar 실행.

### 리포 현황
- 기존 CI: `.github/workflows/dev-ci.yml`(PR→dev 테스트), `main-ci.yml`(PR→main, 동일 내용).
  - ⚠️ setup-java **JDK 21**인데 build.gradle toolchain은 **17** → 잠재적으로 깨지는 조합.
    **해결: 다운그레이드 대신 JDK 17+21 둘 다 설치**(사용자가 곧 build.gradle을 21로 올릴 예정이라 미래 대비).
    Gradle toolchain 자동탐지가 두 JDK를 모두 찾도록 `org.gradle.java.installations.fromEnv` 등으로 배선.
- **EB/CodeBuild 잔재 (삭제 대상)**: `buildspec.yml`, `.platform/nginx/conf.d/client_max_body_size.conf`.
- **main `application.yml` 없음.** prod 설정은 `.gitignore`된 `application-{local,dev,prod}.yml`로, **빌드 시 jar에 번들**됨(외부 주입 아님). 로컬 개발자 파일이 소스.
- 앱 내부 `DiscordWebhookClient` 존재(런타임용) — CI/CD 알림과는 별개.
- 최근 커밋에 Graceful Shutdown 도입됨.

### application-prod.yml 구조 (사용자 제공, 마스킹본 기준)
- **민감(→ 시크릿 블롭)**: `spring.datasource.url/username/password`, `spring.data.redis.host`,
  `cloud.aws.credentials.access-key/secret-key`, `cloud.aws.sqs.push-queue-name/push-dlq-name`,
  `s3.bucket`, `jwt.secret`, `sentry.dsn`, `openai.api.key`, `discord.webhook.url`.
- **비민감(구조값)**: server.shutdown=graceful, driver-class, multipart 50MB, redis.port/ssl,
  jpa(ddl-auto=validate, open-in-view=false, batch_fetch_size=100), **lifecycle.timeout-per-shutdown-phase=120s**,
  logging levels, aws.region=ap-northeast-2, s3 디렉토리 4종, jwt 만료시간, sentry.environment=prod,
  **management.endpoints.web.exposure.include="prometheus,health,metrics"**(health 노출 확인됨), metrics 설정.

---

## 3. 확정된 설계 결정

| 항목 | 결정 |
|---|---|
| GHA→EC2 접속 | **SSH 키** (appleboy/ssh-action 계열, scp+ssh). EC2_USER=`ubuntu` (passwordless sudo 가정) |
| 프로세스 관리 | **systemd 템플릿 유닛** `ghostrunner@.service` (`%i`=포트). nohup 폐기 |
| 무중단 방식 | **Blue-Green 2단계**(switch→외부health→finalize/rollback) on 8080/8081 + nginx upstream 스위칭. 외부 health 통과 전엔 구버전 유지 |
| 포트 토글 | 서버의 `ACTIVE_PORT`(확정) + `PENDING_PORT`(전환 대기) 상태 파일. 매 배포마다 8080↔8081 자동 왕복 |
| 배포 게이트 | 전환 '전' full-health(`/actuator/health`, DB/Redis 포함) / 전환 '후' liveness(`/actuator/health/liveness`, 앱만)+재시도 / finalize 전 외부 health |
| jar 격리 | 포트별 `app-8080.jar` / `app-8081.jar` (구 인스턴스 jar 보존, lazy 클래스로딩 안전) |
| config 주입 | **단일 GitHub Secret `APP_PROD_YML`** (전체 prod yml 실제값)을 CI가 빌드 직전 `src/main/resources/application-prod.yml`로 write. 개별키 분해/커밋 템플릿 없음 |
| profile | `prod` |
| 스코프 | **dev만.** main-ci.yml은 그대로 둠 |
| Docker | **미도입** (2GB에서 RAM 이득 없음, 오버헤드만) |
| Discord 실패 알림 | **Actions 실행 링크 + 서버 `journalctl` tail** 동봉 |
| 힙 설정 | `-Xms256m -Xmx512m` (현재 기본값과 동일 수준 → 정상운영 무회귀; 겹침 피크 완화) |
| graceful | systemd `TimeoutStopSec=130` (yml의 120s drain + 여유), `SuccessExitStatus=143` |

### 메모리 분석 & 폴백 (2GB 제약의 핵심)
- 겹침 피크 ≈ 구버전 912M + 신버전 warming ~650M ≈ **~1.55GB RAM + swap ~100~200M**.
  실효 여유(available 664M + swap-free 1.8G ≈ 2.4G) 안에 들어옴 → **OOM-kill 없음.**
- 신버전은 health 통과 전 **무부하 warming**(nginx 미전환)이라 힙 미충전. 전환 즉시 구버전 drain.
- **현실적 비용**: 겹침 창(warming 40~70s, + drain 최대 120s는 in-flight 있을 때만)에 swap 실제 접촉 →
  **순간 레이턴시 저하 가능** (죽지는 않음). OpenAI 등 장시간 요청이 drain을 늘릴 수 있음.
- **정상 운영 무영향**: 배포 후 단일 인스턴스 = 현재와 동일 조건.
- **안전장치**: deploy.sh에 (1) 기동 전 `available` 가드(임계 미만 시 중단/폴백), (2) `STRATEGY=bluegreen|restart` 스위치.
  **첫 배포 때 `free -h` 실측**으로 최종 확정. 심하면 restart 모드(수 초 다운타임) 또는 t3.small→medium 증설.

### Blue-Green 실패/롤백 안전성
구버전(ACTIVE)은 신버전이 **로컬 health + 외부 health 모두 통과할 때까지 절대 stop하지 않음.**
| 실패 지점 | 처리 | 다운타임 |
|---|---|---|
| 빌드/테스트/scp/기동 실패 | 중단, 구버전 그대로 | 없음 |
| 신규 local health 타임아웃 | idle stop, 중단, journalctl tail 알림 | 없음 |
| `nginx -t` 실패 | reload 안 함, 중단 | 없음 |
| 전환 후 외부 health 실패 | **nginx 구포트 원복+reload(자동 롤백)**, 신규 stop, 알림 | 순간(롤백) |
| 성공 | 구버전 drain+stop, `ACTIVE_PORT` 갱신, 성공 알림 | 없음 |

---

## 4. 산출물 (리포에 생성/수정/삭제)

**생성**
- `.github/workflows/dev-cd.yml` — 배포 워크플로우. 트리거 `push: dev` + `workflow_dispatch`.
  스텝: 체크아웃 → JDK17 → `APP_PROD_YML`·`FIREBASE_PRIVATE_KEY_JSON` 시크릿을 파일로 write →
  `./gradlew build` → scp(staging jar) → ssh `deploy.sh` 실행 → 외부 `GET api.ghostrunner.io/actuator/health` →
  Discord 성공/실패(실패 시 journalctl tail + run 링크) 알림.
- `deploy/ghostrunner@.service` — systemd 템플릿 (User=ubuntu, `app-%i.jar`, `--server.port=%i --spring.profiles.active=prod`,
  `-Xms256m -Xmx512m`, `SuccessExitStatus=143`, `TimeoutStopSec=130`, `Restart=on-failure`).
- `deploy/deploy.sh` — Blue-Green 오케스트레이터(서버 실행, CI가 SSH로 호출). **서브커맨드 switch/finalize/rollback** 2단계.
  토글·full-health/liveness 게이트(재시도)·nginx 전환·자동 롤백(실패 시 CRITICAL·idle 보존)·available 가드·STRATEGY 폴백(.prev 백업).
- `src/main/resources/application.yml` — **커밋되는 비민감 공통 설정**. `management.endpoint.health.probes.enabled=true`(liveness 게이트용). 시크릿 없음.
- `deploy/nginx-ghostrunner.conf` — `/etc/nginx/conf.d/`에 놓을 **upstream `ghostrunner_backend`** 스니펫(deploy.sh가 포트 재작성).
  setup.sh가 `sites-available/default`의 `proxy_pass http://localhost:8080;` → `proxy_pass http://ghostrunner_backend;`로 한 줄 변경(백업+`nginx -t`+실패 시 원복). SSL/Certbot 블록 불변. body-size는 이미 전역이라 손 안 댐.
- `deploy/setup.sh` — 서버 1회성 부트스트랩(멱등): 디렉토리, systemd/nginx 설치, `ACTIVE_PORT` 초기화, enable, 최초 cutover 안내.
- `deploy/README.md` — 런북(시크릿 등록, SSH 키, setup 실행, 최초 cutover, 수동 롤백, 메모리 실측, STRATEGY 전환).

**수정**
- `.github/workflows/dev-ci.yml` — setup-java에 **JDK 17+21 둘 다 설치**(다운그레이드 X). dev-cd도 동일.

**Sentry 소스 컨텍스트 (옵션 A 채택 — 켜둠)**
- build.gradle `includeSourceContext=true` + `authToken=env(SENTRY_AUTH_TOKEN)` 때문에 `./gradlew build` 시
  `sentryUploadSourceContext`가 소스 스니펫을 Sentry(`sogogimaratang`/`ghostrunner-dev-backend`)에 업로드.
- **dev-cd 빌드 스텝에 `SENTRY_AUTH_TOKEN` env 주입** → 예외 시 Sentry UI에서 실제 코드 라인 확인 가능.
- 토큰은 **빌드 타임에만** 필요. dev-ci(테스트만)는 이 태스크를 안 밟으므로 토큰 불필요(그대로 둠).
- 사용자: Sentry → Settings → Auth Tokens → **Organization Auth Token** 발급(무료 플랜 가능) → `SENTRY_AUTH_TOKEN` 시크릿 등록.

**삭제**
- `buildspec.yml`, `.platform/` (EB/CodeBuild 잔재). body-size는 nginx.conf에 이미 전역이라 이전 불필요.

**주의**: `application-prod.yml`은 커밋하지 않음(`.gitignore` 유지). CI 런타임에만 생성.

---

## 5. GitHub Secrets (총 6개 + 기존 firebase, 사용자 등록)

| Secret | 용도 | 상태 |
|---|---|---|
| `APP_PROD_YML` | 전체 prod 설정 블롭(실제값) | 신규 |
| `EC2_HOST` | 배포 대상(도메인/공인IP) | 신규 |
| `EC2_USER` | `ubuntu` | 신규 |
| `EC2_SSH_KEY` | 배포용 SSH 개인키 | 신규 |
| `CICD_DISCORD_WEBHOOK_URL` | 파이프라인 알림용 웹훅(런타임 discord.webhook.url과 별개) | 신규 |
| `FIREBASE_PRIVATE_KEY_JSON` | firebase 키 | 기존 |
| `SENTRY_AUTH_TOKEN` | 소스 컨텍스트 업로드(dev-cd 빌드) — Organization Auth Token | 신규(채택) |

---

## 6. 역할 분담

### 🤖 Claude (리포 작업 — 서버 SSH 접속은 안 함)
Task List #1~#8 참조. 리포 파일 생성/수정/삭제 → PR 게시 → 서버 셋업 후 첫 배포 함께 검증.

### 🙋 사용자
- **A. 사전준비**: (1) SSH 배포 키 생성·EC2 authorized_keys 등록·개인키를 시크릿에, (2) GitHub Secrets 5개 등록, (3) **현재 nginx 설정 공유**(proxy_pass·SSL — upstream 최소수정안 산출용).
- **B. 서버 셋업(내 스크립트로 1회)**: `setup.sh` 실행(sudo) → **최초 cutover**(systemd 8081 기동·검증 → nginx 8081 전환 → 기존 nohup kill → `ACTIVE_PORT=8081`).
- **C. 첫 배포 검증**: `workflow_dispatch` 수동 배포 → 겹침 `free -h` 실측 → 확정 or `STRATEGY=restart`/증설 결정.

---

## 6.5. 독립 검증 & 하드닝 (완료)
`VERIFICATION_REPORT.md`(무맥락 SubAgent) 판정: 조건부 통과. Blocker 반영 완료:
- **C1** 롤백 nginx 실패를 치명 처리 + idle 보존 (더 이상 거짓 "롤백 완료" 없음)
- **B1** 2단계 배포로 외부 health를 finalize 전 진짜 게이트化 → 실패 시 자동 롤백(구버전 무중단)
- **B2** 전환 후 게이트를 liveness+재시도로 분리(의존성 blip 오탐 제거). `application.yml` 커밋
- **B3** restart 폴백에 `.prev` 백업/원복
- **B9a** `setup.sh` `$BAK` 초기화
- (미채택) B5 브랜치 보호 명문화 — 사용자 판단으로 생략

**2차 검증(`VERIFICATION_REPORT_R2.md`, 적대적 케이스) 반영:**
- **F1**(Critical, 신규) switch 후 러너 사망 → stale PENDING 방치. `cmd_switch` 진입 시 **nginx 실제 라우팅 기준 자동 정리**(승격/orphan)
- **F2** `flock` 으로 CI+수동 조작 경합 차단
- **H2** 외부 health + 로컬 health 모두 **본문 `"status":"UP"` 검증**(200 오탐 차단)
- **H3** liveness 404 시 **root health 폴백** + http_code 로깅. (리포 권고 "커밋 yml에 exposure"는 프로파일 override로 무효라 채택 안 함 → 폴백+README 체크리스트로 대체)
- **M1/M4** `DEPLOY_MODE` 파일로 mode 명시(추론 제거) + 포트 화이트리스트 검증
- **M3** `df` 디스크 가드 + `set_upstream` 백업/검증/원복(부분쓰기 방어)
- **M5** journalctl→Discord 전 password/token 마스킹 + 채널 제한 README note
- (미채택) H4 nohup 자동kill(systemd java도 패턴 매칭돼 위험), M2(문서만), L1/L2/L3/Info

## 7. 열린 항목 / 다음 액션
- [x] 사용자: 현재 nginx 설정 공유 완료 (도메인 api.ghostrun.io, proxy_pass localhost:8080, body-size 전역)
- [x] health check 도메인 확정: **`api.ghostrun.io`** (dev-cd 및 deploy.sh 외부검증에 사용)
- [ ] Claude: Task #1~#8 구현 착수 (사용자 GO 대기)
- [ ] 첫 배포 시 겹침 메모리 실측으로 Blue-Green 최종 확정
- [ ] (스코프 밖) 모니터링 비용 절감: Grafana Cloud 무료티어 또는 t4g 다운사이징 — 별도 트랙
- [ ] 인스턴스 타입 확인(health 타임아웃 튜닝, nice-to-have)
