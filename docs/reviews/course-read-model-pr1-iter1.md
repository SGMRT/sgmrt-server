# 코드 품질 리포트 — CourseReadModel PR-1 (쓰기 측)

- 대상: `docs/refactoring/course-read-model/04-detailed-design.md` PR-1 범위
- 리뷰 회차: iteration 1
- 리뷰 방식: 코드 리딩 중심 (전체 테스트 685개 그린 상태 전제, 실행 검증 생략)

---

## 총평

먼저 칭찬부터 하겠습니다. 이 PR은 제가 최근 본 리팩토링 중에 **"왜 이렇게 했는지"가 코드에 남아 있는** 드문 케이스입니다.

`TopRunners`라는 불변 VO 하나로 순위 규칙을 걷어내고, 엔티티는 매핑과 변경 감지만 남기고, X락은 `CourseReadModelWriter` 안으로만 몰아넣은 구조는 교과서적입니다. 특히 `@Transactional(propagation = MANDATORY)`를 선택한 건 제가 박수를 보내고 싶은 결정입니다. 제가 예전에 겪은 사고 중 최악은 "리드모델 갱신 코드가 트랜잭션 밖에서 돌고 있었는데 아무도 몰랐던" 케이스였습니다. 조용히 커밋되고, 조용히 어긋나고, 3개월 뒤에 데이터가 안 맞는다는 CS로 발견됐죠. MANDATORY는 그 함정을 **런타임에 폭발시키는** 선택이고, 이건 "안전하지 않은 상태를 표현 불가능하게 만든다"는 원칙 그대로입니다.

`ReadModelSyncListener`를 지우면서 이중 반영 경로를 없앤 것, `insertIfBetter` 시프트/스와프 200줄을 지운 것, 데드 쿼리(`countDistinctRunnersByCourseId`)까지 정리한 것 — 리팩토링이 "추가"가 아니라 "제거"로 끝났다는 게 이 PR의 가장 큰 미덕입니다.

테스트도 좋습니다. 특히 `CourseReadModelWriterTest`의 MANDATORY 계약 테스트와 실제 스레드 2개로 돌린 X락 동시성 테스트, `CourseReadModelRepositoryTest`의 `@Embedded` null 슬롯 왕복 테스트는 "이거 깨지면 전부 무너지는 지점"을 정확히 겨냥했습니다. 테스트 개수가 아니라 **테스트가 겨냥한 지점**이 좋습니다.

이제 우려 사항입니다. 두 가지가 걸립니다.

첫째, **테스트 685개가 그린인데 dev 서버는 부팅에 실패할 겁니다.** `CourseProfile`을 `@Embedded`로 붙이면서 `distance_km NOT NULL` 컬럼이 `course_read_model`에 생겼는데, `data.sql`의 리드모델 INSERT는 그 컬럼을 채우지 않습니다. 테스트 프로파일은 `data.sql`을 안 타서 초록불이 켜져 있을 뿐입니다. 이게 제가 이 리뷰에서 가장 하고 싶은 말입니다 — **테스트 그린은 "안 깨졌다"가 아니라 "테스트가 보는 범위 안에서 안 깨졌다"입니다.**

둘째, **관측 가능성(observability)이 후퇴했습니다.** 기존 `ReadModelSyncListener`에는 `log.warn("ReadModel not found for course={}")`가 있었습니다. 지금 Writer는 리드모델이 없으면 `return;` 한 줄로 조용히 끝납니다. 6개월 뒤 "어떤 코스만 TOP4가 안 채워져요"라는 제보를 받았을 때, 로그에 아무 흔적이 없으면 새벽 3시에 코드를 정독해야 합니다.

나머지는 다듬는 수준입니다. 구조 자체는 훌륭하고, 아래 CRITICAL 2건만 처리하면 저는 이 코드를 프로덕션에 내보내는 데 동의합니다.

---

## 총점: 83/100

## 등급: **A** (약간의 개선 후 출시 가능)

단, **CRITICAL-1은 dev 배포 전 필수**입니다. 등급이 A인 이유는 구조·설계·테스트가 모두 견고하고, 발견된 CRITICAL 2건이 모두 **구조 문제가 아니라 배포 아티팩트 누락**이기 때문입니다. 한 줄 수정과 절차 하나로 해결됩니다.

---

## 차원별 점수

| 차원 | 점수 | 핵심 피드백 |
|------|------|-------------|
| 가독성 | 9/10 | 네이밍이 의도를 그대로 드러냄(`isReadModelAggregationTarget`, `distinctCourseIdsOf`). javadoc이 "무엇"이 아니라 "왜"를 설명 |
| 아키텍처 준수 | 8/10 | Writer 단일 진입점·X락 내재화는 모범적. `CourseReadModelRepository`가 JPQL로 `Running` 엔티티를 참조해 도메인 경계를 넘음 |
| 단일 책임 | 9/10 | 엔티티=매핑+변경감지, VO=순위규칙, Writer=락+조율로 깔끔히 분리. `CourseService`에서 리드모델 관심사 완전 제거 |
| 캡슐화 | 9/10 | 불변 record + 방어적 복사 + private 헬퍼. 다만 엔티티가 잘못된 입력을 조용히 무시(`rename`, `updateRunnersCount`) |
| 테스트 품질 | 9/10 | MANDATORY 계약·실스레드 동시성·`@Embedded` 왕복 등 "깨지면 치명적인 지점"을 정확히 겨냥. given/when/then·한글 네이밍 준수 |
| 에러 처리 | 7/10 | MANDATORY 페일패스트는 훌륭. 그러나 스킵 경로 전부가 무로그 — 기존 리스너의 `warn`이 사라져 운영 관측성 후퇴 |
| 성능 | 7/10 | 변경감지 UPDATE 생략·EXISTS 전환·LIMIT 4 루즈 인덱스는 좋음. 락 순서 미정렬(데드락), 모든 공개 러닝마다 X락, 코스당 3쿼리 루프 |
| 보안 | 9/10 | 전 쿼리 파라미터 바인딩, 로그에 PII 없음, 소유권 검증 유지. 신규 취약점 없음 |
| 설계 일치도 | 8/10 | Q1~Q4 전부 구현, Writer API 시그니처 일치. 설계 문서가 스스로 경고한 DDL 선적용이 저장소 아티팩트에 미반영 |
| 유지보수성 | 8/10 | 문서화된 계약 + 데드코드 제거. `MAX_RANK`↔`LIMIT 4` 암묵 결합, 암묵적 flush 순서 의존이 지뢰 |

---

## 잘한 점

### 1. `@Transactional(propagation = MANDATORY)` — "조용히 안 타는 함정"의 반대 성질

`CourseReadModelWriter.java:42`

```java
@Transactional(propagation = Propagation.MANDATORY)
public class CourseReadModelWriter {
```

리드모델 갱신이 원본(러닝/코스) 변경과 원자적으로 커밋돼야 한다는 요구를 **타입 시스템 대신 런타임 계약으로 강제**했습니다. `REQUIRED`였다면 호출자가 트랜잭션 없이 불러도 새 트랜잭션이 열려서 "일단 돌긴 하는데 원자성은 깨진" 상태가 됩니다. 이건 로컬·테스트에서는 절대 안 잡히고 운영에서 부분 커밋으로 터집니다. MANDATORY는 그 상황을 즉시 예외로 만듭니다.

여기에 `CourseReadModelWriterTest.applyRun_withoutTransaction_throws()`로 **계약 자체를 테스트로 고정**한 것까지 세트로 좋습니다. 누군가 나중에 `REQUIRED`로 바꾸면 테스트가 먼저 깨집니다.

### 2. 순위 규칙을 불변 VO로 뽑아낸 것 — 200줄이 사라진 이유

`TopRunners.java:24-51`

```java
public TopRunners with(Long memberId, int timeSeconds) {
    if (!qualifies(memberId, timeSeconds)) {
        return this;                      // 변화 없으면 동일 인스턴스
    }
    ...
}

private boolean qualifies(Long memberId, int timeSeconds) {
    RankSlot myBestSlot = findByMember(memberId);
    if (myBestSlot != null) {
        return timeSeconds < myBestSlot.getTimeSeconds();   // 본인 슬롯 확인이 먼저
    }
    if (slots.size() < MAX_RANK) {
        return true;
    }
    ...
}
```

기존 `insertIfBetter`의 시프트/스와프는 "4개 슬롯을 손으로 밀어내는" 코드였습니다. 이런 코드는 **경계 조건에서 반드시 버그가 납니다** — 슬롯이 3개일 때, 본인이 이미 3위일 때, 동률일 때. 실제로 설계 문서에도 "구현 중 순서 버그 발견·수정"이 기록돼 있고, 코드는 그 수정된 순서(본인 슬롯 확인 → size 검사)를 정확히 반영하고 있습니다.

이걸 리스트 조작 + 정렬로 바꾸면 경계 조건이 **정렬 알고리즘 안으로 흡수**됩니다. 그리고 `TopRunnersTest`가 이 전환의 안전망 역할을 합니다:
- `existingMemberSlowerRecordWhenNotFullKeepsBestRecord()` — 바로 그 순서 버그를 겨냥한 테스트
- `tieWithLastSlotKeepsExistingRank()` / `tieWithOwnRecordKeepsExistingRank()` — strict `<` 정책 고정

정렬 안정성(TimSort)에 기대어 "동률이면 기존 순위 유지"가 성립하는 것도 맞게 동작합니다. 새 슬롯을 `add`로 뒤에 붙인 뒤 안정 정렬하므로 동률 시 기존 원소가 앞에 남습니다.

### 3. "증분은 근사, 재계산은 진실의 원천" 이라는 이분법을 코드가 지킴

`CourseReadModelWriter.java:145-154`

```java
private void recalculateReadModel(CourseReadModel readModel) {
    List<RankSlot> topRankedSlots = readModelRepository.findTop4RunnersByBestDuration(courseId).stream()
            .map(this::toRankSlot).toList();
    readModel.replaceTopRunners(new TopRunners(topRankedSlots));
    readModel.updateRunnersCount(readModelRepository.countDistinctPublicRunners(courseId));
}
```

증분 방식 집계의 고질병은 **드리프트**입니다. 삭제·비공개 전환·중복 반영·장애 재시도가 쌓이면 값이 서서히 어긋나고, 어긋난 걸 아무도 모릅니다. 여기서는 증분이 못 다루는 변화가 생길 때마다 무조건 COUNT/집계로 덮어쓰는 경로를 뒀고, 그 경로가 **삭제·공개전환·리드모델 생성 세 곳 모두**에 연결돼 있습니다.

`CourseReadModelWriterTest.recalculate_correctsDriftedTopRunnersAndRunnersCount()`가 **유령 러너 + runnersCount=99라는 인위적 드리프트를 심어놓고 정정되는지** 검증한 게 특히 좋습니다. "정상 상황에서 잘 되는지"가 아니라 "망가진 상황에서 복구되는지"를 테스트한 겁니다.

### 4. Q1~Q4 정책이 코드-쿼리-테스트 세 곳에서 일관됨

집계 대상 필터 `is_public ∧ ¬deleted ∧ ¬has_paused`가

- 재계산 쿼리 3종 (`CourseReadModelRepository.java:104-106, 128-131, 152-156`)
- 증분 호출부 가드 (`RunningCommandService.isReadModelAggregationTarget()`)
- 테스트 (`CourseReadModelRecalculationQueryTest` "비공개·삭제·일시정지 러닝은 집계에서 제외된다", `RunningReadModelSyncIntegrationTest`의 일시정지 러너 픽스처)

세 곳에서 같은 모집단을 봅니다. 예전 코드에서 `countDistinctRunnersByCourseId`(일시정지 포함)와 TOP4 집계(일시정지 제외)가 **서로 다른 모집단**을 쓰던 불일치가 정확히 이 방식으로 제거됐습니다. `CourseReadModelRepository.java:117-120` javadoc이 "이름이 비슷한 구 쿼리와 달리 일시정지를 제외한다"고 명시해둔 것도 좋습니다 — 다음 사람이 실수로 옛 쿼리를 갖다 쓰는 걸 막습니다.

### 5. Q3(EXISTS 자기 제외)의 견고함

`CourseReadModelWriter.java:157-161`

