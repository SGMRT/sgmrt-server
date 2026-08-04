# 코드 품질 리포트 — CourseReadModel PR-1 (쓰기 측) / 리뷰 회차 2

- 대상: 회차 1 리포트(`docs/reviews/course-read-model-pr1-iter1.md`)의 **FIX 5건 델타 재검증**
- 리뷰 방식: 코드 리딩 + **실행 검증** (TestContainers MySQL 8.0 으로 `data.sql` 실적재 및 원본 대조, `SHOW CREATE TABLE` 로 스키마 대조, 신규 테스트에 대한 **변이(mutation) 프로브**)
- 전체 재감사는 수행하지 않음. DEFER 3건 / PASS 3건은 손대지 않은 것을 확인만 함.
- 검증에 쓴 일회용 테스트는 모두 삭제했고, 저장소에는 흔적을 남기지 않음.

---

## 총평

**FIX 5건 전부 올바르게 적용됐습니다. 그리고 이번에는 눈으로만 보지 않고, 4건을 실제로 돌려서 확인했습니다.**

회차 1에서 제가 가장 걱정했던 "테스트는 초록불인데 dev 서버는 안 뜬다"는 시나리오부터 재현 테스트를 태웠습니다. 결과:

- `spring.sql.init.mode=always` + `defer-datasource-initialization=true` 로 실제 MySQL 8.0 에 `data.sql` 전체를 적재 → **오류 없음**. `Field 'distance_km' doesn't have a default value` 는 나오지 않습니다.
- `course` ↔ `course_read_model` 의 12개 역정규화 필드를 NULL-safe(`<=>`)로 전수 대조 → **드리프트 0건**.
- TOP4·`runners_count` 를 `running_record` 실집계(공개 ∧ ¬삭제 ∧ ¬일시정지)와 대조 → **3개 코스 전부 일치**. 비공개 코스 2는 리드모델 행 없음.
- Hibernate 가 만든 목표 스키마를 `SHOW CREATE TABLE` 로 떠서 `ddl/pr1.sql` 과 대조 → **6개 변경이 타입·NULL 여부까지 정확히 일치**.

특히 좋았던 건 CRITICAL-1 을 "컬럼만 채우고 끝"내지 않은 점입니다. 구 시드의 `owner_uuid` 는 `user-uuid-001~004` 였는데 `member` 테이블에는 **그런 uuid 가 아예 없습니다**(실제는 `test-uuid-001~003`). 이건 제 회차 1 리포트에도 없던 드리프트인데 찾아서 고치셨습니다. PR-2 가 `owner_uuid` 로 소유자 프로필을 조인하는 순간 dev 에서 소유자가 통째로 비어 보였을 문제를 선제적으로 막은 셈입니다. 시드 상단에 "왜 이 값이어야 하는지"(원본 복사 + 집계 일치 + 비공개는 행 없음)를 3줄로 남긴 것도 정확히 다음 사람에게 필요한 정보입니다.

`ddl/pr1.sql` 은 기대 이상입니다. 적용 순서, `DEFAULT 0` 의 이유, **멱등성 없음 경고**(MySQL 은 `ADD COLUMN IF NOT EXISTS` 미지원), 롤백 DDL, 그리고 **`owner_uuid` 를 되돌리지 않는 이유**(nullable 완화 후 생긴 NULL 행 때문에 `NOT NULL` 복원이 실패한다)까지 적혀 있습니다. 롤백 실패 시나리오를 미리 적어둔 DDL 스크립트는 흔치 않습니다. 롤백은 항상 가장 급할 때 하게 되고, 그때 사람은 이런 걸 절대 기억 못 합니다.

MAJOR-1 의 `filter → distinct → sorted` 도 정확합니다. 그리고 이번에 제가 한 가지를 더 했습니다 — **고쳐놓은 코드를 일부러 되돌린 변이를 만들어, 새 테스트가 정말 빨간불이 되는지 확인**했습니다. 결과는 아래에 표로 정리했는데, 요약하면 **테스트는 제 역할을 합니다.** `sorted()` 를 지우면 테스트 1이 깨지고, `distinct()` 만 지워도 테스트 2가 깨집니다. 처음엔 Mockito `InOrder` 의 "부분 순서" 성질 때문에 무효한 테스트일 거라 의심했는데, 실제로 돌려보니 제 의심이 틀렸습니다. 다만 좁은 사각지대 하나는 남아 있어서 MINOR 로 적어뒀습니다.

우려 사항은 이제 **CRITICAL·MAJOR 모두 없습니다.** 남은 건 MINOR 5건이고, 전부 "지금 당장 안 해도 되지만 다음에 이 파일 열 때 같이 하면 좋을 것"들입니다. dev 배포 차단 사유는 사라졌고, 저는 이 PR 에 approve 를 찍겠습니다.

---

## 총점: 89/100 (회차 1: 83 → **+6**)

## 등급: **A** (약간의 개선 후 출시 가능 / dev 배포 차단 사유 없음)

CRITICAL 2건이 해소됐고 **실행으로 확인**까지 됐습니다. S(90+)를 주지 않은 이유는 단 하나 — 시드 정합을 지켜줄 **자동 장치가 여전히 없어서**(MINOR-4) 이번 수정이 "구조적 해결"이 아니라 "이번 한 번 손으로 맞춘 것"이기 때문입니다. 그 티켓이 닫히면 S 입니다.

### 차원별 점수 변동

| 차원 | 회차1 | 회차2 | 변동 사유 |
|------|------|------|-----------|
| 가독성 | 9 | 9 | — |
| 아키텍처 준수 | 8 | 8 | — (MINOR-2 JPQL DEFER 유지) |
| 단일 책임 | 9 | 9 | — |
| 캡슐화 | 9 | 9 | — |
| 테스트 품질 | 9 | 9 | 락 순서 테스트 신규 3건. 변이 프로브로 유효성 실증 — 사각지대 1건은 MINOR |
| 에러 처리 | 7 | **9** | 스킵 `debug` + 상태변화 게이팅 `info` + 재계산 `info` 복원, PII 없음 |
| 성능 | 7 | **8** | 다중 재계산 락 전순서 고정(데드락 회피). X락 핫스팟(MAJOR-3)은 DEFER 유지 |
| 보안 | 9 | 9 | 신규 로그에 uuid·좌표·URL 없음. 내부 PK 만 노출 |
| 설계 일치도 | 8 | **10** | DDL 아티팩트 + 설계 문서 §4 링크, 시드가 원본과 1:1 정합(실측) |
| 유지보수성 | 8 | **9** | 락 순서 규약·flush 선행 계약·락 보유 구간 경고가 javadoc 에 명문화 |

