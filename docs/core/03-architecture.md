# 03. 소프트웨어 아키텍처

## 패키지 구조

```
soma.ghostrunner
├── GhostrunnerApplication      (@EnableRetry)
├── domain.{running, course, pacemaker, member, auth, device, notification, notice}
│   ├── api           # 컨트롤러 + 요청/응답 DTO
│   ├── application   # 서비스, 파사드, 이벤트 리스너, 워커
│   ├── domain        # 엔티티, VO, 도메인 서비스, 도메인 이벤트
│   ├── infra | dao   # 리포지토리 (⚠️ 도메인마다 명명 불일치: infra/persistence vs dao)
│   ├── exception, dto
└── global
    ├── clients       # aws(s3), openai, expo, firebase, discord
    ├── config        # Security, Redis, Aws, Querydsl, Async, Swagger 등
    ├── security      # JWT 필터/프로바이더, @AdminOnly AOP
    ├── common        # 로깅 필터, API 버저닝(SemanticVersion), CommonApi(presign)
    └── error         # ErrorCode, 전역 예외 핸들러
```

규모: main 약 250개 파일 / 15,000 LOC. 전형적 God Class는 없으나 `CourseFacade`(357줄), `CourseReadModel`(379줄)이 최대.

## 도메인 이벤트 흐름 (핵심 설계)

도메인 간 통신은 대부분 Spring `ApplicationEventPublisher` + `@TransactionalEventListener`로 이뤄진다.

```
RunningCommandService (러닝 생성/수정 TX)
 │ publish
 ├─ RunFinishedEvent ──► member.RunFinishedEventListener   (BEFORE_COMMIT) VDOT 계산·upsert
 ├─ RunFinishedEvent ──► course.CourseCacheEventListener   (AFTER_COMMIT)  Redis 코스 캐시 무효화
 ├─ RunUpdatedEvent  ──► course.CourseCacheEventListener   (AFTER_COMMIT)  캐시 무효화
 ├─ CourseRunEvent   ──► course.CourseSubscriptionEventListener (BEFORE_COMMIT) 구독 멱등 생성
 └─ CourseRunEvent   ──► notification.PushEventListener    (AFTER_COMMIT)  "내 코스를 남이 달림" / "개인 최고기록" 푸시

NoticeService.activate() ─ NoticeActivatedEvent ─► PushEventListener (AFTER_COMMIT) 공지 브로드캐스트
PacemakerLlmCallbackService ─ PacemakerCreatedEvent ─► PushEventListener (AFTER_COMMIT) 페이스메이커 완성 푸시

[2026-08 리드모델 리팩토링 이후] 리드모델(CourseReadModel) 동기화는 이벤트가 아니라
RunningWriter/CourseWriter → course.CourseReadModelWriter **직접 호출**(같은 TX, X락)로 수행.
구 ReadModelSyncListener는 삭제됨. 상세: docs/refactoring/course-read-model/core/04-detailed-design.md

[2026-08 Writer 분리 이후] DB 쓰기 트랜잭션 경계는 도메인별 Writer 빈으로 통일 —
러닝 생성·수정·삭제는 running.RunningWriter(구 RunningCreationWriter), 코스 저장·수정·삭제는
course.CourseWriter가 연다. RunningCommandService는 트랜잭션 밖 조율
(가공·S3 업로드·VDOT)과 Writer 위임만 남았다. CourseRunEvent 발행 지점도 RunningWriter다.
```

## Reader/Writer 계층 규칙 (running·course 도메인)

2026-08 리팩토링으로 **running·course 두 도메인은 리포지토리 접근을 Reader/Writer로 격리**한다.
(설계: `docs/design/reader-writer-layering.md`)

```
api ──► Service/Facade ──► Writer ──► Repository     (쓰기: 진입점은 Service/Facade)
api ──► Reader ──────────────────────► Repository     (조회: 조립이 필요할 때만 Facade 경유)

Writer ──► Reader            허용 (쓰기 TX 안 재조회 — Reader는 호출자 TX에 참여)
Writer ──► 타 도메인 Writer   허용 (RunningWriter → CourseWriter.save)
Service/Facade ──► Repository  금지
Reader ──► 쓰기               금지
```