```java
private boolean isMembersFirstPublicRun(Long courseId, Long memberId, Long runningId) {
    return !readModelRepository.existsOtherPublicRunByCourseAndMember(courseId, memberId, runningId);
}
```

"러닝 저장 전에 판정한다"가 아니라 "판정 쿼리에서 자기를 제외한다"를 택한 게 정답입니다. 전자는 **호출 순서에 정확성이 종속**되므로, 나중에 누가 저장 순서를 바꾸면 runnersCount가 조용히 2배가 됩니다. 후자는 순서와 무관하게 성립합니다. `applyRun_secondRunOfSameMember_keepsRunnersCount()` 테스트가 이 성질을 고정합니다.

### 6. `CourseService`가 되찾은 책임 경계

리드모델 조작(`syncReadModelPublicity`, `createReadModelForCourse`, 8인자 `replaceTop4` 호출) 60여 줄이 통째로 빠지고 `readModelWriter` 위임 3줄로 대체됐습니다. `CourseService`가 `RunningRepository`를 직접 들고 있던 의존(코스 서비스가 러닝 테이블을 조회)도 사라졌습니다. 클래스 하나가 짧아진 것보다, **"코스 서비스가 왜 러닝 최고기록을 알아야 하지?"라는 질문이 사라진 것**이 본질적인 개선입니다.

---

## 개선 필요 사항

### [CRITICAL-1] `data.sql`이 `course_read_model`의 신규 NOT NULL 컬럼을 채우지 않아 dev/local 부팅이 실패한다

**현재 코드** (`src/main/java/soma/ghostrunner/domain/course/domain/CourseProfile.java:13-14`):

```java
@Column(name = "distance_km", nullable = false)
private Double distance;
```

**현재 코드** (`src/main/java/soma/ghostrunner/domain/course/domain/CourseReadModel.java:63-64`):

```java
@Embedded
private CourseProfile courseProfile;
```

**현재 코드** (`src/main/resources/data.sql:152-159`):

```sql
INSERT INTO course_read_model (
    course_id, name, owner_uuid, source, route_url, start_lat, start_lng,
    top1_member_id, top1_time_seconds,
    top2_member_id, top2_time_seconds,
    top3_member_id, top3_time_seconds,
    top4_member_id, top4_time_seconds,
    runners_count, is_public, created_at, updated_at
)
VALUES
    (1, '한강 러닝 코스', 'user-uuid-001', 'USER', 'https://example.com/route1.json', 37.5219, 127.0411,
     2, 1500, 4, 1600, 1, 1700, 3, 1800, 5, true, NOW(), NOW()),
    ...
```

**문제점**:

`CourseProfile`을 `@Embedded`로 붙이는 순간 Hibernate는 embeddable의 `@Column` 정의를 **그대로** 호스트 테이블에 적용합니다. embeddable이 통째로 null일 수 있다는 사실과 무관하게 `distance_km`는 `NOT NULL`로 DDL이 생성됩니다. 즉 `course_read_model`에 `distance_km DOUBLE NOT NULL` 컬럼이 새로 생깁니다.

그런데 `data.sql`의 리드모델 INSERT는 컬럼 목록에 `distance_km`가 없습니다. `application-dev.yml:22`는 `ddl-auto: create`, `:30-33`은 `defer-datasource-initialization: true` + `spring.sql.init.mode: always`입니다. 즉 **dev 서버는 기동할 때마다 테이블을 재생성하고 `data.sql`을 반드시 실행합니다.**

MySQL이 STRICT 모드(RDS 기본값)이면 이 INSERT는

```
Field 'distance_km' doesn't have a default value
```

로 실패하고, `spring.sql.init.continue-on-error`가 설정돼 있지 않으므로(기본 `false`) `ScriptStatementFailedException` → **ApplicationContext refresh 실패 → 애플리케이션이 아예 안 뜹니다.**

**왜 테스트 685개가 이걸 못 잡았나**: `src/test/resources/application.yml`에는 `spring.sql.init.mode` 설정이 없습니다. 기본값은 `embedded`이고, TestContainers MySQL은 embedded DB가 아니므로 **`data.sql`이 실행되지 않습니다.** 테스트는 초록불인데 dev는 죽는, 전형적인 "테스트가 안 보는 구멍"입니다. 제가 겪은 사고 중 상당수가 이 유형입니다 — CI는 통과했는데 배포하니 안 뜬다.

같은 이유로 `thumbnail_url`(nullable이라 무해)과, 향후 `elevation_*_m`(현재 nullable이라 무해)도 함께 점검해야 합니다. 지금 당장 폭발하는 건 `distance_km` 하나입니다.

**개선 코드** (`src/main/resources/data.sql`):

```sql
INSERT INTO course_read_model (
    course_id, name, owner_uuid, source, route_url,
    distance_km, elevation_average_m, elevation_gain_m, elevation_loss_m, thumbnail_url,
    start_lat, start_lng,
    top1_member_id, top1_time_seconds,
    top2_member_id, top2_time_seconds,
    top3_member_id, top3_time_seconds,
    top4_member_id, top4_time_seconds,
    runners_count, is_public, created_at, updated_at
)
VALUES
    -- 코스 1: 한강 — course 테이블의 코스 1과 동일한 프로필 값을 역정규화 (5.0 / 10.5 / 20.0 / 18.0)
    (1, '한강 러닝 코스', 'user-uuid-001', 'USER', 'https://example.com/routes/1.json',
     5.0, 10.5, 20.0, 18.0, 'https://example.com/thumbnails/1.png',
     37.5283, 126.9340,
     2, 1500, 4, 1600, 1, 1700, 3, 1800, 5, true, NOW(), NOW()),
    ...
```

**개선 이유**:

1. 리드모델은 정의상 `course` 테이블의 역정규화 사본입니다. 시드 데이터도 **원본과 같은 값**이어야 의미가 있습니다. 현재 `data.sql`은 리드모델의 `route_url`/`start_lat`/`start_lng`조차 `course` 테이블 값(`.../routes/1.json`, `37.5283, 126.9340`)과 다른 값을 넣고 있어서, PR-2에서 조회 경로를 리드모델로 전환하는 순간 **dev에서 코스 목록과 지도 응답이 서로 다른 위치·URL을 보여주게 됩니다.** 이번에 컬럼을 채우는 김에 원본과 정렬시키는 게 맞습니다.

2. 근본 대책으로는, 시드 데이터를 손으로 관리하는 대신 **`writer.syncPublicity(courseId, true)`를 태우는 시딩 컴포넌트**로 바꾸는 걸 권합니다. 그러면 리드모델 스키마가 바뀔 때마다 `data.sql`을 따라 고칠 필요가 없고, 백필 로직(PR-2)과도 코드를 공유합니다. 지금처럼 "엔티티는 리팩토링했는데 시드 SQL은 안 따라온" 드리프트가 구조적으로 발생하지 않게 됩니다.

3. 최소한의 재발 방지책으로 dev 프로파일에 `spring.jpa.hibernate.ddl-auto` 대신 스키마 검증을 걸고, `data.sql` 실행 여부를 CI에서 한 번이라도 태우는 스모크 테스트(컨텍스트 로딩 + `sql.init.mode=always`)를 추가하는 것도 고려하십시오.

---

### [CRITICAL-2] prod DDL 선적용이 저장소 어디에도 강제되지 않는다 — 배포 순서가 틀리면 러닝 종료 API 전면 장애

**현재 상태** (`src/main/resources/schema.sql` 전체 12줄):

```sql
-- ShedLock 테이블 (분산 스케줄러 락)
CREATE TABLE IF NOT EXISTS shedlock ( ... );
```

`course_read_model` 관련 DDL은 없습니다. 저장소에 Flyway/Liquibase도 없습니다(`spring.flyway.enabled: false`).

**문제점**:

설계 문서 §4 "운영 주의"가 이미 경고하고 있습니다:

> prod DDL: `ALTER TABLE course_read_model ADD COLUMN ...` 5개 + `MODIFY owner_uuid NULL` — prod의 ddl-auto 설정(외부 주입)이 create가 아니라면 **수동 DDL 선적용 필요**. 배포 전 확인

문서에 적혀 있다는 건 좋습니다. 문제는 **문서는 배포 파이프라인을 막지 못한다**는 겁니다. 지금 상태에서 누군가 이 브랜치를 prod에 배포하면:

- `course_read_model`에 `distance_km`, `elevation_*_m`, `thumbnail_url` 컬럼이 없음
- Hibernate가 생성하는 모든 `INSERT`/`SELECT`가 해당 컬럼을 포함 → `Unknown column 'crm1_0.distance_km' in 'field list'`
- 리드모델을 **읽거나 쓰는 모든 경로**가 SQL 예외 → 러닝 종료(`createRun`), 러닝 삭제, 코스 공개 전환이 전부 500
- 러닝 종료가 실패하면 사용자는 **방금 뛴 기록을 잃습니다.** 리드모델은 부가 데이터인데, MANDATORY로 같은 트랜잭션에 묶여 있어서 리드모델 실패가 러닝 저장을 롤백시킵니다

MANDATORY 선택 자체는 옳습니다(원자성). 다만 그 선택은 **"리드모델 경로가 죽으면 러닝 저장도 죽는다"는 장애 전파를 받아들인 것**이므로, 스키마 정합성이 곧 서비스 가용성이 됩니다. 그만큼 DDL 적용을 사람의 기억에 맡기면 안 됩니다.

추가로 `owner_uuid`는 기존에 `NOT NULL`이었고 이번에 nullable로 완화됐습니다. `MODIFY owner_uuid VARCHAR(36) NULL`을 안 하면 OFFICIAL/더미 코스의 리드모델 생성이 제약 위반으로 실패합니다. 이건 "일부 코스만 안 됨"이라 더 늦게 발견됩니다.

**개선 방안**:

1. **최소안** — 저장소에 배포 아티팩트를 남깁니다.

```sql
-- docs/refactoring/course-read-model/ddl/pr1.sql (배포 전 수동 선적용)
ALTER TABLE course_read_model
    ADD COLUMN distance_km          DOUBLE       NOT NULL DEFAULT 0,
    ADD COLUMN elevation_average_m  DOUBLE       NULL,
    ADD COLUMN elevation_gain_m     DOUBLE       NULL,
    ADD COLUMN elevation_loss_m     DOUBLE       NULL,
    ADD COLUMN thumbnail_url        TEXT         NULL,
    MODIFY COLUMN owner_uuid        VARCHAR(36)  NULL;
```

`DEFAULT 0`을 붙이는 이유는 **기존 행 때문**입니다. `NOT NULL` 컬럼을 기본값 없이 추가하면 기존 행에 대해 MySQL이 암묵적 기본값을 쓰거나(비STRICT) 에러를 냅니다(STRICT + `ALTER`). 명시적 기본값을 주면 결정적으로 동작하고, PR-2 백필이 실제 값으로 덮어씁니다.

2. **권장안** — 이 프로젝트 규모에서 이제 마이그레이션 도구를 도입할 시점입니다. Flyway 의존성만 추가하고 `V1__baseline.sql`(현 스키마 덤프) + `V2__course_read_model_denormalize.sql`을 두면, **애플리케이션 기동 시 스키마 버전이 안 맞으면 뜨지 않습니다.** MANDATORY로 "트랜잭션 없이 도는 함정"을 막은 것과 정확히 같은 철학입니다 — 잘못된 상태를 런타임에 폭발시키는 것.

3. 당장 도입이 어렵다면 최소한 **prod의 `ddl-auto` 실제 값을 확인해 이 리뷰 스레드에 기록**해 주십시오. `update`라면 `ADD COLUMN`은 자동으로 되지만 `MODIFY owner_uuid NULL`은 **되지 않습니다**(Hibernate의 `update`는 컬럼 추가만 하고 기존 컬럼 정의를 바꾸지 않습니다). `validate`나 `none`이면 전면 장애입니다. 어느 쪽이든 수동 DDL이 필요합니다.

**개선 이유**: 스키마 변경은 "코드 배포"와 별개의 릴리스 단위입니다. 이 둘의 순서를 사람이 기억해야 하는 구조는 언젠가 반드시 어긋납니다 — 특히 롤백할 때. 배포 스크립트가 아니라 **애플리케이션이 스스로 거부하게** 만드는 것이 유일하게 확실한 방법입니다.