---

## FIX 5건 판정

| # | 항목 | 판정 | 검증 방식 |
|---|------|------|-----------|
| CRITICAL-1 | `data.sql` 리드모델 시드 정합 | ✅ **해소** | 실 MySQL 적재 + 원본/집계 전수 대조 |
| CRITICAL-2 | `ddl/pr1.sql` 배포 아티팩트 | ✅ **해소** | `SHOW CREATE TABLE` ↔ DDL 1:1 대조 |
| MAJOR-1 | 재계산 락 순서 전순서 고정 | ✅ **해소** | 코드 리딩 + **변이 프로브로 테스트 유효성 실증** |
| MAJOR-2 | 로그 복원 + 게이팅 | ✅ **해소** | 코드 리딩 + PII 점검 |
| MINOR-5 | flush 선행 계약 javadoc | ✅ **해소** | 리포지토리 쿼리 native 여부 대조(서술 정확) |

**회귀 검증**: `./gradlew test --tests "soma.ghostrunner.domain.course.*" --tests "soma.ghostrunner.domain.running.application.*"` → **37 클래스 / 189 테스트 / 실패 0 / 에러 0**. FIX 보고서 수치와 일치합니다.

---

## 검증 상세

### CRITICAL-1 — `data.sql` 시드 (✅ 해소, 실행 검증)

일회용 통합 테스트로 **dev/local 과 동일한 부팅 경로**(`ddl-auto: create` → Hibernate DDL → `data.sql`)를 실 MySQL 8.0 컨테이너에서 재현했습니다.

**① 스크립트 적재**: 예외 없음, 컨텍스트 로딩 성공.

**② 원본 ↔ 리드모델 전수 대조** (`course` JOIN `course_read_model` JOIN `member`, 12개 필드 NULL-safe 비교):

```
### DRIFT ROWS = []
```

비교 대상: `name` / `distance_km` / `elevation_average_m` / `elevation_gain_m` / `elevation_loss_m` / `route_url` / `thumbnail_url` / `start_latitude↔start_lat` / `start_longtitude↔start_lng` / `source` / `is_public` / `member.uuid↔owner_uuid`.

**③ 커버리지** (공개 코스만 리드모델 보유):

```
### COVERAGE = [{id=1, is_public=true, rm=1}, {id=2, is_public=false, rm=0},
                {id=3, is_public=true, rm=1}, {id=4, is_public=true, rm=1}]
```

**④ 집계 정합** (시드 TOP4/runners_count ↔ `running_record` 실집계):

```
### AGG = [{course_id=1, runners_count=2, actual_runners=2, top1=(m2,1545), top2=(m1,1590)},
           {course_id=3, runners_count=1, actual_runners=1, top1=(m2,2376)},
           {course_id=4, runners_count=2, actual_runners=2, top1=(m2,1200), top2=(m1,1248)}]
### EXPECTED TOP = [(c1,m2,1545),(c1,m1,1590),(c2,m1,1152),(c3,m2,2376),(c4,m2,1200),(c4,m1,1248)]
```

비공개 코스 2의 러닝(`1152s`)이 어느 리드모델에도 반영되지 않은 것까지 정확합니다.

**arity 확인**: 컬럼 24개 ↔ VALUES 각 행 24개 일치. NOT NULL 컬럼 9종(`course_id`/`name`/`route_url`/`distance_km`/`start_lat`/`start_lng`/`runners_count`/`is_public`/`source`) 전부 충족. `id` 는 `AUTO_INCREMENT` 라 생략이 정상.

### CRITICAL-2 — `ddl/pr1.sql` (✅ 해소, 스키마 대조)

| 컬럼 | 엔티티 매핑 | Hibernate 실제 생성 | `pr1.sql` | 판정 |
|------|------------|--------------------|-----------|------|
| `distance_km` | `CourseProfile.java:13` `Double`, `nullable=false` | `double NOT NULL` | `DOUBLE NOT NULL DEFAULT 0` | ✅ |
| `elevation_average_m` | `CourseProfile.java:16` `Double` | `double DEFAULT NULL` | `DOUBLE NULL` | ✅ |
| `elevation_gain_m` | `CourseProfile.java:19` | `double DEFAULT NULL` | `DOUBLE NULL` | ✅ |
| `elevation_loss_m` | `CourseProfile.java:22` | `double DEFAULT NULL` | `DOUBLE NULL` | ✅ |
| `thumbnail_url` | `CourseReadModel.java:60` `columnDefinition="TEXT"` | `text` (nullable) | `TEXT NULL` | ✅ |
| `owner_uuid` | `CourseReadModel.java:54` `length=36`, nullable 완화 | `varchar(36) DEFAULT NULL` | `MODIFY VARCHAR(36) NULL` | ✅ |

**누락도 없습니다.** 이번 PR 의 엔티티 diff 는 위 6건이 전부입니다. 특히 `RankSlot`(`RankSlot.java:19-21`)이 `Long`/`Integer` **래퍼 타입**이라 `top1~top4` 8개 컬럼이 여전히 `bigint`/`int` nullable 로 생성되고 컬럼명도 `@AttributeOverride` 로 기존과 동일합니다. 여기서 `int` 프리미티브를 썼다면 `NOT NULL` 로 바뀌어 DDL 이 8줄 더 필요했을 텐데, 그렇지 않다는 걸 실제 스키마로 확인했습니다. `@Embeddable` 전환이 DDL 을 요구하지 않는다는 게 **추측이 아니라 실측**입니다.

`DEFAULT 0` 이 Hibernate 생성 스키마(기본값 없음)와 다르지만 `ddl-auto: validate` 는 컬럼 기본값을 검사하지 않으므로 부팅 실패 요인이 아닙니다. 문제 없습니다.

### MAJOR-1 — 락 순서 (✅ 해소, 테스트 유효성 실증)

**코드** (`CourseReadModelWriter.java:117-127`):

```java
List<Long> lockOrderedCourseIds = courseIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
```

