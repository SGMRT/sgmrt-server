# GhostRunner 백엔드 문서

리팩토링을 위한 프로젝트 이해 문서. 2026-08 코드베이스 전수 분석 + 개발자 문답을 바탕으로 작성.

| 문서 | 내용 |
|---|---|
| [01-overview.md](01-overview.md) | 서비스 개요, 운영 상태, 리팩토링 제약/방향 |
| [02-domain-model.md](02-domain-model.md) | 도메인별 JPA 엔티티, 관계, 소프트삭제 패턴 |
| [03-architecture.md](03-architecture.md) | 패키지/레이어 구조, 도메인 이벤트, 비동기 파이프라인(페이스메이커·푸시) |
| [04-infrastructure.md](04-infrastructure.md) | AWS 인프라, Redis, 프로파일/환경, 배포, 관측성 |
| [05-api.md](05-api.md) | 외부 API 인벤토리 (**불변 계약** — 리팩토링 시 변경 금지) |
| [06-refactoring-notes.md](06-refactoring-notes.md) | 발견된 문제점 목록과 리팩토링 백로그 |