---

### [MAJOR-1] `recalculate`가 코스 ID를 정렬 없이 순회하며 X락을 잡는다 — 다중 삭제에서 데드락

**현재 코드** (`CourseReadModelWriter.java:79-87`):

```java
public void recalculate(Collection<Long> courseIds) {
    if (courseIds == null || courseIds.isEmpty()) {
        return;
    }
    for (Long courseId : courseIds) {
        readModelRepository.findByCourseIdForUpdate(courseId)
                .ifPresent(this::recalculateReadModel);
    }
}
```

**현재 코드** (`RunningCommandService.java:207-223`):

```java
List<Long> affectedCourseIds = distinctCourseIdsOf(runningsToDelete);

runningRepository.deleteInRunningIds(runningIds);
courseReadModelWriter.recalculate(affectedCourseIds);

private List<Long> distinctCourseIdsOf(List<Running> runnings) {
    return runnings.stream()
            .map(Running::getCourse)
            .filter(Objects::nonNull)
            .map(Course::getId)
            .distinct()          // 순서는 입력 러닝 순서에 종속 — 정렬되지 않음
            .toList();
}
```

**문제점**:

`findByCourseIdForUpdate`는 `PESSIMISTIC_WRITE`, 즉 `SELECT ... FOR UPDATE`입니다. 획득한 행 락은 **트랜잭션 커밋까지 유지**됩니다. 따라서 코스 A, B 두 개를 재계산하는 트랜잭션은 A락 → B락 순으로 락을 **누적**합니다.

락을 여러 개 잡는 트랜잭션에서 **획득 순서가 정해져 있지 않으면 데드락은 시간 문제**입니다. 구체적 시나리오:

```
사용자1: 러닝 삭제 요청 [코스A의 러닝, 코스B의 러닝]  → 락 획득 순서 A → B
사용자2: 러닝 삭제 요청 [코스B의 러닝, 코스A의 러닝]  → 락 획득 순서 B → A

T1: A락 획득                 T2: B락 획득
T1: B락 대기 ──────────────► T2가 보유
T2: A락 대기 ──────────────► T1이 보유
=> InnoDB 데드락 감지 → 한쪽 트랜잭션 강제 롤백
```

`distinctCourseIdsOf`의 순서는 `runningRepository.findByIds(runningIds)`의 반환 순서, 즉 **클라이언트가 보낸 러닝 ID 배열의 순서**에 사실상 종속됩니다. 즉 순서를 클라이언트가 결정합니다. 이건 우연이 아니라 **재현 가능한 조건**입니다.

데드락이 나면 `deleteRunnings`가 `CannotAcquireLockException`으로 실패하고, 사용자는 "기록 삭제 실패"를 봅니다. 재시도하면 대개 성공하므로 **재현이 어렵고 로그에만 남는 유형의 장애**입니다. 제가 예전에 정산 배치에서 똑같은 걸 겪었는데, 원인을 찾는 데 2주 걸렸습니다. 범인은 정확히 이거였습니다 — 여러 행에 락을 거는 코드가 정렬을 안 했던 것.

`updateRunningPublicStatus`는 `List.of(course.getId())` 단건이라 무해하지만, `deleteRunnings`는 다건입니다.

**개선 코드** (`CourseReadModelWriter.java`):

```java
/**
 * 러닝 기록을 진실의 원천으로 TOP4·러너 수를 다시 계산해 덮어쓴다. (증분 드리프트 보정)
 * 리드모델이 없는 코스는 건너뛴다.
 *
 * <p><b>락 순서</b> — 여러 코스를 재계산할 때는 반드시 courseId 오름차순으로 X락을 잡는다.
 * 서로 다른 트랜잭션이 같은 코스 집합을 다른 순서로 잠그면 InnoDB 데드락이 발생하기 때문이다.
 * (예: 사용자A가 [1,2]를, 사용자B가 [2,1]을 동시에 삭제하는 경우)</p>
 */
public void recalculate(Collection<Long> courseIds) {
    if (courseIds == null || courseIds.isEmpty()) {
        return;
    }
    List<Long> lockOrderedCourseIds = courseIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()                       // 전역 락 순서 고정 — 데드락 방지
            .toList();

    for (Long courseId : lockOrderedCourseIds) {
        readModelRepository.findByCourseIdForUpdate(courseId)
                .ifPresent(this::recalculateReadModel);
    }
}
```

**개선 이유**:

데드락 회피의 정석은 **모든 트랜잭션이 동일한 전역 순서로 락을 획득하는 것**입니다. `courseId` 오름차순이라는 전순서(total order)를 강제하면, 두 트랜잭션이 겹치는 코스 집합을 다뤄도 대기 그래프에 사이클이 생기지 않습니다. 한쪽이 기다릴 뿐 데드락은 성립하지 않습니다.

이 로직을 **Writer 안에 두는 것**이 핵심입니다. 호출자(`RunningCommandService`)에게 "정렬해서 넘겨주세요"라고 요구하면 새 호출자가 생길 때마다 규약이 깨집니다. 락 규율은 락을 잡는 곳에 있어야 하고, 이 PR이 이미 "X락은 Writer 안에서만"이라는 원칙을 세웠으니 **락 순서도 같은 원칙의 일부**입니다.

부수적으로 `distinct()`/null 필터를 Writer에 넣으면 `RunningCommandService.distinctCourseIdsOf`의 방어 로직과 이중이 되지만, Writer는 자기 계약을 스스로 보장하는 편이 낫습니다. 호출자 쪽 헬퍼는 그대로 두어도 무방합니다(의도가 드러나는 이름이라 가독성에 기여).

**테스트 제안**:

```java
@DisplayName("여러 코스를 동시에 재계산해도 락 순서가 고정되어 데드락이 발생하지 않는다")
@Test
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void recalculate_concurrentOppositeOrder_doesNotDeadlock() {
    // given : 코스 A, B 각각 리드모델 + 공개 러닝
    // when  : 스레드1은 recalculate([A,B]), 스레드2는 recalculate([B,A])를 동시에
    // then  : failures 비어 있음 (CannotAcquireLockException 없음)
}
```

---

### [MAJOR-2] 스킵 경로가 전부 무로그 — 기존 리스너에 있던 관측성이 사라졌다

**현재 코드** (`CourseReadModelWriter.java:62-73`):

```java
public void applyRun(Long courseId, Long memberId, int durationSeconds, Long runningId) {
    CourseReadModel readModel = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);
    if (readModel == null) {
        return;                                    // 무로그
    }

    readModel.applyRun(memberId, durationSeconds); // 반환값 무시 — 반영 여부 기록 없음

    if (isMembersFirstPublicRun(courseId, memberId, runningId)) {
        readModel.updateRunnersCount(readModel.getRunnersCount() + 1);
    }
}
```

`rename`(`:92-95`), `delete`(`:123-126`), `recalculate`(`:83-86`)도 모두 `ifPresent`로 조용히 지나갑니다. 클래스 전체에서 로그는 `createPublicReadModel`의 `log.info` 한 줄뿐입니다.

**삭제된 코드** (`ReadModelSyncListener.java`, 구버전):

```java
if (readModel == null) {
    log.warn("ReadModel not found for course={}. Skipping sync.", courseId);
    return;
}

boolean inserted = readModel.insertIfBetter(memberId, durationSeconds);
if (inserted) {
    log.info("Updated TOP4 for course={}: member={}, time={}s", courseId, memberId, durationSeconds);
}
```

**문제점**:

"리드모델이 없으면 스킵"은 **정상 케이스(비공개 코스)와 이상 케이스(공개 코스인데 리드모델이 유실됨)를 구분하지 않습니다.** 지금은 둘 다 아무 흔적을 남기지 않습니다.

실제로 벌어질 시나리오를 하나 그려보겠습니다. PR-2 백필이 일부 코스를 놓쳤거나, 코스 공개 전환 트랜잭션이 특정 조건에서 롤백돼 리드모델만 안 생긴 코스가 몇 개 생겼다고 합시다. 그 코스에서 사람들이 계속 뜁니다. 리드모델은 영원히 안 채워집니다. **에러도 없고, 로그도 없고, 알림도 없습니다.** 발견 경로는 오직 사용자 제보뿐입니다 — "이 코스만 랭킹이 안 보여요."

제보를 받고 나서도 원인 추적이 어렵습니다. 로그를 뒤져도 "그 코스에서 러닝이 끝났을 때 Writer가 뭘 했는지"에 대한 기록이 하나도 없기 때문입니다. 코드를 정독하고 DB를 직접 조회해서야 "아, 리드모델 행이 없네"를 알게 됩니다. 새벽 3시에 이 과정을 하고 싶은 사람은 없습니다.

또 하나: `readModel.applyRun(...)`이 **`boolean`을 반환하도록 설계된 이유**는 "TOP4가 실제로 바뀌었는지"를 호출자가 알 수 있게 하기 위함입니다(설계 §1-5). 지금은 그 정보를 버리고 있어서, 엔티티의 반환값이 사실상 죽은 API가 됐습니다.

**개선 코드**:

```java
public void applyRun(Long courseId, Long memberId, int durationSeconds, Long runningId) {
    CourseReadModel readModel = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);
    if (readModel == null) {
        // 비공개 코스의 정상 스킵과 구분되지 않으므로 debug — 특정 코스만 반영이 안 될 때 켜서 확인한다
        log.debug("Skip applyRun: read model absent. course={}, member={}", courseId, memberId);
        return;
    }

    boolean topRunnersChanged = readModel.applyRun(memberId, durationSeconds);

    boolean firstRun = isMembersFirstPublicRun(courseId, memberId, runningId);
    if (firstRun) {
        readModel.updateRunnersCount(readModel.getRunnersCount() + 1);
    }

    if (topRunnersChanged || firstRun) {
        log.info("Applied run to read model. course={}, member={}, duration={}s, top4Changed={}, firstRun={}",
                courseId, memberId, durationSeconds, topRunnersChanged, firstRun);
    }
}
```

**개선 이유**:

1. **"바뀐 것만 로그"** — 러닝 종료 대부분은 TOP4에 못 듭니다(설계 문서의 "대부분의 러닝은 여기서 끝난다"). 매번 info를 찍으면 로그가 러닝 수만큼 늘어 비용이 됩니다. `topRunnersChanged || firstRun`으로 게이팅하면 **의미 있는 상태 변화만** 남습니다. 이건 기존 리스너가 `if (inserted)`로 했던 것과 같은 판단이고, 지금 코드가 잃어버린 부분입니다.

2. **스킵은 debug** — 비공개 코스 스킵은 정상 동작이라 warn을 찍으면 늑대소년이 됩니다. 하지만 아예 없으면 조사할 수단이 없습니다. debug로 두면 평소엔 조용하고, 문제 발생 시 해당 패키지 로그 레벨만 올려서 즉시 확인할 수 있습니다.

3. **개인정보 없음** — `courseId`, `memberId`(내부 PK), 러닝 시간만 남깁니다. `memberUuid`나 좌표는 찍지 않습니다. CLAUDE.md의 "prod 러닝 데이터 마스킹 정책"과 충돌하지 않는 범위입니다.

4. 여유가 되면 `recalculate`에도 `log.info("Recalculated read model. course={}, topCount={}, runnersCount={}", ...)`를 넣으십시오. 재계산은 빈도가 낮고(삭제·공개전환) **값을 통째로 덮어쓰는 위험한 연산**이므로, 사후에 "언제 무엇으로 덮였는지" 추적할 수 있어야 합니다.

---

### [MAJOR-3] 모든 공개 러닝이 코스 리드모델 행에 X락을 건다 — 인기 코스의 직렬화 지점

**현재 코드** (`CourseReadModelWriter.java:63`):

```java
CourseReadModel readModel = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);
```

**문제점**:

`applyRun`은 러닝 종료 트랜잭션 안에서 호출되고, 첫 줄부터 `SELECT ... FOR UPDATE`로 해당 코스의 리드모델 행에 배타 락을 겁니다. 이 락은 **러닝 저장 트랜잭션이 커밋될 때까지** 유지됩니다.