순서가 맞습니다. null 을 먼저 걷어내므로 `sorted()`(자연 순서 `Long.compareTo`)에서 NPE 가 나지 않고, 결과는 **중복 없는 오름차순 = 전순서(total order)** 입니다. 두 트랜잭션이 어떤 코스 집합을 어떤 입력 순서로 받아도 대기 그래프에 사이클이 생기지 않습니다. 정렬을 Writer 안에 둔 것도, javadoc 에 "호출자에게 정렬을 요구하면 새 호출자가 생길 때마다 규약이 깨진다"는 **이유**까지 적은 것도 요청대로입니다.

호출 경로도 전수 확인했습니다 — Writer 호출부 6곳 중 다중 락은 `recalculate` 뿐이고 나머지(`applyRun`/`rename`/`syncPublicity`/`delete`)는 전부 단건입니다. `findByCourseIdForUpdate` 를 Writer 밖에서 부르는 코드도 없습니다.

**테스트 유효성 — 변이 프로브 결과**: 신규 테스트가 Mockito `InOrder` 를 쓰는데, `InOrder` 는 원래 **부분 순서** 검증이고 `InOrder.verifyNoMoreInteractions()` 도 "마지막으로 검증된 호출 **이후**"만 봅니다. 그래서 무효한 테스트일 가능성을 의심하고, 두 테스트의 검증 코드를 그대로 복제해 **고친 부분을 되돌린 변이**에 태워봤습니다.

| 변이 | 실제 락 호출 | 테스트1 (`verify 10,20,30`) | 테스트2 (`verify 10,20`) |
|------|-------------|---------------------------|-------------------------|
| `sorted()` + `distinct()` 모두 제거 | T1: `30,10,20` / T2: `20,10,20` | **FAIL (잡음)** | PASS (못 잡음) |
| `distinct()` 만 제거 | `10,20,20` | — | **FAIL (잡음)** |
| `sorted()` 만 제거 | `20,10` | — | **FAIL (잡음)** |
| 정상 구현 | `10,20,30` / `10,20` | PASS | PASS |

**결론: 제 의심이 틀렸습니다.** 어떤 변이를 넣어도 최소 한 테스트가 빨간불이 됩니다. `sorted()` 를 지우면 테스트 1이 세 번째 `verify(30L)` 에서 `VerificationInOrderFailure` 로 깨지고, `distinct()` 만 지워도 테스트 2가 깨집니다. **회귀 방지 장치로서 제 역할을 합니다.**

남은 사각지대는 하나입니다 — 아래 MINOR-1 참조.

### MAJOR-2 — 로그 (✅ 해소)

`applyRun`(`CourseReadModelWriter.java:68-89`)의 게이팅이 의도대로입니다. `topRunnersChanged || firstPublicRun` 일 때만 `info` 를 남기므로 TOP4 에 못 드는 대다수 러닝은 로그를 만들지 않습니다. 엔티티 `applyRun` 의 `boolean` 반환값이 다시 살아나 설계 §1-5 의 API 가 죽은 코드가 아니게 된 것도 좋습니다.

**PII 점검 이상 없습니다.** 신규 로그 3곳의 인자는 `courseId`(PK), `memberId`(내부 PK), `durationSeconds`, `topCount`, `runnersCount` 뿐입니다. `memberUuid`·좌표(`startLat`/`startLng`)·`routeUrl`·`thumbnailUrl` 은 어디에도 찍히지 않습니다. CLAUDE.md 의 prod 러닝 데이터 마스킹 정책과 충돌하지 않습니다.

스킵 경로를 `debug` 로 둔 판단도 맞습니다. 비공개 코스 스킵은 정상 동작이라 `warn` 이면 늑대소년이 되고, `debug` 면 평소엔 조용하다가 조사할 때 레벨만 올리면 됩니다.

### MINOR-5 — flush 선행 계약 javadoc (✅ 해소, 서술 정확)

javadoc(`CourseReadModelWriter.java:100-111`)이 주장하는 근거를 리포지토리에서 대조했습니다. `findTop4RunnersByBestDuration`·`countDistinctPublicRunners` 둘 다 `nativeQuery = true` 가 맞고(`CourseReadModelRepository.java:96-131`), `deleteInRunningIds` 가 `@Modifying` 벌크인 것도 맞습니다. "JPQL 로 바꾸면 이 보장이 사라진다"는 경고도 정확합니다. **문서가 사실과 일치합니다** — 틀린 주석은 없는 주석보다 나쁘니 이게 가장 중요합니다.

---

## 잔여 이슈 (전부 MINOR)

### [MINOR-1] `InOrder.verifyNoMoreInteractions()` 는 "앞쪽의 예상 못 한 락"은 잡지 못한다

**현재 코드** (`CourseReadModelWriterUnitTest.java:55-59`):

```java
InOrder lockOrder = inOrder(readModelRepository);
lockOrder.verify(readModelRepository).findByCourseIdForUpdate(10L);
lockOrder.verify(readModelRepository).findByCourseIdForUpdate(20L);
lockOrder.verify(readModelRepository).findByCourseIdForUpdate(30L);
lockOrder.verifyNoMoreInteractions();
```

**문제점**: 위 변이 프로브에서 확인했듯 이 테스트는 정렬·중복 회귀는 잘 잡습니다. 다만 `InOrder.verifyNoMoreInteractions()` 는 내부적으로 `InvocationsFinder.findFirstUnverifiedInOrder()` 를 쓰는데, 이 함수는 **마지막으로 검증된 호출 이후**의 미검증 호출만 후보로 삼고 그 앞의 미검증 호출을 만나면 후보를 리셋합니다. 즉 **첫 검증 대상보다 앞에서 일어난 예상 밖의 락은 통과**합니다.

구체적으로, 나중에 누군가 `recalculate` 안에서 "코스 정보 최신화" 같은 이유로 `findByCourseIdForUpdate(otherId)` 를 루프 앞에 하나 추가하면 — 락 개수가 늘고 순서 규약이 실질적으로 깨져도 — 두 테스트 모두 초록불입니다. 실제로 프로브에서 `5,10,20,30` 호출에 대해 `verify(10),verify(20),verify(30)` 는 전부 통과합니다.

지금 구현은 루프 하나뿐이라 발생 여지가 없어 MINOR 입니다. 다만 이 테스트의 목적이 "미래의 변경으로부터 락 규약을 지키는 것"이므로, 사각지대를 없애두는 편이 낫습니다.

**개선 코드**:

