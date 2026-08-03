# 01. 서비스 개요

## GhostRunner란

러닝 앱 백엔드. 핵심 컨셉은 두 가지다.

1. **고스트 러닝** — 같은 코스에서 과거의 나 또는 다른 러너의 기록("고스트")과 경쟁하며 달린다. 러닝 기록은 코스에 귀속되고, 코스별 랭킹/고스트 비교가 제공된다.
2. **AI 페이스메이커** — VDOT(잭 다니엘스 러닝 공식) 기반 워크아웃 템플릿을 산출한 뒤, LLM(OpenAI)이 이를 다듬고 구간별 음성 가이드 메시지를 생성한다.

- **운영 상태**: 실서비스 운영 중 (앱 클라이언트 배포됨)
- **팀/출처**: SW마에스트로 프로젝트 출신 (`group = soma`, Jira 프리픽스 `SGMR`)
- **기술 스택**: Java 17, Spring Boot 3.5, JPA(Hibernate 6) + QueryDSL, MySQL(RDS), Redis(ElastiCache), AWS(S3/SQS/CloudWatch), OpenAI API, Firebase Auth, Expo Push, Sentry, Prometheus

## 도메인 구성

| 도메인 | 책임 |
|---|---|
| `running` | 러닝 기록 생성/조회, 텔레메트리(경로) 처리·간소화, S3 업로드 |
| `course` | 코스 CRUD, 위치 기반 지도 조회(캐싱), 랭킹/통계, 읽기모델(CQRS), 구독 |
| `pacemaker` | VDOT 계산, 워크아웃 생성, LLM 파이프라인, 복구 워커, 레이트리밋 |
| `member` | 회원 프로필/설정/약관/VDOT 저장 |
| `auth` | Firebase 로그인 → 자체 JWT 발급, 리프레시 토큰(Redis), 토큰 탈취 감지 |
| `device` | 기기/푸시 토큰 등록, 앱 버전(SemVer) 관리 |
| `notification` | 푸시 발송(SQS 경유 → Expo), 푸시 이력, 어드민 브로드캐스트 |
| `notice` | 공지사항 CRUD/활성화, 공지 dismissal, 공지 푸시 트리거 |

## 리팩토링 제약 및 방향 (개발자 확정 사항)

1. **외부 API 절대 불변** — 앱 클라이언트가 배포되어 있어 요청/응답 스펙, 경로 변경 불가. 내부 구조만 변경한다. → [05-api.md](05-api.md)가 계약 문서.
2. **리팩토링 목적**: 구조/설계 개선(도메인 간 결합 해소 중심) + 코드 품질/중복 제거.
3. **LLM 파이프라인 단순화**: 서킷브레이커(Resilience4j), 재시도/복구 워커는 **코드에서 제거 예정**. 트랜잭션을 분리해둔 구조(TX1 생성 / TX2 상태전이 / LLM 호출은 트랜잭션 밖)를 중심으로 재구성한다.
4. **인프라 전환 예정**: 현 Elastic Beanstalk 기반 → Docker/Kubernetes로 전환 계획. **MySQL은 RDS 유지.** 백엔드 코드는 컨테이너 친화적으로 유지.

## 문서에 담지 않은 보안 이슈 (긴급)

`application-local.yml`, `application-dev.yml`에 실제 시크릿(AWS 키, RDS 비밀번호, OpenAI API 키, JWT 시크릿, Discord 웹훅)이 평문 커밋되어 있다. **값 로테이션 + Parameter Store/Secrets Manager 이전 + git 히스토리 정리**가 리팩토링과 별개로 선행되어야 한다.