러닝 종료 트랜잭션은 가볍지 않습니다. `RunningCommandService.createRun`을 보면 트랜잭션 안에서 S3 업로드(`upload(...)` — 원시/보간 텔레메트리 + 스크린샷 3회)를 수행합니다. `createRunAndCourse`는 5회입니다. 즉 **네트워크 I/O가 트랜잭션 안에 있습니다.**

지금 코드의 순서는 다행히 `upload()` → `createAndSaveRunning()` → `applyRunToReadModel()`이라 **락 획득이 S3 업로드 뒤**입니다. 이건 잘 배치된 겁니다. 하지만 트랜잭션 자체가 길기 때문에, 락 획득 이후 커밋까지의 구간(러닝 INSERT 플러시 + EXISTS 쿼리 + 이벤트 리스너 `BEFORE_COMMIT` 처리 — VDOT 계산, 구독 생성)이 여전히 락 보유 구간입니다.

결과적으로 **같은 코스를 동시에 완주한 사용자들의 러닝 저장이 직렬화**됩니다. 평상시엔 문제없지만, 마라톤 이벤트나 인기 코스 챌린지처럼 "같은 코스에서 수백 명이 비슷한 시각에 완주"하는 상황에서는 이 행이 핫스팟이 됩니다. 대기가 길어지면 `innodb_lock_wait_timeout`(기본 50초)에 걸려 러닝 저장이 실패하고, **사용자는 방금 뛴 기록을 잃습니다.**

여기서 짚고 싶은 건, 이게 "잘못된 코드"가 아니라 **강한 일관성을 선택한 대가**라는 점입니다. 설계 §0-1이 "X락은 Writer 안에서만"을 핵심 성질로 세웠고 그 판단은 옳습니다. 다만 그 대가가 어디에 청구되는지는 알고 있어야 합니다.

**개선 코드** (선택 1 — 락 이전 빠른 탈락):

```java
public void applyRun(Long courseId, Long memberId, int durationSeconds, Long runningId) {
    // 1) 락 없이 현재 상태를 먼저 읽어 "확실히 아무것도 안 바뀌는" 케이스를 걸러낸다
    CourseReadModel snapshot = readModelRepository.findByCourseId(courseId).orElse(null);
    if (snapshot == null) {
        log.debug("Skip applyRun: read model absent. course={}", courseId);
        return;
    }
    boolean mayChangeTopRunners = snapshot.wouldChangeTopRunners(memberId, durationSeconds);
    boolean firstRun = isMembersFirstPublicRun(courseId, memberId, runningId);
    if (!mayChangeTopRunners && !firstRun) {
        return;                                  // X락을 아예 잡지 않는다
    }

    // 2) 바뀔 가능성이 있을 때만 X락을 잡고, 락 안에서 다시 판정한다 (double-checked)
    CourseReadModel locked = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);
    if (locked == null) {
        return;
    }
    boolean changed = locked.applyRun(memberId, durationSeconds);
    if (firstRun) {
        locked.updateRunnersCount(locked.getRunnersCount() + 1);
    }
    ...
}
```

`CourseReadModel`에 추가:

```java
/** 락을 잡기 전 빠른 탈락용 판정. 최종 판정은 반드시 락 안의 applyRun 이 다시 수행한다. */
public boolean wouldChangeTopRunners(Long memberId, int timeSeconds) {
    TopRunners current = topRunners();
    return !current.with(memberId, timeSeconds).equals(current);
}
```

**개선 이유**:

`TopRunners.qualifies()`가 이미 "대부분의 러닝은 여기서 끝난다"고 말하고 있듯이, **실제로 TOP4를 바꾸는 러닝은 소수**입니다. 그런데 지금은 그 소수를 위해 전부가 락을 잡습니다. 락 이전에 비잠금 읽기로 걸러내면 핫 코스의 락 경합이 대폭 줄어듭니다.

정확성은 유지됩니다 — 비잠금 판정은 **오탐(불필요하게 락을 잡음)만 허용**하고 미탐(잡아야 하는데 안 잡음)은 다음 두 이유로 안전합니다:
- TOP4 진입 판정은 단조적입니다. 스냅샷 시점 이후 다른 트랜잭션이 커밋해 TOP4가 더 빨라지면 "원래 못 들던 기록"이 여전히 못 드는 것이므로 결과가 같습니다. 반대로 TOP4가 느려지는 유일한 경로는 재계산(삭제/비공개)인데, 그 경로는 스스로 전체를 덮어쓰므로 여기서 놓쳐도 정정됩니다.
- 최종 반영은 락 안에서 `applyRun`이 다시 판정하므로 Lost Update는 여전히 불가능합니다.

다만 이건 **최적화이지 버그 수정이 아닙니다.** 지금 당장 필요한 판단이 아니라면 다음 두 가지만이라도 해두십시오:

1. `applyRun` javadoc에 **"이 메서드는 러닝 종료 트랜잭션이 커밋될 때까지 코스 리드모델 행에 X락을 유지한다. 호출 이후에 무거운 작업(외부 I/O)을 추가하지 말 것"** 이라는 경고를 명시. 지금 순서가 맞게 돼 있어도, 다음 사람이 `applyRunToReadModel()` 위에 코드를 끼워 넣을 이유는 얼마든지 생깁니다.
2. 코스별 러닝 종료 동시성에 대한 부하 테스트를 PR-2 부하 테스트에 포함(같은 코스 1000VU 완주).

---

### [MINOR-1] `MAX_RANK`와 SQL의 `LIMIT 4`가 따로 논다

**현재 코드** (`TopRunners.java:15`):

```java
public static final int MAX_RANK = 4;
```

**현재 코드** (`CourseReadModelRepository.java:110`):

```java
        ORDER BY bestDurationSeconds ASC
        LIMIT 4
```

`MAX_RANK`를 5로 바꾸면 VO는 5개를 담으려 하지만 재계산 쿼리는 4행만 돌려주고, `CourseReadModel.applyTopRunners`는 `top1~top4` 필드에만 대입합니다. 즉 **세 곳이 각자 4를 알고 있습니다.**

TOP4는 "랭킹 도메인이 아닌 N=4 고정 뷰 데이터"라는 게 확정 전제이므로 실제로 바뀔 일은 거의 없습니다. 그래서 MINOR입니다. 다만 상수에 그 사실을 적어두는 게 좋겠습니다:

```java
/**
 * 슬롯 수는 4로 고정이다. 엔티티의 top1~top4 컬럼, 재계산 쿼리의 LIMIT 4와 함께 바뀌어야 하므로
 * 이 상수만 고쳐서는 안 된다. (TOP4 = 메인 화면 뷰 데이터, 랭킹 도메인 아님)
 */
public static final int MAX_RANK = 4;
```

JPQL이라면 `LIMIT :maxRank`로 파라미터화하는 것도 방법이지만, 네이티브 쿼리에서 `LIMIT`를 파라미터로 받으면 실행 계획 캐시 효율이 떨어질 수 있고 어차피 컬럼이 4개 고정이라 실익이 없습니다. **주석으로 결합을 드러내는 것**이 이 경우 가장 정직한 해법입니다.

---

### [MINOR-2] `CourseReadModelRepository`가 JPQL로 `Running` 엔티티를 참조해 도메인 경계를 넘는다

**현재 코드** (`CourseReadModelRepository.java:149-162`):

```java
@Query("""
    SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END
    FROM Running r
    WHERE r.isPublic = true
      AND r.hasPaused = false
      AND r.course.id = :courseId
      AND r.member.id = :memberId
      AND r.id <> :excludeRunningId
""")
boolean existsOtherPublicRunByCourseAndMember(...);
```

두 가지가 섞여 있습니다.

**(a) 도메인 경계** — 나머지 재계산 쿼리 2종은 네이티브라 `running_record` **테이블 이름**만 알면 됩니다. 그런데 이 쿼리만 JPQL이라 `running.domain.Running` **엔티티 매핑에 컴파일 타임으로 결합**됩니다. `docs/core/03-architecture.md`는 `running → course` 결합을 이미 "리팩토링 핵심 대상"으로 기록하고 있는데, 여기선 반대 방향(`course.dao → running.domain`)의 결합이 새로 생겼습니다. 누군가 `Running.hasPaused`를 리네임하면 코스 도메인의 리포지토리가 깨집니다.

javadoc이 "MySQL의 EXISTS는 0/1을 BIGINT로 반환해 boolean으로 변환되지 않는다"고 이유를 밝혀둔 건 좋습니다. 다만 그 문제는 네이티브에서도 해결 가능합니다:

```java
@Query(nativeQuery = true, value = """
    SELECT COUNT(*) > 0
    FROM running_record r
    WHERE r.is_public = TRUE
      AND r.deleted = FALSE
      AND r.has_paused = FALSE
      AND r.course_id = :courseId
      AND r.member_id = :memberId
      AND r.id <> :excludeRunningId
      LIMIT 1
""")
boolean existsOtherPublicRunByCourseAndMember(...);
```

이러면 나머지 두 쿼리와 스타일이 통일되고(`deleted = FALSE`가 명시적으로 보이는 것도 리뷰어 입장에선 오히려 낫습니다 — 지금은 `@SoftDelete`의 암묵 필터에 의존해서, 네이티브 2종과 JPQL 1종의 필터가 **같은지 눈으로 확인이 안 됩니다**), 도메인 경계도 테이블 참조 수준으로 낮아집니다.

**(b) `COUNT` vs `EXISTS`** — `COUNT(*)`는 조건에 맞는 **모든 행을 세고 나서** 판정합니다. `EXISTS`/`LIMIT 1`은 첫 행에서 멈춥니다. 한 사람이 한 코스에서 뛴 횟수는 대개 한 자릿수라 실측 차이는 미미하겠지만, 이건 **매 러닝 종료마다 실행되는 쿼리**입니다. 커버링 인덱스(`is_public, deleted, course_id, member_id, duration_sec`)를 타더라도 헤비 유저의 단골 코스에서는 수십~수백 행을 세게 됩니다. 공짜로 얻을 수 있는 개선이면 가져가는 게 맞습니다.

우선순위는 낮습니다. 다만 **(a)의 "필터가 같은지 눈으로 확인 안 되는" 부분**은 Q1~Q4 정합성의 핵심이라 한 번 정리해두면 다음 사람이 편합니다.

---

### [MINOR-3] 엔티티가 잘못된 입력을 조용히 무시한다

**현재 코드** (`CourseReadModel.java:218-228`):

```java
public void updateRunnersCount(long count) {
    if (count >= 0) {
        this.runnersCount = count;
    }
}

public void rename(String name) {
    if (name != null && !name.isBlank()) {
        this.name = name;
    }
}
```

음수 카운트나 공백 이름이 들어오면 **아무 일도 안 일어나고 아무도 모릅니다.**

호출부를 보면 실제로 잘못된 값이 올 경로는 없습니다 — `updateRunnersCount`는 `COUNT(...)`(항상 ≥0) 또는 `getRunnersCount() + 1`(항상 ≥1)만 받고, `rename`은 `CourseService.updateCourseName`이 `StringUtils.hasText`로 이미 걸러줍니다. 그래서 MINOR입니다.

문제는 **이 방어 코드가 버그를 숨기는 방향으로 작동한다**는 겁니다. 만약 미래에 어떤 계산 실수로 `updateRunnersCount(-1)`이 호출되면, 우리는 "카운트가 안 바뀌는 이상 현상"을 보게 되고 원인이 이 `if`문이라는 걸 알아내는 데 한참 걸립니다. 잘못된 입력은 **삼키는 게 아니라 터뜨리는 게** 디버깅에 유리합니다.

```java
public void updateRunnersCount(long count) {
    if (count < 0) {
        throw new IllegalArgumentException("러너 수는 음수일 수 없다. count=" + count);
    }
    this.runnersCount = count;
}

public void rename(String name) {
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("코스명은 비어 있을 수 없다.");
    }
    this.name = name;
}
```

기존 테스트(`CourseReadModelTest`의 "음수는 무시되고 기존 값이 유지된다", "null 이름은 무시되고 기존 코스명이 유지된다")가 현재 동작을 고정하고 있으므로, 정책을 바꾸려면 테스트도 같이 바꿔야 합니다. **어느 쪽을 택하든 "의도된 정책"이라는 게 드러나야** 합니다 — 지금은 왜 무시하는지가 어디에도 안 적혀 있습니다. 정책을 유지한다면 최소한 javadoc에 이유를 남기십시오.