- **Repository를 보는 것은 Reader와 Writer뿐이다.** Service/Facade에는 조율만 남는다.
- **쓰기 `@Transactional`은 Writer에만 있다.** Reader는 쓰기 트랜잭션을 열지 않는다. 다만 **트랜잭션 경계를 클래스 이름만으로 단정하지 말 것** — 예외가 2건 있다.
  - `RunningReader`는 `@Transactional(readOnly = true)`를 갖는다(허용). 호출자가 TX를 열지 않는 조회 경로(`CourseFacade`의 고스트 페이징·TOP 랭킹·상위 퍼센트·코스 통계)를 스스로 감싸기 위함이고, `open-in-view: false`라 **떼면 지연 로딩이 깨진다.** 쓰기 TX 안에서 호출되면 호출자 TX에 참여한다.
  - `CourseSubscriptionWriter`는 이름이 Writer지만 자체 TX를 열지 않고 호출자(`CourseWriter`/`RunningWriter`) TX에 참여한다.
- 구성: `RunningReader`/`RunningWriter`, `CourseReader`/`CourseWriter`, `CourseSubscriptionWriter`(구독 테이블 쓰기의 단일 지점 — 러너 구독과 코스 주인 구독 모두 담당).
- **예외 2건**: `CourseMapCacheEvictor`(커밋 후 콜백이라는 특수 실행 문맥의 캐시 인프라), `RegionResolver`(`@Transactional(NEVER)`가 계약이라 "Writer = 쓰기 TX를 연다" 규칙을 적용할 수 없어 Resolver로 명명).
- 다른 도메인(member, notice, device, auth, pacemaker)은 **적용 대상이 아니다** — 트랜잭션 경계가 단순해 Service–Repository로 충분하고, 기계적 복제는 단순함만 잃는다.

- BEFORE_COMMIT 리스너(VDOT, 구독)는 **원 트랜잭션에 합류** → 강한 정합성, 대신 러닝 생성 TX가 길어짐.
- AFTER_COMMIT 리스너(캐시, 푸시)는 부수효과로 분리.
- 리드모델 동기화는 직접 호출로 전환됨 (이벤트의 실질 이득이 없는 동일 TX 동기 경로였기 때문 — 04 문서 §6 참고).

## 러닝 생성 파이프라인 (`RunningCommandService`)

```
POST /v1/runs (multipart: 텔레메트리 파일 + 기록)
 → TelemetryProcessor.process        # raw → 보간(interpolated)
 → PathSimplificationService         # PathSimplifier (RDP/VW 알고리즘)
 → S3 업로드 5종                      # raw/interpolated/simplified/checkpoints(jsonl), screenshot(jpg)
 → Course 저장(신규 코스인 경우) + Running 저장   # RunningWriter의 저장 TX
 → 도메인 이벤트 발행 (위 흐름)
```

## 페이스메이커 LLM 파이프라인 (가장 복잡한 흐름)

> ⚠️ **리팩토링 방향 확정**: 서킷브레이커·복구 워커는 제거 예정. 트랜잭션 분리 구조(TX1/TX2/LLM 밖)를 중심으로 재구성한다. 아래는 현재 상태 기록.

```
POST /v1/pacemaker
 → PacemakerFacade
   1. PacemakerRateLimitService.incrementCounter     # Redis Lua, 하루 3회 제한
   2. [TX1] PacemakerCreationService                  # VDOT 조회 → 워크아웃 템플릿 선택·스케일링
      → Pacemaker(INIT) + PacemakerSet(message=null) 저장
      (실패 시 카운터 보상: @Retryable + @Recover)
   3. PacemakerLlmTriggerService.processAsync         # @Async("llmTaskExecutor")
      → 서킷 OPEN이면 즉시 FAILED("FALLBACK")
      → [TX2, REQUIRES_NEW] PacemakerStatusService.updateToProceeding
      → (트랜잭션 밖) PacemakerLlmService.requestLlm…  # ⚠️ 또 @Async(같은 풀) — 요청당 스레드 2개
         → LLM 2단계 호출: improveWorkout → fillVoiceGuidance
   4. PacemakerLlmCallbackService.handleSuccess/Error # 멱등 체크 → COMPLETED 전이 + set message 채움
      실패 시: rate limit 카운트 복구 + FAILED

클라이언트는 GET /v1/pacemaker/{id} 폴링으로 완성 확인.
```