```java
// then : 호출 "횟수"와 "전체 순서"를 동시에 고정한다
ArgumentCaptor<Long> lockedCourseIds = ArgumentCaptor.forClass(Long.class);
verify(readModelRepository, times(3)).findByCourseIdForUpdate(lockedCourseIds.capture());
assertThat(lockedCourseIds.getAllValues()).containsExactly(10L, 20L, 30L);
verifyNoMoreInteractions(readModelRepository);   // InOrder 버전이 아닌 Mockito 정적 메서드
```

**개선 이유**: `ArgumentCaptor.getAllValues()` 는 실제 호출 인자를 **호출된 순서 그대로** 담습니다. `containsExactly` 는 순서·개수·구성원을 한 번에 단언하므로 `InOrder` 의 "부분 순서" 성질에 전혀 기대지 않습니다. 그리고 `Mockito.verifyNoMoreInteractions(mock)` 은 위치와 무관하게 미검증 호출 전부를 잡습니다. 결과적으로 **"코스마다 정확히 한 번, 오름차순으로, 그 외 아무 락도 없이"** 라는 규약이 문자 그대로 고정됩니다.

세 번째 테스트(`recalculate_withoutCourseIds_acquiresNoLock`)는 `never()` 를 쓰고 있어 문제없습니다. 그대로 두십시오.

---

### [MINOR-2] 데드락 방지가 `course_read_model` 한 테이블 안에서만 성립한다 — 테이블 간 락 순서는 여전히 역전

**현재 코드** (`RunningCommandService.java:210-211`):

```java
runningRepository.deleteInRunningIds(runningIds);      // ① running_record 에 X락 (벌크 UPDATE, 즉시 실행)
courseReadModelWriter.recalculate(affectedCourseIds);  // ② course_read_model 에 X락
```

**현재 코드** (`RunningCommandService.java:189-194`):

```java
running.updatePublicStatus();                            // 더티체킹만 — 이 시점엔 아직 락 없음
...
courseReadModelWriter.recalculate(List.of(course.getId()));
// → ① findByCourseIdForUpdate 로 course_read_model X락
// → ② 네이티브 집계 쿼리 실행 직전 세션 플러시 = 이때서야 running_record UPDATE + X락
```

**문제점**: 두 경로의 락 획득 순서가 **테이블 단위로 반대**입니다.

```
T1 (러닝 삭제)      : running_record → course_read_model
T2 (러닝 공개 전환) : course_read_model → running_record
```

같은 멤버가 같은 코스의 러닝에 대해 두 요청을 진짜 동시에 보내면(두 기기, 더블탭) InnoDB 데드락이 성립할 수 있습니다. `recalculate` 의 `sorted()` 는 `course_read_model` 행들 **사이**의 순서만 고정하므로 이 시나리오는 다루지 못합니다.

발생 조건이 좁아(동일 멤버 + 동일 코스 + 실제 동시) MINOR 로 둡니다. **이번 FIX 가 만든 회귀가 아니라, 회차 1 MAJOR-1 이 다루지 못한 잔여 범위**입니다.

**개선 방향**: 회차 1에서 DEFER 한 MINOR-5 (a)안 — `updateRunningPublicStatus` 에서 `recalculate` **호출 전에** 명시적 `entityManager.flush()` — 을 넣으면 T2 도 `running_record → course_read_model` 순서가 되어 두 경로의 테이블 락 순서가 일치합니다.

```java
running.updatePublicStatus();
eventPublisher.publishEvent(running.createUpdatedEvent());

// 재계산은 DB를 직접 집계하므로 변경분이 먼저 반영돼야 하고(정확성),
// 동시에 running_record → course_read_model 순으로 락을 잡게 되어 deleteRunnings 와 락 순서가 일치한다(데드락 회피)
entityManager.flush();
courseReadModelWriter.recalculate(List.of(course.getId()));
```

**개선 이유**: (a)안을 미뤄둔 원래 명분은 "조용한 데이터 오염 방지" 하나뿐이었는데, **테이블 간 락 순서 정렬**이라는 두 번째 명분이 생겼습니다. 근거가 둘이 되면 우선순위도 달라집니다. 그 티켓에 이 내용을 추가해 두십시오.

---

### [MINOR-3] `pr1.sql` 에 "구버전 코드와의 호환성"과 "적용 후 행 상태 확인"이 빠져 있다

DDL 을 코드보다 먼저 적용하라는 지시는 명확한데, **먼저 적용해도 안전한 이유**가 없습니다. 실제로는 안전합니다 —

- `ADD COLUMN` 5개: 구버전 코드의 INSERT 는 이 컬럼들을 명시하지 않고 `distance_km` 는 `DEFAULT 0`, 나머지는 nullable 이라 그대로 성공합니다.
- `MODIFY owner_uuid NULL`: 제약 **완화**이므로 항상 값을 채우던 구버전 코드에 영향이 없습니다.

배포 담당자가 새벽에 이 파일을 열었을 때 가장 먼저 하는 질문이 "지금 돌고 있는 코드는 안 깨지나?"입니다. 그 답을 파일 안에 두십시오. 한 줄이면 됩니다.

추가로 적용 후 확인 절차에 **행 상태 점검 쿼리**를 권합니다. 지금은 `SHOW CREATE TABLE` 로 스키마만 보게 돼 있는데, `DEFAULT 0` 때문에 **기존 리드모델 행이 전부 `distance_km = 0` 인 상태**로 남습니다. PR-1 에서는 아무도 이 값을 읽지 않아 무해하지만, PR-2 조회 전환이 백필보다 먼저 나가면 지도에 **"0.0 km 코스"** 가 뜹니다.

```sql
-- 적용 후 확인 (PR-2 백필 전까지 이 값이 전부 0 인 것이 정상)
SELECT COUNT(*) AS rows_awaiting_backfill FROM course_read_model WHERE distance_km = 0;
```

이 숫자를 배포 로그에 남겨두면 PR-2 백필 후 0 이 되는지로 **백필 완료를 정량 확인**할 수 있습니다.

---

### [MINOR-4] 시드 정합을 지켜줄 자동 장치는 여전히 없다 (같은 사고가 재발 가능)

이번에 시드를 정확히 맞췄고 제가 실행으로 확인까지 했습니다만, `src/test/resources/application.yml` 에는 여전히 `spring.sql.init.mode` 설정이 없습니다. 즉 **CI 는 `data.sql` 을 한 번도 실행하지 않습니다.** 다음에 리드모델에 NOT NULL 컬럼이 하나 더 붙으면 똑같은 일이 똑같이 반복되고, 똑같이 dev 부팅 실패로 발견됩니다.