---

### [MINOR-4] 리드모델이 이미 있는 코스를 재공개할 때는 재계산하지 않는다 — 생성 경로와 비대칭

**현재 코드** (`CourseReadModelWriter.java:103-118`):

```java
public void syncPublicity(Long courseId, boolean isPublic) {
    CourseReadModel readModel = readModelRepository.findByCourseIdForUpdate(courseId).orElse(null);

    if (!isPublic) {
        if (readModel != null) {
            readModel.makePrivate();
        }
        return;
    }

    if (readModel == null) {
        createPublicReadModel(courseId);    // create + 전체 재계산 (Q4)
        return;
    }
    readModel.makePublic();                 // 플래그만 — 재계산 없음
}
```

설계 Q4는 "**신규 생성**은 create + 전체 재계산으로 통일"이라고만 규정하므로, 재공개 경로에 재계산이 없는 것 자체는 **설계 위반이 아닙니다.**

논리적으로도 현재는 안전합니다. `applyRun`/`recalculate`는 리드모델의 `is_public` 값을 보지 않고 갱신하므로, 코스가 비공개인 동안에도 리드모델은 계속 최신 상태로 유지됩니다.

제가 걸리는 건 **그 안전성이 "Writer의 다른 메서드들이 is_public을 안 본다"는 사실에 암묵적으로 의존한다**는 점입니다. 누군가 나중에 성능을 이유로 `applyRun`에 `AND rm.is_public = true` 같은 조건을 넣거나, "비공개 코스는 스킵하자"는 최적화를 넣는 순간 — 비공개 기간 동안의 러닝이 통째로 누락되고, 재공개해도 정정되지 않습니다. 그 시점엔 아무도 이 인과관계를 기억하지 못합니다.

재공개는 빈도가 극히 낮은 연산이고, `recalculate`는 이미 X락을 잡은 상태에서 쿼리 2번입니다. 보험료가 거의 공짜입니다:

```java
if (readModel == null) {
    createPublicReadModel(courseId);
    return;
}
readModel.makePublic();
// 비공개 기간 중의 변화가 누락됐을 가능성을 배제하기 위해 재공개 시점에 항상 재집계한다.
// (빈도가 낮은 연산이고, 이미 X락을 보유 중이라 추가 비용이 사실상 없다)
recalculateReadModel(readModel);
```

이러면 "공개 상태가 되는 모든 경로는 진실의 원천으로부터 재구성된다"는 **단일 규칙**이 성립하고, 위 암묵 의존이 사라집니다. 설계 문서 §0-3의 T4(`코스 공개 전환 → recalculate`) 화살표와도 더 잘 맞습니다.

---

### [MINOR-5] 암묵적 flush 순서에 정확성이 의존하고 있다 (현재는 안전하나 주석이 없다)

`RunningCommandService.updateRunningPublicStatus`(`:187-195`)는 영속 상태의 `Running.isPublic`을 토글한 **직후** `recalculate`를 호출합니다. `deleteRunnings`(`:210-211`)도 벌크 소프트 삭제 직후 `recalculate`를 부릅니다.

재계산 쿼리는 DB에서 직접 집계하므로, **호출 시점에 변경분이 DB에 반영돼 있어야** 정확합니다. 현재는 안전합니다:

- `deleteInRunningIds`는 `@Modifying` 벌크 JPQL이라 즉시 DB에 실행됩니다(`clearAutomatically = true`로 영속성 컨텍스트도 비워지므로, 이어지는 `findByCourseIdForUpdate`가 DB에서 새로 읽습니다)
- `updateRunningPublicStatus`의 더티 체킹 변경분은, `findTop4RunnersByBestDuration`이 **네이티브 쿼리**이기 때문에 플러시됩니다. Hibernate 6는 동기화 쿼리 스페이스가 등록되지 않은 네이티브 쿼리 실행 전에 **세션 전체를 플러시**합니다(`NativeQueryImpl.prepareForExecution()`)

즉 정확성이 **"재계산 쿼리가 네이티브라서"** 성립합니다. 이건 취약합니다. 누군가 성능이나 타입 안전성을 이유로 `findTop4RunnersByBestDuration`을 JPQL로 바꾸면, JPQL의 자동 플러시는 **쿼리가 건드리는 테이블(query space)에 미반영 변경이 있을 때만** 발생하므로 — `CourseReadModel`만 조회하는 `findByCourseIdForUpdate`는 `running_record`의 변경을 플러시하지 않고 — 재계산이 **변경 전 데이터로 수행될 수 있습니다.** 그 결과 리드모델이 조용히 옛 값으로 덮어써집니다. 테스트는 통과할 수도 있습니다(테스트마다 `flushAndClear`를 명시적으로 부르고 있으므로).

두 가지 중 하나를 권합니다.

**(a) 의존성을 명시적으로 만든다** — 호출부에서 flush를 보장:

```java
running.updatePublicStatus();
eventPublisher.publishEvent(running.createUpdatedEvent());

// 재계산은 DB를 직접 집계하므로 공개 여부 변경이 먼저 DB에 반영돼야 한다
entityManager.flush();
courseReadModelWriter.recalculate(List.of(course.getId()));
```

**(b) 최소한 주석으로 계약을 남긴다** — `recalculate` javadoc에:

```java
/**
 * ...
 * <p><b>호출자 계약</b> — 이 메서드는 running_record 를 DB에서 직접 집계한다. 따라서 호출 전에
 * 러닝 변경분(공개 여부 토글·소프트 삭제)이 DB에 반영돼 있어야 한다. 현재는 재계산 쿼리가
 * 네이티브라 Hibernate 가 세션 전체를 자동 플러시하지만, 이 쿼리를 JPQL 로 바꾸면 그 보장이
 * 사라진다.</p>
 */
```

(a)가 더 견고하지만 `EntityManager`를 서비스에 주입해야 하는 부담이 있습니다. 최소 (b)는 해두십시오. **"왜 지금 동작하는지"를 아무도 모르는 코드**가 가장 위험합니다.

---

### [MINOR-6] `CourseReadModelWriter`만 `@Component` — 주변과 스테레오타입이 다르다

`CourseReadModelWriter.java:40`이 `@Component`인데, `CourseService`는 `@Service`입니다. 둘 다 `course.application` 패키지의 애플리케이션 계층 협력자이고 기능적으로는 동일하게 동작합니다.

"Writer는 유즈케이스가 아니라 협력 컴포넌트"라는 의도였다면 그것도 일리는 있습니다. 다만 코드베이스 전반의 관례(`application` = `@Service`)와 어긋나면 다음 사람이 "왜 이것만 다르지?"를 고민하게 됩니다. `@Service`로 통일하거나, 의도가 있다면 클래스 javadoc에 한 줄 남기는 편이 좋겠습니다.

---

## 설계 문서 대비 차이

| 항목 | 설계 | 구현 | 비고 |
|------|------|------|------|
| `RankSlot` @Embeddable + @AttributeOverride ×4 | §1-3 | 일치 (`CourseReadModel.java:76-102`) | 기존 8컬럼명 그대로 유지 |
| `TopRunners.with()` / `qualifies()` 순서 | §1-4 | 일치 (`TopRunners.java:24-51`) | 본인 슬롯 확인 → size 검사 순서 정확히 반영 |
| `ownerUuid` nullable 완화 | §1-2 | 일치 (`CourseReadModel.java:54-55`) | `nullable=false` 제거됨 |
| `@Embedded CourseProfile` + `thumbnailUrl` 역정규화 | §1-2 | 일치 (`CourseReadModel.java:60-64`) | **단, DDL/시드 미반영 → CRITICAL-1, 2** |
| `applyRun` 변경감지 → UPDATE 생략 | §1-5 | 일치 (`CourseReadModel.java:173-181`) | 반환값이 Writer에서 미사용 (MAJOR-2) |
| 시프트/스와프 200줄, 8인자 `replaceTop4`, `containsMember`, `updateRouteUrl` 삭제 | §1-5 | 일치 | 잔존 호출처 없음 확인 |
| Writer `@Transactional(MANDATORY)` | §3-1, §3-5 | 일치 (`CourseReadModelWriter.java:42`) | 계약 테스트까지 존재 |
| Writer 메서드 시그니처 5종 | §3-1 | 일치 | `applyRun/recalculate/rename/syncPublicity/delete` |
| Q1 일시정지 제외 (재계산·증분 동일) | 전제 | 일치 | 쿼리 3종 + `isReadModelAggregationTarget` 모두 적용 |
| Q2 공개 러닝만 `applyRun` | 전제 | 일치 (`RunningCommandService.java:136-140`) | 호출자 계약이 javadoc에 명시됨 |
| Q3 EXISTS 자기 제외 | §1-6 | 일치 (의미), 상이 (구현) | `EXISTS` 대신 `COUNT(r) > 0` JPQL — 사유 javadoc에 기록됨 (MINOR-2) |
| Q4 공개 전환 시 create + 전체 재계산 | 전제 | 일치 (신규 생성 경로) | 기존 리드모델 재공개는 재계산 없음 — 설계 범위 밖 (MINOR-4) |
| 재계산 쿼리 3종 (TOP4 / COUNT DISTINCT / EXISTS) | §3-3 | 일치 (`CourseReadModelRepository.java:99-162`) | 공통 필터 `is_public ∧ ¬deleted ∧ ¬has_paused` |
| `ReadModelSyncListener` 삭제 (이중 반영 방지) | §0-4 F | 일치 | 통합 테스트로 "정확히 한 번" 검증 |
| `CourseService` → Writer 이관, `readModelRepository` 직접 의존 제거 | §0-4 G | 일치 | `RunningRepository` 의존까지 제거됨 |
| **prod DDL 선적용** (`ADD COLUMN` ×5 + `MODIFY owner_uuid NULL`) | §4 운영 주의 | **미반영** | 저장소에 DDL/마이그레이션 아티팩트 없음 → **CRITICAL-2** |
| **`data.sql` 시드 정합** | 문서에 언급 없음 | **미반영** | `distance_km NOT NULL` 누락 → dev/local 부팅 실패 → **CRITICAL-1** |
| `CourseReadModelReader` + `@Cacheable` | §2-2, §3-1 | 미구현 | **PR-2 범위 — 정상** |
| `findCoursesForMap` 확장 + `deleted_at` 필터 제거 | §3-4 | 미구현 | **PR-2 범위 — 정상** (현 쿼리는 `deleted_at IS NULL` 유지, 테스트도 그 동작을 고정) |
| 백필 러너 | §4 PR-2 | 미구현 | **PR-2 범위 — 정상** |

---

## 선배 개발자의 한마디

**하나. "테스트 685개 그린"은 안전의 증거가 아니라, 테스트가 보는 범위의 증거입니다.**

이번에 발견한 CRITICAL-1이 딱 그 이야기입니다. 엔티티에 `@Embedded CourseProfile` 한 줄을 추가했는데, 그 한 줄이 `course_read_model` 테이블에 `NOT NULL` 컬럼을 만들고, 그게 `data.sql`을 깨고, 그게 dev 서버 부팅을 막습니다. 그런데 테스트 프로파일은 `data.sql`을 타지 않으므로 전부 초록불입니다.

리팩토링에서 제가 항상 던지는 질문이 있습니다. **"이 변경이 코드 밖에서 요구하는 것은 무엇인가?"** — DDL, 시드 데이터, 설정, 배포 순서, 운영 문서. 이번 PR은 코드 안쪽은 흠잡을 데가 거의 없는데, 코드 밖에서 요구하는 것 두 가지(dev 시드, prod DDL)가 비어 있습니다. 그리고 그 둘이 이 리뷰의 CRITICAL 전부입니다.

특히 MANDATORY를 선택한 이상 이 문제는 더 무겁습니다. 리드모델 갱신이 러닝 저장과 한 트랜잭션에 묶여 있다는 건, **리드모델 경로의 스키마 오류가 곧 "사용자가 뛴 기록의 유실"** 이라는 뜻이니까요. 원자성을 얻은 대신 장애 전파 범위를 넓힌 선택이고, 저는 그 선택에 동의합니다 — 다만 그렇다면 스키마 정합성은 사람의 기억이 아니라 도구가 보장해야 합니다. Flyway 도입을 진지하게 검토해 주십시오.

