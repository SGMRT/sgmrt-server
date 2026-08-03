# 04. 인프라 및 환경

## 배포 아키텍처 (현재)

```
개발자 → GitHub → AWS CodePipeline (CodeBuild → S3 아티팩트)
                          ↓
클라이언트 → Route53 → [VPC]
                        ├─ 퍼블릭 서브넷: ALB, NAT Gateway, Bastion Host
                        └─ 프라이빗 서브넷: Elastic Beanstalk
                            ├─ EC2 × 2 (Docker + Spring/Tomcat)
                            ├─ ElastiCache (Redis)
                            ├─ RDS (MySQL) ← dev/prod DB 스키마 분리, 서버는 공유
                            └─ S3
외부: OpenAI API (NAT 경유), CloudWatch/Sentry/Prometheus (로그·메트릭)
```

- 빌드: `buildspec.yml` (CodeBuild, corretto17, Parameter Store에서 Firebase 키 주입)
- EB 설정: `.platform/nginx/conf.d/client_max_body_size.conf`
- **전환 계획**: Elastic Beanstalk → Docker/Kubernetes. **RDS는 유지.**

## 프로파일 / 환경

| 프로파일 | 파일 | 특징 |
|---|---|---|
| local | `application-local.yml` | localhost MySQL/Redis, `ddl-auto: create` + data.sql |
| dev | `application-dev.yml` | 실 RDS의 **dev 전용 DB** 사용, `ddl-auto: create` + 더미데이터(의도된 초기화), Prometheus 노출, Sentry dev |
| prod | 리포에 파일 없음 | **EB 환경변수/외부 주입**으로 설정. logback만 prod 프로파일 분기 존재 |
| test | `test/resources/application.yml` | Testcontainers용 |

- RDS 서버는 dev/prod가 공유하되 DB(스키마)가 분리되어 있어 dev의 `ddl-auto: create`는 의도된 동작.
- ⚠️ 시크릿이 local/dev yml에 평문 커밋됨 → [01-overview.md](01-overview.md) 보안 이슈 참조.

## 데이터 저장소

### MySQL (RDS)
- Hibernate 6 + QueryDSL 5 (jakarta). `open-in-view: false`, `default_batch_fetch_size: 100`
- `schema.sql`은 ShedLock 테이블만 정의. 그 외 스키마는 ddl-auto 의존 (**마이그레이션 도구 없음** — Flyway 등 도입 후보)
- QueryDSL 사용처: `RunningQueryRepositoryImpl`(311줄), `CustomCourseRepositoryImpl`, `CustomDeviceRepositoryImpl`(SemVer 3필드 비교)

### Redis (ElastiCache)
| 용도 | 키 패턴 | 구현 |
|---|---|---|
| 리프레시 토큰 | `RT:{memberUuid}` | `RefreshTokenService` |
| 코스 조회 캐시 | `course:{id}` (TTL 60분, MSET/MGET) | `course/dao/CourseCacheRepository` |
| 푸시 멱등 락 | `push:idempotency:{uuid}:{token}` (SETNX) | `PushIdempotencyService` |
| 페이스메이커 일일 한도 | `pacemaker_api_rate_limit:{uuid}:{날짜}` (TTL 86400) | Lua 스크립트 |

- Lua: `lua/rate-limiter.lua`(원자적 한도 체크+INCR+EXPIRE), `lua/rate-liter-compensation.lua`(보상 DECR)
- ⚠️ Redisson 분산락(`RedisDistributedLockManager`, `RedisRunningRepository.getLock()`)은 **호출처 없는 데드코드**

## AWS 서비스

### S3
- `global/clients/aws/s3/GhostRunnerS3Client` — JSONL 직렬화 업로드 + MultipartFile 업로드
- `GhostRunnerS3PresignUrlClient` — 프로필 이미지 PUT presign (10분, jpg/jpeg만)
- 저장 파일: 러닝 텔레메트리(raw/보간/간소화/체크포인트 jsonl), 러닝 스크린샷, 공지 이미지, 프로필 이미지

### SQS
- `SqsTemplate` + `SqsMessageListenerContainerFactory` (ACK ON_SUCCESS, 동시성 10, poll 20s)
- 큐: 푸시 메인 큐 + DLQ. 프로듀서 `PushSqsSender`, 컨슈머 `PushSqsWorker`
- ⚠️ **dev 설정에서 main/dlq 큐 이름이 서로 뒤바뀌어 있음** (확인 필요)
- 테스트/로컬: `cloud.aws.sqs.endpoint` 설정 시 LocalStack override

### CloudWatch Logs
- `logback-spring.xml`의 `AwsLogsAppender` — dev/prod 별도 로그 그룹
- prod는 `MaskingPatternLayout`으로 러닝 민감 필드(페이스, bpm, 텔레메트리 등) 마스킹

## 외부 서비스

| 서비스 | 용도 | 구현 |
|---|---|---|
| OpenAI | 페이스메이커 LLM (`/v1/responses`, 모델 gpt-5) | `PacemakerOpenAiRestClient` (read timeout 180s) |
| Firebase Auth | 소셜 로그인 idToken 검증 | `FirebaseConfig` + `FirebaseUidResolver` (키 파일은 CI가 생성) |
| Expo Push | 푸시 발송 | `ExpoPushClient` (HttpClient5 풀 6, 전용 executor) |
| Discord | 푸시 실패/DLQ 알림 웹훅 | `DiscordWebhookClient` |
| Sentry | 에러 트래킹 + AOP 스팬(`SentrySpanAspect`) | prod ERROR 이상 |

## 관측성 / 공통 기반

- **로깅**: `LogFilter`(requestId MDC) + `HttpLogger`, `MdcTaskDecorator`(비동기 MDC 전파)
- **메트릭**: Actuator + Prometheus (dev 노출)
- **API 버저닝**: `SemanticVersion`/`VersionRange` — Device 앱버전 기반 푸시 필터·딥링크 분기
- **스케줄러 분산락**: ShedLock(JDBC) — 현재 사용처는 `PacemakerRecoveryWorker`뿐 (워커 제거 시 ShedLock 필요성 재검토)
- **Swagger**: springdoc 2.7.0

## 테스트 환경

- `IntegrationTestSupport`: `@SpringBootTest` + Testcontainers **MySQL 8 + Redis 7 + LocalStack(SQS)** static 기동, `@DynamicPropertySource` 주입
- `ApiTestSupport`, `DatabaseCleanserExtension`
- CI: GitHub Actions (`main-ci.yml`, `dev-ci.yml`) — PR 시 `./gradlew test`
- ⚠️ 툴체인 불일치: Gradle Java 17 / CodeBuild corretto17 / GitHub Actions **JDK 21**