회차 1에서 인프라 티켓으로 DEFER 한 항목이라 새 지적은 아닙니다. 다만 **이번 FIX 로 문제가 사라진 게 아니라 이번 한 번 손으로 맞춘 것**이라는 점은 티켓에 분명히 적어두십시오. 이 리포트가 S 등급을 주지 않은 유일한 이유이기도 합니다.

참고로 제가 이번 검증에 쓴 테스트가 사실상 그 스모크 테스트입니다. `IntegrationTestSupport` 를 상속한 빈 테스트에 아래 한 덩어리만 붙이면 CRITICAL-1 유형은 영구히 막힙니다. 착수하면 30분입니다.

```java
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.defer-datasource-initialization=true"
})
class DataSqlSeedLoadTest extends IntegrationTestSupport {
    // 컨텍스트가 뜨기만 하면 성공 — data.sql 이 스키마와 어긋나면 ScriptStatementFailedException 으로 CI 가 막는다.
    // 여기에 course ↔ course_read_model 대조 쿼리까지 넣으면 값 드리프트도 함께 고정된다.
}
```

---

### [MINOR-5] `createPublicReadModel` 경로에서 로그가 시간순과 반대로 보인다

`createPublicReadModel`(`CourseReadModelWriter.java:172-181`)이 `recalculateReadModel` 을 호출하므로, 코스 공개 전환 시 로그가

```
Recalculated read model. course=42, topCount=3, runnersCount=7
Created read model for course=42
```

순서로 남습니다. "재계산 → 생성"처럼 읽혀 인과가 뒤집혀 보입니다. 동작에는 영향이 없고 볼륨도 미미합니다. `log.info("Created read model...")` 를 `readModelRepository.save(...)` 직후로 올리면 해결됩니다. 이것 때문에 따로 파일을 열 필요는 없고, 다른 작업으로 이 파일을 열 때 함께 정리하십시오.

---

## 새 문제(회귀·부작용) 점검 결과

| 점검 항목 | 결과 |
|-----------|------|
| `data.sql` 다른 테이블 시드 파손 | 없음 — 스크립트 전체가 실 MySQL 에 적재 성공 |
| 구 시드 값을 참조하는 코드/테스트/문서 | 없음 (`user-uuid-00N`, `올림픽공원 달리기`, `강남 야간 러닝`, `여의도 한강 순환` 전수 grep → 회차1 리포트 인용문 외 0건) |
| 리드모델 행 수 감소(4 → 3)의 영향 | 없음 — 테스트는 `data.sql` 미실행, 지도 쿼리는 `is_public = true` 필터라 코스 2는 원래 제외 |
| `recalculate` 의미 변화로 인한 호출부 영향 | 없음 — 호출부 2곳 모두 순서 무관. null/중복 필터는 완화 방향이라 기존 동작 유지 |
| 신규 로그로 인한 동작 변화 | 없음 — `applyRun` 의 연산 순서(엔티티 갱신 → EXISTS 판정) 그대로 |
| 신규 로그의 PII 노출 | 없음 — 내부 PK·초 단위 시간만 |
| `@Embeddable` 전환에 따른 미신고 DDL | 없음 — `RankSlot` 이 래퍼 타입이라 TOP4 8컬럼 nullable 유지(실 스키마 확인) |
| `distance_km NOT NULL` 로 인한 신규 실패 경로 | 없음(현재) — `Course.courseProfile` 이 항상 채워짐. 다만 프로필 없는 코스가 생기면 리드모델 생성이 실패하고 MANDATORY 로 본 트랜잭션까지 롤백된다는 구조는 유지 |
| 기존 테스트 회귀 | 없음 — 37 클래스 / 189 테스트 / 실패 0 |
| DEFER·PASS 항목에 손댄 흔적 | 없음 — `MAX_RANK`, JPQL EXISTS, `@Component`, `applyRun` X락 구조 모두 그대로 |

---

## 설계 문서 대비 차이 (회차 1 대비 변동분만)

| 항목 | 설계 | 구현 | 비고 |
|------|------|------|------|
| prod DDL 선적용 (`ADD COLUMN` ×5 + `MODIFY owner_uuid NULL`) | §4 운영 주의 | **반영됨** | `ddl/pr1.sql` 신규 + 설계 문서 §4:431-433 에 링크·미적용 시 영향·담당자 확인 항목 명시 |
| `data.sql` 시드 정합 | 문서에 언급 없음 | **반영됨** | 원본 1:1 정합 + 집계 일치 (실행 검증 완료) |
| prod `ddl-auto` 실제 값 | — | **미확인(외부 주입)** | 문서에 "배포 담당자가 확인해 기록할 것"으로 남음 — **배포 전 액션 아이템으로 유효** |

---

## 선배 개발자의 한마디

**이번 회차에서 제가 배운 게 하나 있습니다. 그리고 그게 이 리뷰의 결론입니다.**

저는 `CourseReadModelWriterUnitTest` 를 읽자마자 "이 테스트 가짜다"라고 생각했습니다. Mockito `InOrder` 가 부분 순서만 본다는 걸 알고 있었고, `verifyNoMoreInteractions()` 의 구현이 마지막 검증 위치 이후만 훑는다는 것도 알고 있었으니까요. 30년쯤 하면 이런 패턴은 코드만 봐도 보입니다.

**그래서 확인해봤습니다. 그리고 제가 틀렸습니다.**

`sorted()` 를 지운 변이를 넣으니 테스트 1이 세 번째 `verify` 에서 정확히 빨간불이 됐고, `distinct()` 만 지운 변이도 테스트 2가 잡았습니다. 제 경험칙은 절반만 맞았습니다 — 사각지대는 있었지만(MINOR-1), 정작 막으려던 회귀는 제대로 막고 있었습니다. 확인 안 하고 리포트를 썼으면 저는 **멀쩡한 테스트를 MAJOR 로 깎고, 팀에게 불필요한 작업을 시켰을 겁니다.**

이게 이번 회차의 교훈입니다.

**하나. 코드 리딩으로 얻은 확신은 가설입니다. 확인하기 전까지는요.**

회차 1의 CRITICAL-1 도 같은 구조였습니다. 그때는 "dev 가 안 뜰 겁니다"라는 제 가설이 맞았고, 이번엔 "테스트가 가짜입니다"라는 가설이 틀렸습니다. 차이는 **어느 쪽이든 돌려보면 30초에서 3분이면 답이 나온다**는 겁니다. 리뷰어가 가설을 사실처럼 쓰면, 팀은 그걸 사실로 받아들이고 움직입니다. 리뷰의 신뢰는 지적의 날카로움이 아니라 **틀렸을 때 틀렸다고 말할 수 있는가**에서 나옵니다.