**둘. 조용한 코드는 6개월 뒤의 나에게 보내는 저주입니다.**

`ReadModelSyncListener`를 지우면서 `log.warn("ReadModel not found for course={}")` 한 줄도 같이 사라졌습니다. 지금 Writer는 리드모델이 없으면 `return;`으로 끝납니다. 코드는 더 깔끔해졌습니다. 그런데 운영에서는 **"특정 코스만 랭킹이 안 채워지는데 로그에 아무 흔적이 없는"** 상태가 됐습니다.

제가 겪은 장애 중 가장 오래 걸린 것들의 공통점이 이겁니다. 예외가 터지는 버그는 스택트레이스가 범인을 알려줍니다. 진짜 무서운 건 **아무 일도 안 일어나는 버그**입니다. 조용히 스킵되고, 조용히 어긋나고, 몇 달 뒤 사용자 제보로 발견되고, 그때는 이미 데이터가 상당량 틀어져 있습니다.

리팩토링할 때 로그를 지우는 건 흔한 실수입니다. "정리"처럼 느껴지기 때문입니다. 하지만 로그는 코드가 아니라 **운영 인터페이스**입니다. 지울 때는 "이걸 지우면 장애 시 무엇으로 조사하지?"를 먼저 물어보십시오. MAJOR-2의 제안대로 `topRunnersChanged || firstRun`으로 게이팅하면, 로그 볼륨은 거의 늘지 않으면서 **상태가 바뀐 순간만 기록**됩니다. 비용 대비 효과가 가장 좋은 방어입니다.

---

*마지막으로: 구조·VO 분리·테스트 설계는 정말 잘 하셨습니다. 위 CRITICAL 2건은 코드의 문제가 아니라 "코드 바깥"의 문제이고, 둘 다 반나절이면 정리됩니다. 그 뒤에는 자신 있게 내보내셔도 됩니다.*

---

## 실용주의 판정 (Pragmatic Action Decision)

> 판정자: Pragmatic Action Decider
> 판정 기준: The Pragmatic Programmer (Andrew Hunt & David Thomas) 원칙 기반
> 작업 범위: **PR-1 (리드모델 쓰기 측 리팩토링)** — `CourseReadModelWriter` 신규 도입, `TopRunners`/`RankSlot` VO 분리, `ReadModelSyncListener` 제거, `CourseReadModel` 역정규화 컬럼 확장. 실서비스 코드이며 dev 브랜치 머지 대상. 조회 전환·캐시·백필은 PR-2(설계 §4)로 분리됨. 배포 일정 여유 있음(PR-2 완료 후 동시 배포 가능성 높음). 전체 테스트 685개 그린.

### 판정 요약

| 이슈 | 심각도 | 판정 | 근거 원칙 |
|------|--------|------|-----------|
| [CRITICAL-1] `data.sql`이 신규 NOT NULL 컬럼 미충족 → dev/local 부팅 실패 | CRITICAL | 🔴 FIX | 원칙 4 Crash Early + 원칙 3 Good Enough(이번 PR이 만든 문제) |
| [CRITICAL-2] prod DDL 선적용이 저장소에 미강제 | CRITICAL | 🔴 FIX *(최소안만)* | 원칙 2 되돌림 가능성(DB 스키마) + 원칙 7 우연에 의한 프로그래밍 |
| [MAJOR-1] `recalculate` 락 순서 미정렬 → 다중 삭제 데드락 | MAJOR | 🔴 FIX | 원칙 4 Crash Early(동시성) + 원칙 7(순서가 클라이언트 입력에 종속) |
| [MAJOR-2] 스킵 경로 무로그 → 관측성 후퇴 | MAJOR | 🔴 FIX | 원칙 4 새벽 3시 장애 콜 + 원칙 3(이번 PR이 삭제한 운영 인터페이스) |
| [MAJOR-3] 모든 공개 러닝이 X락 → 인기 코스 직렬화 | MAJOR | 🟡 DEFER | 원칙 36 완벽한 SW는 없다 + 성능 최적화(임계치 미도달) |
| [MINOR-1] `MAX_RANK` ↔ `LIMIT 4` 암묵 결합 | MINOR | 🟢 PASS | 원칙 5 DRY 미해당(N=4는 확정 전제) |
| [MINOR-2] 리포지토리 JPQL이 `Running` 엔티티 참조 | MINOR | 🟡 DEFER | 원칙 2 되돌림 가능(내부 구현) + PR-2가 같은 파일을 만짐 |
| [MINOR-3] 엔티티가 잘못된 입력을 조용히 무시 | MINOR | 🟢 PASS | 원칙 36(호출 경로 없음) + MANDATORY 결합상 예외 전환이 새 리스크 |
| [MINOR-4] 재공개 시 재계산 없음 — 생성 경로와 비대칭 | MINOR | 🟡 DEFER | 원칙 2 되돌림 가능 + 현재 동작 정상(설계 위반 아님) |
| [MINOR-5] 암묵적 flush 순서 의존 | MINOR | 🔴 FIX *(주석 계약만)* | 원칙 7 우연에 의한 프로그래밍(문서화되지 않은 가정) |
| [MINOR-6] `@Component` vs `@Service` 스테레오타입 불일치 | MINOR | 🟢 PASS | 주관적 관례 + 전파 위험 없음 |

**집계: FIX 5 / DEFER 3 / PASS 3**

> **적용 현황 (2026-08-04)**: FIX 5건 전부 적용 완료 (아래 각 Task 의 체크 항목 참조). DEFER 3건·PASS 3건은 손대지 않았다.
> 회귀 검증: `./gradlew test --tests "soma.ghostrunner.domain.course.*" --tests "soma.ghostrunner.domain.running.application.*"` → 37 클래스 / 189 테스트 / 실패 0.

FIX 5건 중 3건(CRITICAL-2, MAJOR-2 일부, MINOR-5)은 S 사이즈이며, 코드 구조 변경은 MAJOR-1의 `sorted()` 한 줄이 전부입니다. **구조는 손대지 않습니다.** 등급 A에 걸맞게, 이 PR의 설계 판단(MANDATORY, Writer 단일 진입점, VO 분리)은 어느 것도 수정 대상이 아닙니다.

---

### 수정 필수 항목 (FIX Tasks)

#### Task: [CRITICAL-1] `data.sql` 리드모델 시드를 `course` 원본과 정합시키고 신규 컬럼을 채운다

