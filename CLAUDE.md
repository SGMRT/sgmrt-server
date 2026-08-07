# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 협업 원칙

- **개발자가 구현을 제안하면 바로 구현하지 말 것.** 먼저 테크리더 관점에서 냉정하게 평가한 뒤 구현한다:
  1. 제안이 맞는 방향인지 — 틀렸거나 더 나은 방법이 있으면 근거와 함께 반대 의견을 낸다
  2. 트레이드오프 — 얻는 것과 잃는 것, 감수해야 할 비용을 명시한다
  3. 평가를 먼저 말하고, 그 다음에 구현을 진행한다
- 동의하기 어려운 제안에 동조하지 않는다. 설득이 안 되면 안 되는 이유를 남기고 개발자의 결정을 따른다.

## 프로젝트 소개

GhostRunner는 러닝 측정, 주변 코스 등록 및 탐색, 과거 기록과 경쟁 기능을 제공하는 러닝 애플리케이션입니다.

## 주요 명령어

```bash
# 빌드
./gradlew build
./gradlew clean build

# 로컬 실행 (MySQL 직접 설치 필요)
./gradlew bootRun --args='--spring.profiles.active=local'

# 로컬 실행 - TestContainers로 MySQL/Redis/SQS 자동 구성
./gradlew localRun

# 전체 테스트 실행
./gradlew test

# 특정 클래스 혹은 메소드 테스트
./gradlew test --tests "soma.ghostrunner.domain.running.api.RunningApiTest"
./gradlew test --tests "soma.ghostrunner.domain.running.api.RunningApiTest.testCreateRun"

# 테스트 출력물 확인
./gradlew test --info
```

## 프로젝트 아키텍처

- Spring Boot 3.5, Java 17
- MySQL, Redis, Amazon SQS, Amazon S3
- Prometheus
- JUnit5, TestContainers

### 도메인 패키지 레이아웃 (`soma.ghostrunner.domain`)

```
soma.ghostrunner.domain.<domain>/
  api/          ← @RestController 계층
  application/  ← @Service 계층
  domain/       ← 핵심 도메인 계층
  infra/        ← @Repository 계층 (JPA, QueryDSL, Redis) 및 외부 Client 접근
```

**도메인:**
- `course`
  - 러닝 코스. Running 여러 개(1..N)를 포함
- `running`
  - 사용자가 측정한 러닝 기록 하나. 일반 러닝 시 Running + Course 동시 생성, 코스 따라 러닝 시 기존 Course에 Running 추가. 
  - 사용자의 VDOT 기반으로 Pacemaker(AI 코칭) 생성
- `member`
  - 회원 (프로필, 생체 정보, VDOT)
- `auth` 
  - JWT 기반 인증/인가, Refresh Token
- `notification` 
  - 푸시 알림
- `notice` 
  - 공지사항
- `device` 
  - 접속 기기 토큰 관리

### 글로벌 패키지 레이아웃 (`soma.ghostrunner.global`)

```
soma.ghostrunner.global/
  config/    ← 스프링 빈 설정 및 등록 (Security, Redis, AWS SDK, OpenAI, Swagger)
  security/  ← JWT 인증 로직 (JwtAuthFilter, JwtProvider), Member Identity는 커스텀 Argument Resolver로 주입
  clients/   ← 외부 클라이언트 (S3, Discord 등)
  common/    ← 로깅, validator, BaseTimeEntity 등
  error/     ← GlobalExceptionAdvice와 ErrorCode, 모든 비즈니스 예외는 GhostRunnerException 상속
```

### 주요 결정 사항

- **AWS 서비스**
  - S3: GPS 경로 좌표 데이터 및 이미지(코스, 멤버 프로필 등)는 DB가 아닌 S3에 저장
  - SQS: 푸시 알림 이벤트는 SQS를 통해 발행
- **Soft Delete** — 주요 도메인 객체(`Running`, `Course`, `Member`)는 Soft Delete 정책 사용
- **QueryDSL** — 동적 필터링 쿼리는 QueryDSL로 작성 (주변 코스 조회, 러닝 기록 페이징 등)
- **Redis** — 데이터 캐싱, 처리율 제한(`RedisRateLimiterRepository`), 분산락(`RedisDistributedLockManager`), Refresh Token 저장
- **이벤트 발행** — `ApplicationEventPublisher` 또는 `@DomainEvents` 활용
- **MapStruct** — 모든 컨트롤러 ↔ 서비스 DTO 변환은 MapStruct 사용, Mapper는 `api/`에 생성
- **푸시 알림** — Expo Push Service 활용 (`PushService`)

### 애플리케이션 프로필

| 프로필 | 목적 |
| --- | --- |
| `local` | 로컬 MySQL, 더미 AWS 토큰 |
| `dev` | 개발 서버 |
| `prod` | 운영 서버 |
| `load-test` | 부하 테스트 |

### 테스트 구조

`src/test/java/soma/ghostrunner/` 참고

- `IntegrationTestSupport` — TestContainers 실행 (MySQL 8.0, Redis 7, LocalStack SQS), repository/service 계층 통합 테스트에서 상속
- `ApiTestSupport` — `@WebMvcTest` 활용, `@MockitoBean`으로 서비스 모킹, controller 계층 테스트에서 상속
- `DatabaseCleanserExtension` — JUnit5 익스텐션, 각 테스트 후 테이블 truncate