**둘. 그 습관을 테스트 작성에도 그대로 쓰십시오.**

회귀 방지 테스트를 새로 쓸 때는 **막으려는 그 버그를 일부러 만들어 넣고 빨간불을 한 번 보는 것**을 습관으로 만드십시오. 30초면 됩니다. 이번 테스트는 다행히 통과했지만, 그건 확인해봤기 때문에 아는 것이지 작성 시점에 보장된 게 아니었습니다. `InOrder` 처럼 "직관과 다르게 동작하는 검증 API" 는 생각보다 흔합니다 — `verify(times(1))` 의 기본값, `@Mock` 의 기본 반환값, `assertThat(list).contains(...)` 가 순서를 안 본다는 것까지.

> **빨간불을 한 번도 못 본 테스트는, 초록불도 의미가 없습니다.**

이 PR 은 나가도 됩니다. 남은 MINOR 5건 중 급한 건 없고, MINOR-4(시드 CI 가드) 하나만 별도 티켓으로 살려두십시오. 그게 닫히면 다음 리뷰에서 S 를 드리겠습니다.

---

## 실용주의 판정 (Pragmatic Action Decision)

> 판정자: Pragmatic Action Decider
> 판정 기준: The Pragmatic Programmer (Andrew Hunt & David Thomas) 원칙 기반
> 판정일: 2026-08-04 / 대상: 회차 2 잔여 MINOR 5건
> 작업 범위: **PR-1 (쓰기 측)** — 엔티티 재설계(`TopRunners`/`RankSlot`), `CourseReadModelWriter` 단일 쓰기 진입점, `RunningCommandService`/`CourseService` 연결, `ReadModelSyncListener` 삭제, 시드·DDL 아티팩트. 조회 전환·Spring Cache·백필은 **PR-2 범위**(설계 §PR 분리)이며 이 PR 직후 착수 예정.
> 전제: 회차 1 FIX 5건 적용 완료 → 회차 2 재검증에서 CRITICAL 0 / MAJOR 0, 총점 89 / 등급 A, 리뷰어 결론 "dev 배포 차단 사유 없음".

### 판정 요약

| 이슈 | 심각도 | 판정 | 근거 원칙 |
|------|--------|------|-----------|
| [MINOR-1] `InOrder.verifyNoMoreInteractions()` 사각지대 | MINOR | 🟡 DEFER | 원칙 2 되돌림 가능(테스트 전용) + 원칙 36 완벽한 SW는 없다(현재 트리거 없음) |
| [MINOR-2] 테이블 간 락 순서 역전(`running_record` ↔ `course_read_model`) | MINOR | 🟡 DEFER *(우선순위 상향)* | 원칙 36 + 원칙 3 Good Enough(무손상 실패 모드) — 단, **prod 트래픽 전 마감** |
| [MINOR-3] `pr1.sql` 구버전 호환 근거·행 상태 점검 누락 | MINOR | 🟡 DEFER | 원칙 2 되돌림 가능(실행되는 DDL 자체는 불변) + 원칙 3(백필 검증은 PR-2 관심사) |
| [MINOR-4] 시드 정합 CI 가드 부재 | MINOR | 🟡 DEFER *(기존 티켓 유지)* | 원칙 3 Good Enough(인프라 릴리스 단위) — 회차 1 판정 불변 |
| [MINOR-5] `createPublicReadModel` 로그 시간순 역전 | MINOR | 🟢 PASS | 원칙 36 + 전파 위험 없음(단일 메서드 내부, 동작 무관) |

**집계: FIX 0 / DEFER 4 / PASS 1**

등급 A 가이드라인("FIX 는 CRITICAL 만")을 그대로 적용했고, CRITICAL 은 0건입니다. **이 PR 에서 추가로 고쳐야 할 것은 없습니다. 그대로 `/split-pr` 로 올리십시오.**

DEFER 4건 중 **새로 만들 티켓은 2건뿐**입니다. MINOR-2 는 회차 1의 `(MINOR-5 파생) 명시적 flush` 티켓에 근거를 추가하는 것이고, MINOR-4 는 회차 1의 인프라 티켓 그대로입니다.

---

### 수정 필수 항목 (FIX Tasks)

**없음.**

Task 를 만들지 않는 것도 판단입니다. 회차 2 리포트는 CRITICAL·MAJOR 0 을 **실행 검증으로** 확인했고, 남은 5건은 전부 "동작하는 코드를 더 좋게 만드는 일"입니다. 여기에 FIX 를 하나라도 끼워 넣으면 그건 실용주의가 아니라 완벽주의이고, 이미 통과한 PR 을 한 사이클 더 붙잡는 비용만 발생합니다.

> *"You Can't Write Perfect Software. Protect your code and users from the inevitable errors." — Tip #36*

---

### 별도 티켓 권장 항목 (DEFER)

#### [MINOR-1] 락 순서 테스트를 `ArgumentCaptor` + `containsExactly` 로 전환

- **왜 나중에 해도 되는가**: 원칙 2 — **테스트 코드 전용 변경**이며 프로덕션 바이트코드에 영향이 0 입니다. 지금 고치는 비용과 6개월 뒤 고치는 비용이 완전히 같습니다. 그리고 원칙 36 — 리뷰어가 **변이 프로브로 직접 실증했듯, 이 테스트는 막으려던 회귀(정렬 제거·중복 제거)를 실제로 잡습니다.** 사각지대는 "누군가 루프 *앞에* 새 락을 추가하는" 가정적 시나리오이고, 현재 `recalculate` 는 루프 하나뿐이라 트리거가 존재하지 않습니다.
- **왜 PASS 가 아닌가**: 이 테스트의 존재 이유가 "미래의 변경으로부터 락 규약을 지키는 것"이기 때문입니다. 목적이 미래 방어인 장치에 미래 사각지대가 있다면, 그건 취향 문제가 아니라 남은 부채입니다. 다만 **지금 갚을 필요가 없는 부채**입니다.
- **분리 티켓 조건**: **PR-2 에 묶습니다.** 회차 1에서 DEFER 한 `[MINOR-4] 재공개 시 재계산` 이 PR-2 에서 `syncPublicity` 를 건드리게 되어 있어(회차 1 DEFER 섹션), 그때 `CourseReadModelWriterUnitTest` 를 어차피 엽니다. 같은 파일을 두 번 흔들지 않습니다.
- **티켓에 남길 것**: 회차 2 리포트의 개선 코드 4줄 그대로. `InOrder` → `verify(times(3)) + ArgumentCaptor.getAllValues() + containsExactly` + `Mockito.verifyNoMoreInteractions`. 세 번째 테스트(`never()`)는 손대지 않습니다. 예상 작업량 **S**.