- [x] **적용 완료** (2026-08-04) — `src/main/resources/data.sql` 리드모델 INSERT 블록 재작성. 신규 컬럼 5개 추가 + 코스 1/3/4 기준 정합화(비공개 코스 2는 리드모델 행 제거), TOP4·runners_count 를 `running_record` 시드 집계와 일치시킴. TestContainers MySQL + `sql.init.mode=always` 로 실적재 검증.
- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 4 — *"Crash Early. A dead program normally does a lot less damage than a crippled one." (Tip #38)* / 원칙 3 — *"Make Quality a Requirements Issue" (Tip #8)*. 이건 기존 코드의 문제가 아니라 **이번 PR이 추가한 `@Embedded CourseProfile` 한 줄이 만든 문제**다. 내가 만든 코드는 내가 책임진다.
- **수정 대상**: `src/main/resources/data.sql:152-176` (`course_read_model` INSERT 블록 전체)
- **수정 내용**:
  1. 컬럼 목록에 `distance_km`(NOT NULL, 필수), `elevation_average_m`, `elevation_gain_m`, `elevation_loss_m`, `thumbnail_url` 5개를 추가하고 모든 VALUES 행에 값을 채운다.
  2. **동시에 원본 정합을 맞춘다.** 현재 시드는 `course` 테이블과 어긋나 있다 — 확인된 드리프트:
     - `course_read_model.course_id=2` → 이름 `'올림픽공원 달리기'`, `is_public=true`. 그러나 `course.id=2`는 `'남산 등산 코스'`, `is_public=false` (`data.sql:41-43`)
     - `course_read_model.course_id=3` → `'강남 야간 러닝'`. 그러나 `course.id=3`은 `'올림픽공원 코스'` (`data.sql:44-46`)
     - `route_url`/`start_lat`/`start_lng`도 `course` 값(`https://example.com/routes/N.json`, `37.5283/126.9340`)과 불일치
     - → PR-2에서 조회를 리드모델로 전환하는 순간 dev에서 **코스 목록과 상세/지도가 다른 코스를 보여준다.** 지금 컬럼을 채우는 김에 `course` 행(1, 3, 4 — `is_public=true`인 코스)을 기준으로 리드모델 시드를 재작성한다. 비공개인 `course.id=2`는 리드모델 행을 만들지 않는 것이 `syncPublicity` 규칙과 일치한다.
  3. 값 출처: `course` 테이블 시드 `data.sql:38-49`의 `distance_km / elevation_average_m / elevation_gain_m / elevation_loss_m / thumbnail_url / route_url / start_latitude / start_longtitude`를 그대로 복사한다.
- **예상 작업량**: **M** (컬럼 추가만이면 S, 원본 정합 재작성 포함해 M)
- **검증 방법**:
  1. 로컬 MySQL을 띄우고 `./gradlew bootRun --args='--spring.profiles.active=local'` — 컨텍스트 로딩 성공 확인 (`application-local.yml:16,24-28`이 `ddl-auto: create` + `sql.init.mode: always`이므로 dev와 동일 경로를 탄다)
  2. `SELECT course_id, name, distance_km, is_public FROM course_read_model;`이 `course` 테이블과 1:1로 맞는지 확인
  3. 실패 시 나타날 증상: `Field 'distance_km' doesn't have a default value` → `ScriptStatementFailedException`

> **범위 밖(참고)**: 리뷰의 "시딩 컴포넌트로 전환"(개선 이유 2)과 "CI 스모크 테스트 추가"(개선 이유 3)는 이 Task에 포함하지 않는다. 전자는 PR-2 백필 러너와 코드를 공유해야 하므로 PR-2에서, 후자는 별도 인프라 티켓으로 분리한다(아래 DEFER 참조).

---

#### Task: [CRITICAL-2] PR-1 DDL 스크립트를 저장소 아티팩트로 남긴다

- [x] **적용 완료** (2026-08-04) — `docs/refactoring/course-read-model/ddl/pr1.sql` 신규 생성(적용 순서·`DEFAULT 0` 사유·롤백 DDL·`owner_uuid` 미복원 사유 주석 포함). 설계 문서 §4 운영 주의에 스크립트 링크 추가. prod `ddl-auto` 실제 값은 저장소에 `application-prod.yml`이 없어 확인 불가 → **배포 담당자 확인 필요**로 문서에 명시.
- **판정**: 🔴 FIX (단, **리뷰의 "최소안" 1번만**. 권장안 2번 Flyway 도입은 DEFER)
- **근거 원칙**: 원칙 2 — *"There Are No Final Decisions" (Tip #18)*. FIX 판정 기준 4번(되돌리기 어려운 변경 — DB 스키마)에 정면으로 해당한다. 배포 일정에 여유가 있고 PR-2와 함께 나갈 가능성이 높다는 점은 **DDL 필요성을 없애주지 않는다.** 오히려 "나중에 배포하니 지금은 됐다"가 정확히 잊혀지는 경로다. 원칙 7 — 스키마 적용 여부가 사람의 기억에 의존하는 상태는 "우연히 동작하는" 시스템이다.
- **수정 대상**: 신규 파일 `docs/refactoring/course-read-model/ddl/pr1.sql` + `docs/refactoring/course-read-model/04-detailed-design.md:428-431` (운영 주의 섹션)
- **수정 내용**:
  1. DDL 스크립트 파일 생성 (리뷰 최소안 그대로, `DEFAULT 0` 포함):
     ```sql
     ALTER TABLE course_read_model
         ADD COLUMN distance_km          DOUBLE       NOT NULL DEFAULT 0,
         ADD COLUMN elevation_average_m  DOUBLE       NULL,
         ADD COLUMN elevation_gain_m     DOUBLE       NULL,
         ADD COLUMN elevation_loss_m     DOUBLE       NULL,
         ADD COLUMN thumbnail_url        TEXT         NULL,
         MODIFY COLUMN owner_uuid        VARCHAR(36)  NULL;
     ```
  2. 파일 상단에 **적용 순서**를 주석으로 명시: "이 DDL은 PR-1 코드 배포 **이전**에 적용한다. 미적용 시 리드모델 읽기/쓰기 전 경로가 `Unknown column` 예외로 실패하며, MANDATORY 전파로 인해 러닝 종료(`createRun`)까지 롤백된다."
  3. 롤백 DDL도 같은 파일 하단에 주석으로 남긴다 (`DROP COLUMN` 5개 — `owner_uuid`는 되돌리지 않는다. 이미 null 행이 생겼을 수 있으므로 `NOT NULL` 복원은 실패한다. 이 사실을 명시할 것).
  4. 설계 문서 §4 "운영 주의"의 해당 줄에 스크립트 경로 링크를 건다.
  5. **prod의 `ddl-auto` 실제 값을 확인해 문서에 기록한다.** 저장소에 `application-prod.yml`이 없어 외부 주입 값을 코드로는 알 수 없다. `update`여도 `MODIFY owner_uuid NULL`은 Hibernate가 수행하지 않으므로 수동 DDL은 어느 경우든 필요하다.
- **예상 작업량**: **S**
- **검증 방법**: dev DB(또는 스테이징)에 스크립트를 그대로 실행 → `SHOW CREATE TABLE course_read_model;`로 6개 변경 반영 확인 → 애플리케이션 기동 후 러닝 종료/삭제/코스 공개전환 3개 경로 스모크

---

#### Task: [MAJOR-1] `recalculate`의 X락 획득 순서를 `courseId` 오름차순으로 고정한다

- [x] **적용 완료** (2026-08-04) — `CourseReadModelWriter.recalculate`가 `filter(nonNull).distinct().sorted()` 로 전순서를 고정. javadoc 에 락 순서 규약 + `applyRun` 에 락 보유 구간 경고 추가. `CourseReadModelWriterUnitTest` 3건으로 호출 순서를 고정(실 데드락 테스트는 비결정적이라 순서 자체를 검증).
- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 4 — *"Crash Early" (Tip #38)*, FIX 판정 기준 6번(동시성/데이터 정합성). 원칙 7 — *"Don't Program by Coincidence" (Tip #62)*: 락 순서가 **클라이언트가 보낸 러닝 ID 배열 순서**에 종속되어 있다. 즉 데드락 발생 조건을 외부에서 결정할 수 있고, 이건 "드물게 운 나쁘면"이 아니라 **재현 가능한 조건**이다. 게다가 `recalculate`와 그 호출부(`deleteRunnings`)는 모두 이번 PR에서 새로 작성된 코드다(FIX 기준 5번).
- **수정 대상**: `src/main/java/soma/ghostrunner/domain/course/application/CourseReadModelWriter.java:79-87` (`recalculate`)
- **수정 내용**:
  1. 루프 진입 전 `courseIds.stream().filter(Objects::nonNull).distinct().sorted().toList()`로 전순서를 고정한다 (리뷰 개선 코드 그대로).
  2. javadoc에 **락 순서 규약**을 명시한다: "여러 코스를 재계산할 때는 반드시 `courseId` 오름차순으로 X락을 잡는다. 서로 다른 트랜잭션이 같은 코스 집합을 다른 순서로 잠그면 InnoDB 데드락이 발생한다."
  3. **정렬은 반드시 Writer 안에서 한다.** 호출자에게 "정렬해서 넘겨주세요"를 요구하면 새 호출자가 생길 때마다 규약이 깨진다. 이 PR이 이미 세운 "X락은 Writer 안에서만" 원칙의 연장이다. `RunningCommandService.distinctCourseIdsOf`(`:217-223`)는 **그대로 둔다** — 의도가 드러나는 이름이라 가독성에 기여하고, 이중 방어는 무해하다.
  4. 함께 반영: `applyRun` javadoc에 락 보유 구간 경고를 추가한다 — "이 메서드는 러닝 종료 트랜잭션이 커밋될 때까지 코스 리드모델 행에 X락을 유지한다. **호출 이후에 무거운 작업(외부 I/O)을 추가하지 말 것.**" (MAJOR-3의 저비용 방어책 1번을 여기에 흡수. 현재 `createRun`의 S3 업로드가 락 획득보다 앞에 있는 배치가 우연히 유지되는 것을 막는다.)
- **예상 작업량**: **S**
- **검증 방법**:
  1. 리뷰가 제안한 테스트 추가 — `recalculate_concurrentOppositeOrder_doesNotDeadlock()`: 스레드1 `recalculate([A,B])`, 스레드2 `recalculate([B,A])` 동시 실행 후 `CannotAcquireLockException` 미발생 확인. `CourseReadModelWriterTest`의 기존 실스레드 X락 동시성 테스트와 같은 패턴을 재사용한다.
  2. 기존 `CourseReadModelWriterTest` / `RunningCommandServiceTest` 전량 그린

---

#### Task: [MAJOR-2] Writer의 상태 변화·스킵 경로에 로그를 복원한다

- [x] **적용 완료** (2026-08-04) — `applyRun` 스킵 경로 `log.debug`, `applyRun` 반환값을 `topRunnersChanged` 로 수용해 `topRunnersChanged || firstPublicRun` 게이팅 `log.info`, `recalculateReadModel` 에 재계산 `log.info` 추가. 로그에는 `courseId`/`memberId`/`durationSeconds`만 남김(PII 없음).
- **판정**: 🔴 FIX
- **근거 원칙**: 원칙 4 — *"새벽 3시 장애 콜"*. 원칙 3 — *"Make Quality a Requirements Issue" (Tip #8)*: 이건 기존 코드의 결함이 아니라 **이번 PR이 `ReadModelSyncListener`를 지우면서 함께 삭제한 운영 인터페이스**다. 삭제한 사람이 되살릴 책임이 있다. 로그가 없는 조용한 스킵은 "에러도 없고 알림도 없고 발견 경로가 사용자 제보뿐"인 상태를 만들며, 이는 원칙 4가 막으려는 바로 그 상황이다.
- **수정 대상**: `src/main/java/soma/ghostrunner/domain/course/application/CourseReadModelWriter.java:62-73` (`applyRun`), `:79-87` (`recalculate`), `:145-154` (`recalculateReadModel`)
- **수정 내용**:
  1. `applyRun`: 리드모델 부재 시 `log.debug("Skip applyRun: read model absent. course={}, member={}", courseId, memberId)`. **warn이 아니라 debug** — 비공개 코스 스킵은 정상 동작이라 warn은 늑대소년이 된다.
  2. `applyRun`: `readModel.applyRun(...)`의 **반환값을 받는다**(`boolean topRunnersChanged`). 현재는 버려져서 엔티티의 설계 §1-5 API가 죽은 코드가 되어 있다. `isMembersFirstPublicRun` 결과도 지역변수(`firstRun`)로 뽑는다.
  3. `applyRun`: `if (topRunnersChanged || firstRun)`으로 **게이팅한 info 로그** 1건. 대부분의 러닝은 TOP4에 못 들므로 무조건 info는 러닝 수만큼 로그가 늘어난다. 게이팅하면 볼륨은 거의 안 늘고 상태 변화만 남는다(구 리스너의 `if (inserted)`와 동일한 판단).
  4. `recalculateReadModel`: `log.info("Recalculated read model. course={}, topCount={}, runnersCount={}", ...)`. 재계산은 빈도가 낮고 **값을 통째로 덮어쓰는 위험한 연산**이므로 "언제 무엇으로 덮였는지"가 반드시 추적 가능해야 한다.
  5. **PII 금지 준수** — `courseId`, `memberId`(내부 PK), `durationSeconds`만 남긴다. `memberUuid`·좌표·경로 URL은 찍지 않는다.
- **예상 작업량**: **S**
- **검증 방법**:
  1. `RunningReadModelSyncIntegrationTest`를 `logging.level.soma.ghostrunner.domain.course=DEBUG`로 실행해 "TOP4 진입 러닝"에서만 info가 찍히고 일반 러닝에서는 안 찍히는지 눈으로 확인
  2. `readModel.applyRun` 반환값을 쓰기 시작했으므로 기존 `CourseReadModelTest`의 반환값 계약 테스트가 여전히 그린인지 확인
  3. 로그 추가로 인한 동작 변화가 없어야 하므로 전체 685개 그린 유지

---

#### Task: [MINOR-5] `recalculate`의 flush 선행 계약을 javadoc으로 명시한다

- [x] **적용 완료** (2026-08-04) — `recalculate` javadoc 에 `호출자 계약` 블록 추가(두 호출 경로가 성립하는 이유 + JPQL 전환 시 보장이 사라진다는 경고). MAJOR-1 과 동일 javadoc 블록이라 함께 처리.
- **판정**: 🔴 FIX (단, **리뷰의 (b)안 주석 계약만**. (a)안 `EntityManager` 주입은 DEFER)
- **근거 원칙**: 원칙 7 — *"Don't Program by Coincidence. Rely only on reliable things." (Tip #62)*. 심각도는 MINOR지만 성격이 정확히 원칙 7이 지목하는 유형이다: **현재 정확성이 "재계산 쿼리가 네이티브라서 Hibernate가 세션 전체를 자동 플러시한다"는, 코드 어디에도 적히지 않은 사실에 의존**한다. 누군가 타입 안전성을 이유로 `findTop4RunnersByBestDuration`을 JPQL로 바꾸면 재계산이 변경 전 데이터로 수행되고 리드모델이 **조용히 옛 값으로 덮어써진다.** 테스트는 `flushAndClear`를 명시 호출하므로 통과할 수도 있다 — 즉 안전망이 없다. 비용이 주석 한 블록인데 실패 모드가 "조용한 데이터 오염"이면 계산은 끝났다.
- **수정 대상**: `src/main/java/soma/ghostrunner/domain/course/application/CourseReadModelWriter.java:75-78` (`recalculate` javadoc). MAJOR-1 Task와 동일한 javadoc 블록이므로 **같은 커밋으로 처리한다.**
- **수정 내용**: javadoc에 `<b>호출자 계약</b>` 항목 추가 —
  - 이 메서드는 `running_record`를 DB에서 직접 집계하므로, 호출 전에 러닝 변경분(공개 여부 토글·소프트 삭제)이 **DB에 반영돼 있어야** 한다.
  - 현재 두 호출 경로가 성립하는 이유를 적는다: `deleteInRunningIds`는 `@Modifying` 벌크 JPQL이라 즉시 실행(+`clearAutomatically`), `updateRunningPublicStatus`의 더티 체킹 변경분은 재계산 쿼리가 **네이티브**라 세션 전체가 플러시되기 때문.
  - **경고**: "재계산 쿼리를 JPQL로 바꾸면 이 보장이 사라진다. JPQL의 자동 플러시는 쿼리가 건드리는 query space에 미반영 변경이 있을 때만 발생한다."
- **예상 작업량**: **S** (주석만, 코드 변경 없음)
- **검증 방법**: 컴파일 + 전체 테스트 그린(동작 변화 없음). 리뷰어가 javadoc만 읽고 "왜 지금 동작하는지" 답할 수 있으면 성공.

---

### 별도 티켓 권장 항목 (DEFER)

#### [MAJOR-3] X락 핫스팟 — 락 이전 빠른 탈락 최적화

- **왜 나중에 해도 되는가**: 리뷰 본문이 스스로 못 박았듯 **"이건 최적화이지 버그 수정이 아닙니다."** 정확성은 현재도 완전하다(원칙 36 — *"You Can't Write Perfect Software"*). 그리고 이건 잘못된 코드가 아니라 **강한 일관성을 선택한 대가**이며, 그 선택(설계 §0-1)은 이 PR의 핵심 판단이므로 존중한다.
- **되돌림 가능성(원칙 2)**: `applyRun` 내부 구현 변경이고 공개 시그니처·DB 스키마·외부 계약이 전혀 안 바뀐다. 나중에 고치는 비용이 지금과 동일하다.
- **지금 하면 안 되는 이유**: double-checked 비잠금 판정은 "미탐이 안전한 이유"(단조성 + 재계산 정정)에 대한 논증이 필요한 변경이다. 부하 데이터 없이 지금 넣으면 **근거 없는 복잡도**만 남는다.
- **분리 티켓 조건**: PR-2 부하 테스트에 "같은 코스 1000VU 동시 완주" 시나리오를 포함하고, 실측에서 `innodb_lock_wait_timeout` 근처의 대기가 관측되면 그때 `wouldChangeTopRunners` 최적화를 착수한다.
- **단, 저비용 방어책 1건은 즉시 반영**: `applyRun` javadoc의 락 보유 구간 경고는 **FIX Task [MAJOR-1]에 흡수**했다(위 4번 항목).

#### [MINOR-2] 리포지토리 JPQL의 `Running` 엔티티 참조

- **왜 나중에 해도 되는가**: 원칙 2 — 내부 구현이고 동작이 완전히 동일하다. 언제든 네이티브로 바꿀 수 있다. 원칙 1(깨진 유리창)을 검토했으나, 이 리포지토리는 이미 네이티브 쿼리 2종으로 `running_record` **테이블**을 참조하고 있어 "코스가 러닝을 조회한다"는 경계 넘기 자체는 이 PR이 새로 만든 관행이 아니다. JPQL이 추가한 것은 컴파일 타임 결합 한 겹뿐이고, javadoc에 선택 사유(MySQL EXISTS의 BIGINT 반환)가 이미 기록되어 있어 다음 사람이 오해할 여지가 낮다.
- **분리 티켓 조건**: **PR-2가 같은 파일(`CourseReadModelRepository`)의 `findCoursesForMap`을 확장한다(설계 §3-4).** 같은 파일을 두 번 흔드는 것보다 그때 함께 정리하는 편이 낫다. 티켓 내용: (a) 네이티브 전환으로 스타일 통일 + `deleted = FALSE` 명시(3개 쿼리 필터가 같은지 **눈으로 확인 가능**해지는 것이 실익), (b) `COUNT(*) > 0` → `LIMIT 1` 조기 종료.

#### [MINOR-4] 재공개 시 재계산 없음 (생성 경로와 비대칭)

- **왜 나중에 해도 되는가**: 리뷰 본문이 "**설계 위반이 아니며 논리적으로도 현재는 안전하다**"고 판단했다. 실제 결함이 아니라 **미래의 특정 변경(누군가 `applyRun`에 `is_public` 조건을 넣는 것)에 대한 보험**이다. 원칙 2 — 지금 넣으나 나중에 넣으나 비용이 같다.
- **분리 티켓 조건**: PR-2에 묶는다. 백필 러너가 "공개 상태가 되는 모든 경로는 진실의 원천으로부터 재구성된다"는 단일 규칙을 어차피 코드로 표현하게 되므로, 그때 재공개 경로도 같은 규칙에 편입시키는 것이 자연스럽다. 3줄짜리 변경이고 이미 X락을 보유 중이라 추가 비용은 사실상 없다.

#### (CRITICAL-2 파생) Flyway 도입

- **왜 나중에 해도 되는가**: 리뷰의 권장안이며 방향에 동의한다. 다만 이건 **PR-1의 범위가 아니라 인프라 릴리스 단위**다(원칙 3 — Good Enough). `V1__baseline.sql` 현 스키마 덤프 + 전 프로필 검증 + 배포 파이프라인 조정이 따라오므로, 리드모델 리팩토링 PR에 끼워 넣으면 리뷰 범위가 폭발한다. 당장의 위험은 위 FIX Task(DDL 아티팩트 + 적용 절차 명문화)로 막힌다.
- **분리 티켓 조건**: 별도 인프라 티켓. 함께 다룰 것 — `data.sql` 시딩을 `writer.syncPublicity()` 기반 컴포넌트로 전환(CRITICAL-1 개선 이유 2), `sql.init.mode=always` 컨텍스트 로딩 스모크 테스트를 CI에 추가(개선 이유 3). 셋 다 "스키마·시드 드리프트를 도구가 막는다"는 같은 주제다.

#### (MINOR-5 파생) 호출부 명시적 `entityManager.flush()`

- **왜 나중에 해도 되는가**: (a)안이 (b)안보다 견고한 건 맞지만 `EntityManager`를 `RunningCommandService`에 주입해야 한다. 계약이 javadoc으로 명시되면 실질 위험은 제거되므로, 주입 비용을 지금 치를 이유가 없다. 재계산 쿼리를 JPQL로 전환하려는 시점이 오면 그때 (a)를 함께 넣는다.

---

### 넘어가도 되는 항목 (PASS)

#### [MINOR-1] `MAX_RANK` ↔ `LIMIT 4` 결합

**왜 괜찮은가**: 원칙 5(DRY)를 적용했으나 해당하지 않는다. DRY가 묻는 것은 "중복이 **불일치로 이어질 가능성**"인데, 리뷰 스스로 "TOP4는 랭킹 도메인이 아닌 **N=4 고정 뷰 데이터**라는 게 확정 전제"라고 적었다. 변할 일이 없는 값은 지식의 중복이 아니라 **상수의 물리적 실체**다. 게다가 엔티티가 `top1~top4` 컬럼 4개로 물리 고정되어 있어, `MAX_RANK`만 바꾸는 시도는 반드시 엔티티 매핑 작업을 동반하므로 조용히 지나갈 수 없다. 리뷰가 제안한 javadoc 한 줄은 반대하지 않는다 — 다른 FIX 작업으로 `TopRunners.java`를 열게 되면 함께 넣어도 좋다. 그것 때문에 별도로 파일을 열 필요는 없다.

#### [MINOR-3] 엔티티가 잘못된 입력을 조용히 무시

**왜 괜찮은가**: 원칙 4(Crash Early)가 후보였고 "삼키지 말고 터뜨려라"는 원칙적으로 옳다. 그러나 원칙 36(*"You Can't Write Perfect Software"* — 과도한 FIX 방지)을 적용해 PASS한다. 근거 셋:

1. **잘못된 값이 도달할 경로가 없다.** 리뷰가 직접 확인했듯 `updateRunnersCount`는 `COUNT(...)`(≥0)와 `getRunnersCount()+1`(≥1)만, `rename`은 `StringUtils.hasText`로 이미 걸러진 값만 받는다. 지금 예외로 바꾸면 **한 번도 안 던져질 예외**를 추가하는 것이다.
2. **MANDATORY 결합이 트레이드오프를 뒤집는다.** 이 엔티티는 러닝 저장과 같은 트랜잭션 안에 있다. 예외를 던지면 미래의 계산 실수 하나가 "카운트가 안 바뀜"(현재)에서 "**사용자가 방금 뛴 기록의 유실**"(변경 후)로 격상된다. 리드모델은 부가 데이터인데 그 방어 코드가 본 데이터를 날리는 구조는 실용적이지 않다.
3. **기존 테스트가 현재 동작을 정책으로 고정**하고 있다(`CourseReadModelTest`의 "음수는 무시되고 기존 값이 유지된다"). 이 PR의 목적과 무관한 정책 변경을 위해 통과 중인 테스트를 뒤집는 것은 원칙 3(Good Enough) 위반이다.

다만 리뷰의 마지막 지적 — **"어느 쪽을 택하든 '의도된 정책'이라는 게 드러나야 한다"** — 은 유효하다. 정책을 유지하는 쪽을 택했으므로, 두 메서드 javadoc에 "리드모델은 부가 데이터이므로 잘못된 입력에 예외를 던져 본 트랜잭션(러닝 저장)을 롤백시키지 않는다"는 한 줄을 남기는 것을 권한다. 이는 FIX가 아닌 권고이며, 다른 작업으로 `CourseReadModel.java`를 열 때 함께 넣으면 된다.

#### [MINOR-6] `@Component` vs `@Service`

**왜 괜찮은가**: 기능 차이가 없고 전파 위험도 없다. 원칙 1(깨진 유리창)을 검토했으나 이건 **아키텍처 규칙 위반이 아니라 스테레오타입 취향**이다 — 어느 쪽이든 `application` 계층의 스프링 빈이며 CLAUDE.md의 패키지 레이아웃 규칙을 위반하지 않는다. 게다가 이 클래스의 javadoc은 "리드모델의 **유일한 쓰기 진입점**"이라는 역할을 이미 5줄에 걸쳐 설명하고 있어, "왜 이것만 다르지?"라는 질문에 대한 답이 사실상 파일 안에 있다. 다음 사람이 `@Service`로 통일하고 싶다면 그때 바꿔도 아무 비용이 없다(원칙 2).

---

### 실용주의 프로그래머의 한마디

**이 PR의 문제는 코드 안에 없습니다. 코드 밖에 있습니다.**

FIX 5건 중 코드 구조를 건드리는 건 `sorted()` 한 줄뿐입니다. 나머지는 시드 SQL, DDL 스크립트, 로그, 주석 — 전부 "코드가 코드 밖에 요구하는 것"들입니다. 리뷰어의 질문 *"이 변경이 코드 밖에서 요구하는 것은 무엇인가?"* 가 이 PR에서는 정확히 정답을 짚었습니다.

그래서 판정도 그 방향으로 갈렸습니다. **구조·설계·VO 분리·테스트는 단 한 건도 FIX 대상이 아닙니다.** MANDATORY도, X락 내재화도, Writer 단일 진입점도 그대로 둡니다. 심지어 리뷰가 MAJOR로 올린 X락 핫스팟(MAJOR-3)조차 DEFER입니다 — 리뷰 본인이 "버그가 아니라 강한 일관성의 대가"라고 썼고, 그 대가는 이 팀이 **알고 지불하기로 한** 것이기 때문입니다. 알고 지불하는 비용은 부채가 아닙니다. **모르고 지불하는 것이 부채입니다.**

세 가지만 기억하십시오.

**하나. "나중에 배포하니까 지금은 됐다"는 DDL을 미루는 이유가 될 수 없습니다.** 배포 일정에 여유가 있다는 사실이 CRITICAL-2를 DEFER로 만들지 않은 이유입니다. 스키마는 코드와 다른 릴리스 단위이고, 그 순서를 사람이 기억해야 하는 구조는 **가장 바쁠 때, 가장 롤백이 급할 때** 어긋납니다. 5분이면 쓰는 SQL 파일 하나가 그 리스크를 없앱니다. 반면 Flyway는 DEFER했습니다 — 방향은 맞지만 이 PR에 끼워 넣으면 리뷰 범위가 폭발하고, 그건 실용주의가 아니라 완벽주의입니다.

**둘. 락을 여러 개 잡으면 순서를 정하십시오. 예외는 없습니다.** MAJOR-1이 무서운 건 데드락 자체가 아니라, **그 발생 조건을 클라이언트가 정한다**는 점입니다. 재현이 안 되고, 재시도하면 되고, 로그에만 남습니다. 원인을 찾는 데 2주가 걸리는 종류의 버그가 정확히 이겁니다. 고치는 데는 `sorted()` 한 줄이 듭니다. 이 비대칭이 판정을 결정했습니다.

**셋. 로그를 지우는 것은 리팩토링이 아닙니다.** `ReadModelSyncListener`를 지운 건 훌륭한 결정이었습니다. 이중 반영 경로를 없앴으니까요. 하지만 그 안에 있던 `log.warn` 한 줄은 **제거 대상 코드가 아니라 운영 인터페이스**였습니다. 삭제한 사람이 되살릴 책임이 있다는 게 원칙 3의 뜻입니다 — 내가 만든 코드는 내가 책임진다. 여기서 "내가 만든 코드"에는 **내가 지운 코드**도 포함됩니다.

마지막으로, PASS 3건에 대해. 넘어가는 것은 태만이 아니라 **판단**입니다. `@Component`를 `@Service`로 바꾸는 데 든 30초는 되찾을 수 있지만, 리뷰 지적을 전부 FIX로 처리하는 팀은 다음번엔 리뷰를 안 하게 됩니다. 리뷰의 신뢰는 **"이 사람이 FIX라고 하면 진짜 고쳐야 한다"**는 데서 나옵니다. 그 신뢰를 지키려면 PASS도 정확히 찍어야 합니다.

> **완벽한 코드는 없습니다. 하지만 깨진 유리창은 고쳐야 합니다.**
> 이 PR에 깨진 유리창은 5장이고, 그중 4장은 유리가 아니라 **창틀 바깥**에 있습니다.