- **복구 워커** (`PacemakerRecoveryWorker`, 제거 예정): `@Scheduled(60s)` + ShedLock. 20분 이상 INIT/PROCEEDING 상태인 건을 최대 10건 재처리. ⚠️ `@Transactional(readOnly=true)` 안에서 LLM 호출(최대 180s×2)을 수행해 커넥션을 점유하는 문제 있음.
- **서킷브레이커** (제거 예정): Resilience4j `llmApi` 인스턴스를 빈 주입 후 `getState()==OPEN`만 체크. 실제 호출이 서킷에 기록되지 않아 **서킷이 열릴 수 없는 구조** — 사실상 동작하지 않음.
- LLM 클라이언트: `PacemakerOpenAiRestClient` → OpenAI `/v1/responses`, 자체 재시도 루프(2회, `Thread.sleep` 백오프), read timeout 180s.
- Graceful shutdown: `server.shutdown: graceful` + `timeout-per-shutdown-phase: 480s` + `llmTaskExecutor` awaitTermination 480s — 진행 중 LLM 호출 완주 보장.

## 푸시 알림 파이프라인

```
도메인 이벤트 (AFTER_COMMIT)
 → PushEventListener → PushContentAssembler        # 제목/본문/딥링크/대상 앱버전 범위
 → PushService → DeviceService                     # 대상 기기 조회 (앱 버전 필터, MemberSettings.pushAlarmEnabled)
 → PushHistory 저장 (status=CREATED)
 → PushSqsSender.sendMany (10건 배치) → SQS
 → PushSqsWorker (@SqsListener)
    → PushIdempotencyService (Redis SETNX 멱등 락)
    → ExpoPushClient → Expo Push API
    → 성공: DELIVERED / 무효 토큰: Device 삭제 / 실패: DLQ → Discord 웹훅 알림
```

## 인증 흐름

```
앱 → Firebase 로그인 → idToken
POST /v1/auth/firebase-signin (Authorization: idToken)
 → FirebaseUidResolver (FirebaseAuth로 uid 검증)
 → MemberAuthInfo.externalAuthUid 매칭
 → 자체 JWT 발급 (JwtTokenFactory, HMAC-SHA, 클레임 userId=memberUuid)
 → 리프레시 토큰은 Redis "RT:{memberUuid}"에 저장
reissue 시 저장값 불일치 → 삭제 + TokenTheftException (탈취 감지)
```

- `SecurityConfig`: STATELESS, **블랙리스트 방식**(`AUTH_BLACKLIST`에 등록된 경로만 `authenticated()`, 나머지 `permitAll()`) ⚠️ 신규 API 인증 누락 위험
- 어드민: `@AdminOnly` 어노테이션 + `AdminOnlyAspect`(AOP)

## 도메인 간 결합 현황 (리팩토링 핵심 대상)

| 결합 | 내용 |
|---|---|
| `member` → `pacemaker` | `RunFinishedEventListener`가 `pacemaker.VdotService` 직접 호출 |
| `pacemaker` → `running` | 레이트리밋에 `running.infra.redis.RedisRunningRepository` 사용, `running`의 예외(`RunningNotFoundException`)를 pacemaker 조회 실패에 던짐 |
| `member` 엔티티 → LLM | `Member.toStringForPacemakerPrompt()` 프롬프트 생성 |
| `running` → `course` | 러닝 생성 시 Course 생성/검증 직접 수행 (이벤트 아닌 직접 호출) |
| `notification` → `device`, `member` | 푸시 대상 조회 — 방향은 자연스러우나 `PushEventListener`가 4개 도메인 이벤트를 모두 수신 |