#### [MINOR-2] 테이블 간 락 순서 정렬 — `updateRunningPublicStatus` 에 명시적 `flush()`

- **먼저, FIX 기준 6번(동시성/데이터 정합성)을 검토했습니다.** 이 항목이 이번 5건 중 유일하게 FIX 후보였고, 실제로 데드락 가능성은 **실재합니다.** 그럼에도 DEFER 하는 근거는 셋입니다.

  1. **실패 모드가 무손상(non-corrupting)입니다.** InnoDB 는 대기 그래프 사이클을 즉시 탐지해 한쪽 트랜잭션을 통째로 롤백합니다. 행이 반쯤 갱신되거나 리드모델이 조용히 옛 값으로 덮이는 일은 없습니다. 사용자에게는 재시도 가능한 실패 1회로 나타납니다. FIX 기준 6번이 겨냥하는 것은 **조용한 데이터 오염**이고, 이건 그 유형이 아닙니다.
  2. **트리거가 클라이언트 통제 밖입니다.** 회차 1의 MAJOR-1 을 FIX 로 올린 결정적 이유는 락 순서가 *클라이언트가 보낸 배열 순서*에 종속돼 **재현 가능한 조건**이었기 때문입니다. 반면 이번 건은 동일 멤버가 **같은 러닝 행**에 대해 삭제와 공개 전환을 실제 시계 기준으로 겹쳐 보내야 성립합니다(두 기기 / 더블탭). 원칙적 가능성과 재현 가능성은 다릅니다.
  3. **이번 PR 이 만든 회귀가 아닙니다.** 리포트가 명시했듯 회차 1 MAJOR-1 이 다루지 못한 잔여 범위이고, 다음 배포 대상은 동시성이 사실상 0 인 dev 입니다.

- **되돌림 가능성(원칙 2)**: `entityManager.flush()` 한 줄 + `EntityManager` 주입. 스키마·외부 계약·공개 시그니처가 전혀 바뀌지 않습니다. 나중 비용 = 지금 비용.
- **다만 우선순위는 올립니다.** 회차 1에서 `(MINOR-5 파생)` 티켓을 DEFER 한 명분은 "조용한 데이터 오염 방지" 하나뿐이었는데, 이제 **테이블 간 락 순서 정렬**이라는 두 번째 근거가 붙었습니다. 근거가 둘이 되면 우선순위도 달라집니다.
- **분리 티켓 조건 (마감선 명시)**:
  - 기존 `(MINOR-5 파생) 호출부 명시적 entityManager.flush()` 티켓에 **본 항목을 두 번째 근거로 추가**한다.
  - **마감선: prod 트래픽 유입 전.** dev 배포는 차단하지 않지만, "동시성 결함을 알면서 prod 에 올린다"는 상태로는 넘기지 않습니다. 알고 지불하는 비용은 부채가 아니지만, **마감선 없이 아는 것은 그냥 잊는 것**입니다.
  - PR-2 가 `recalculate` 호출 경로를 다시 만지므로 PR-2 에 흡수하는 것을 1순위로 검토한다.
- **티켓에 남길 것**: 리포트의 개선 코드 그대로(`flush()` + 두 줄 주석 — 정확성 근거와 데드락 회피 근거를 **둘 다** 적을 것). 검증은 `RunningCommandServiceTest` 전량 그린 + 삭제/공개전환 경로 통합 테스트. 예상 작업량 **S**.

#### [MINOR-3] `pr1.sql` 에 구버전 호환 근거 1줄 + 적용 후 행 상태 점검 쿼리 추가

- **왜 나중에 해도 되는가**: 원칙 2 — **주석은 실행되는 DDL 을 바꾸지 않습니다.** 이 항목을 반영하든 안 하든 prod 에 적용되는 `ALTER TABLE` 은 글자 하나 다르지 않고, 적용 결과도 동일합니다. 즉 되돌릴 수 없는 부분에는 아무 영향이 없습니다. 배포 담당자가 "구버전 코드 안 깨지나?"를 묻는다면 답은 **안 깨진다**이고(`ADD COLUMN` 은 전부 기본값 있거나 nullable, `MODIFY` 는 제약 완화), 최악의 경우 작성자에게 물어보면 30초에 끝납니다. 파일 하단 롤백 섹션에는 이미 `owner_uuid` 에 대한 호환성 서술이 있습니다(`pr1.sql:56`).
- **행 상태 점검 쿼리는 PR-2 관심사입니다(원칙 3)**: `distance_km = 0` 행이 문제가 되는 시점은 **조회가 리드모델로 전환되는 PR-2** 이고, 그 쿼리의 진짜 용도는 "백필 완료의 정량 확인"입니다. 백필 러너를 만드는 티켓이 그 검증 절차를 소유하는 것이 자연스럽습니다. PR-1 단계에서는 리포트가 확인했듯 **아무도 이 값을 읽지 않습니다.**
- **분리 티켓 조건**: PR-2 백필 티켓의 **배포 체크리스트 항목**으로 편입한다 —
  - 백필 전 `SELECT COUNT(*) FROM course_read_model WHERE distance_km = 0;` 값을 배포 로그에 기록
  - 백필 후 같은 쿼리가 0 인지 확인 = 백필 완료 판정 기준
  - **PR-2 조회 전환은 백필 완료 확인 이후에만 활성화**(순서가 뒤집히면 지도에 "0.0 km 코스"가 뜬다)
- **선택 사항(차단 아님)**: `/split-pr` 로 커밋을 정리하며 `pr1.sql` 을 어차피 연다면, 구버전 호환 1줄은 3분이면 들어갑니다. 넣어도 좋고 안 넣어도 좋습니다. **다만 이걸 하려고 리뷰 사이클을 한 번 더 돌리지는 마십시오.**

#### [MINOR-4] 시드 정합 CI 가드 (`data.sql` 컨텍스트 로딩 스모크 테스트)

- **회차 1 판정을 유지합니다 — 인프라 티켓 DEFER.** 상황이 달라지지 않았습니다. 원칙 3(Good Enough) — 이건 PR-1 의 범위가 아니라 **인프라 릴리스 단위**이고, `sql.init.mode` 를 테스트 프로필에 켜는 변경은 전 테스트 스위트의 데이터 전제를 건드립니다(`DatabaseCleanserExtension` 와의 상호작용, 189개 테스트의 시드 가정). 리드모델 리팩토링 PR 에 끼워 넣을 크기가 아닙니다.
- **다만 티켓 본문에 이 문장을 반드시 넣으십시오**: *"CRITICAL-1 은 이번 한 번 손으로 맞춘 것이지 구조적으로 해결된 것이 아니다. 리드모델에 NOT NULL 컬럼이 하나 더 붙으면 동일 사고가 동일하게 재발하며, 발견 시점도 동일하게 dev 부팅 실패다."* 리뷰어가 S 등급을 주지 않은 유일한 사유이므로, **티켓 제목에 "S 등급 조건"을 달아두면 잊히지 않습니다.**
- **착수 힌트**: 리포트의 `DataSqlSeedLoadTest` 스니펫이 사실상 완성품입니다(리뷰어가 이번 검증에 실제로 쓴 코드). 여기에 `course ↔ course_read_model` 대조 쿼리까지 넣으면 값 드리프트도 함께 고정됩니다. 예상 작업량 **M**(테스트 자체는 30분, 스위트 영향 확인이 나머지).
- **함께 다룰 것**: 회차 1에서 같은 티켓으로 묶은 Flyway 도입 + `data.sql` 시딩을 `writer.syncPublicity()` 기반 컴포넌트로 전환. 셋 다 "스키마·시드 드리프트를 도구가 막는다"는 같은 주제입니다.

---

### 넘어가도 되는 항목 (PASS)

#### [MINOR-5] `createPublicReadModel` 로그 시간순 역전

**왜 괜찮은가**: 원칙 1(깨진 유리창)부터 순서대로 걸어봤지만 어디에도 걸리지 않습니다.

- **전파 위험 없음** — 단일 메서드(`CourseReadModelWriter.java:172-181`) 안에서 `log.info` 한 줄의 위치 문제입니다. 다른 개발자가 이걸 보고 "우리 팀은 로그를 이렇게 쓰는구나"라고 따라 할 성질의 것이 아닙니다.
- **동작 영향 0, 볼륨 영향 0** — 코스 공개 전환은 저빈도 경로이고, 두 로그 모두 이미 찍히고 있습니다. 순서만 다릅니다.
- **되돌림 자유(원칙 2)** — `save()` 직후로 한 줄 올리면 끝입니다. 언제 해도 비용이 같습니다.
- **리포트 본인의 판단과 일치** — *"이것 때문에 따로 파일을 열 필요는 없고, 다른 작업으로 이 파일을 열 때 함께 정리하십시오."* 동의합니다. **티켓조차 만들지 마십시오.** 티켓 시스템에서 가장 해로운 것은 CRITICAL 이 아니라, 아무도 안 여는 MINOR 티켓 200장이 만드는 소음입니다.

관측성 관점에서도 실질 손해가 없습니다. 두 로그 모두 `course=42` 를 달고 있어 조사할 때는 코스 ID 로 grep 하지 순서로 읽지 않습니다.

---

### 실용주의 프로그래머의 한마디

**FIX 0 건입니다. 그리고 그게 이번 판정의 전부입니다.**

회차 1에서 저는 FIX 5건을 냈습니다. 회차 2에서는 0건입니다. 이 차이가 의미하는 건 코드가 완벽해졌다는 게 아니라, **남은 문제들이 "지금 이 PR 을 붙잡을 만한 가치가 없는" 크기로 내려왔다**는 것입니다. 그 판단선을 매번 다시 긋는 것이 실용주의입니다.

**하나. 판정을 어렵게 만든 건 MINOR-2 하나였습니다.** 데드락 가능성이 실재하고, 제 FIX 기준 6번은 "동시성 문제는 무조건 FIX"라고 적혀 있습니다. 그런데 기준을 그대로 적용하지 않았습니다 — **실패 모드를 봤기 때문입니다.** InnoDB 가 즉시 탐지해 통째로 롤백하는 데드락은 원칙 38(*Crash Early*)이 원하는 바로 그 동작입니다. 죽은 프로그램은 절름발이 프로그램보다 피해가 적습니다. 조용히 잘못된 값을 쓰는 코드였다면 저는 지금 FIX 를 찍었을 겁니다. **"동시성 문제인가"가 아니라 "실패했을 때 무엇이 남는가"를 물어야 합니다.**

**둘. 대신 마감선을 걸었습니다.** DEFER 는 "안 해도 된다"가 아닙니다. MINOR-2 에는 *prod 트래픽 유입 전*이라는 날짜 아닌 사건 기준의 마감선을, MINOR-4 에는 *S 등급 조건*이라는 이름표를 붙였습니다. 마감선 없는 DEFER 는 그냥 잊기로 한 결정이고, 6개월 뒤 새벽 3시에 다시 만나게 됩니다. **알고 지불하는 비용은 부채가 아니지만, 언제 갚을지 안 적은 것은 부채입니다.**

**셋. PR-2 가 바로 뒤에 온다는 사실이 판정의 절반을 결정했습니다.** DEFER 4건 중 3건(MINOR-1, MINOR-2, MINOR-3)의 착지점이 전부 PR-2 입니다. 같은 파일을 두 번 흔드는 것보다 한 번에 정리하는 편이 낫고(원칙 42 — *Take Small Steps*는 걸음을 잘게 쪼개라는 뜻이지, 같은 자리를 두 번 밟으라는 뜻이 아닙니다), PR-2 는 어차피 `Writer`·`CourseReadModelRepository`·백필을 모두 건드립니다. **다만 이건 PR-2 를 실제로 곧 착수한다는 전제 위에 서 있습니다.** PR-2 가 미뤄지면 이 DEFER 3건은 근거를 잃고, 그때는 독립 티켓으로 승격시켜야 합니다.

> **고칠 것이 없다는 결론도 근거가 있어야 합니다. "괜찮아 보인다"와 "이 다섯 가지 이유로 괜찮다"는 다른 문장입니다.**

`/split-pr` 진행하십시오. 이 PR 에서 더 할 일은 없습니다.
