---
name: auto-team-loop
description: |
  설계 → TDD 구현 → 리뷰/수정 루프 → 테스트 검증의 4단계 파이프라인을 콘솔이 직접 오케스트레이션하는 자동화 스킬. 각 단계의 전문 에이전트(.claude/agents/)를 콘솔이 Agent 도구로 직접 호출한다. 리뷰 단계는 Pragmatic Action Decider가 FIX 0건을 낼 때까지 수정 사이클과 핑퐁한다.
  Triggers: "에이전트 팀 루프", "팀 자동화 루프", "auto team loop", "설계부터 테스트까지", "design tdd review test 루프"
  Do NOT use for: 단일 에이전트만 필요한 작업(직접 Agent 호출), 단순 코드 리뷰(/code-review), 커밋/PR만 필요한 경우(/split-pr)
argument-hint: "requirements-md-path | 자연어 요구사항"
allowed-tools: Agent, SendMessage, Read, Glob, Grep, Bash, AskUserQuestion, TaskCreate, TaskUpdate, TaskList, TaskGet
---

# Auto Team Loop

설계 → TDD 구현 → 리뷰/수정 핑퐁 → 테스트 검증의 4단계 파이프라인을 **콘솔(메인 세션)이 직접 오케스트레이션**한다. 각 에이전트는 `.claude/agents/*.md`에 정의되어 있으며, 콘솔이 `Agent` 도구의 `subagent_type`으로 직접 호출한다.

## 사용 가능한 에이전트

```
!`ls .claude/agents/`
```

## 핵심 원칙 (반드시 모든 단계를 빠짐없이 실행)

1. **콘솔이 모든 에이전트를 직접 호출한다** — 리드 성격의 에이전트(Design Reviewer / TDD Team Lead / Test Team Lead / Pragmatic Action Decider)는 분석·판단·문서 작성 전용이며, 다른 에이전트를 호출하지 않는다. 에이전트 간 결과 전달은 콘솔이 이전 결과를 다음 프롬프트에 첨부하는 방식으로 수행한다.
2. **각 단계는 명시적 산출물로 다음 단계에 넘어간다** — Phase 1은 `docs/design/*.md`, Phase 2는 통과하는 테스트 + 구현 코드, Phase 3은 리뷰 보고서 + FIX 0건, Phase 4는 검증 보고서.
3. **Review→Fix 핑퐁 루프는 최대 5회** — 무한 루프 방지. 5회 초과 시 사용자에게 보고하고 중단.
4. **인수가 없거나 모호하면 묻는다** — 빈 인수, 존재하지 않는 파일 경로, 너무 추상적인 요구사항은 `AskUserQuestion`으로 확인.
5. **순차 의존 호출은 `run_in_background: false`로, 독립 호출은 같은 메시지에 다중 Agent 호출로 병렬 실행**한다.
6. **외부 API 불변** — 설계·구현·리뷰 전 단계에서 `docs/core/05-api.md`의 기존 엔드포인트 스펙을 변경하지 않는다.

## 인수가 없을 때 안내

`$ARGUMENTS`가 비어 있으면, 다음 안내를 출력하고 사용자 입력을 기다린다:

---

**Auto Team Loop을 시작합니다.** 아래 형식 중 하나로 입력해 주세요.

| 형식 | 예시 |
|------|------|
| **요구사항 MD 파일** | `docs/requirements/feature-x.md` |
| **자연어 요구사항** | `코스 즐겨찾기 기능 추가. 러너가 코스를 즐겨찾기에 등록/해제할 수 있어야 함...` |
| **티켓 + 메모** | `SGMR-123 코스 랭킹 조회 캐싱` |

---

## 에이전트 호출 매핑

`Agent` 도구의 `subagent_type`에 사용할 이름 (에이전트 정의의 `name:` 필드와 일치해야 함):

| 단계 | 호출 순서 | subagent_type | 역할 |
|------|---------|---------------|------|
| 설계 | 1 | `requirement-analyst` | 요구사항 비판적 분석 + 엣지 케이스 도출 |
| 설계 | 2 | `codebase-explorer` | 기존 패턴/의존성/스키마 현황 파악 |
| 설계 | 3 | `architect` | API/도메인/컴포넌트/DDL 설계 |
| 설계 | 4 | `design-reviewer` | 검증 + `docs/design/*.md` 생성 (유일한 MD 생성 권한) |
| TDD | A | `tdd-team-lead` | Task 분할 (분석만) |
| TDD | B | `red` | 실패하는 테스트 작성 + 실행 확인 |
| TDD | C | `green` | 테스트 통과 최소 구현 |
| TDD | D | `refactor` | 품질 개선 (테스트 통과 유지) |
| 리뷰 | 1 | `code-quality-auditor` | 10차원 평가 + 보고서 작성 |
| 리뷰 | 2 | `pragmatic-action-decider` | FIX/DEFER/PASS 판정 + FIX Task 정의 |
| 테스트 | 0 | `test-team-lead` | 변경 분석 → 작업 명세 (분석만) |
| 테스트 | 1a | `unit-test-runner` | 단위 테스트 실행/수정 |
| 테스트 | 1b | `build-validator` | 컴파일/테스트/빌드 검증 (1a와 병렬) |
| 테스트 | 2 | `http-test-generator` | E2E `.http` 파일 생성 |

## 실행 순서

### Phase 0: 입력 검증 및 컨텍스트 준비

`$ARGUMENTS`를 파싱하여 요구사항을 확정한다.

| 조건 | 동작 |
|------|------|
| MD 파일 경로 | `Read`로 파일 존재 확인. 없으면 사용자에게 재확인 |
| 자연어 요구사항 (50자 이상) | 그대로 컨텍스트로 사용 |
| 너무 짧거나 모호 | `AskUserQuestion`으로 보강 (배경/목적/범위) |

현재 브랜치를 확인한다:

```bash
git branch --show-current
git status --short
```

브랜치가 `dev`/`main`인 경우 작업 브랜치를 만들지 사용자에게 질문한다 (기본 브랜치 직접 작업은 위험하므로 멈춤).

---

### Phase 1: 설계

#### Step 1-1: Requirement Analyst 호출

```
Agent({
  subagent_type: "requirement-analyst",
  description: "요구사항 분석",
  run_in_background: false,
  prompt: "{요구사항 컨텍스트 전체}. 분석 결과를 최종 텍스트로 보고하세요. MD 파일은 생성하지 마세요."
})
```

반환된 텍스트(요구사항 명세 + 엣지 케이스 + 미결 사항)를 보존한다. 미결 사항이 있으면 `AskUserQuestion`으로 확인 후 보강한다.

#### Step 1-2: Codebase Explorer 호출

```
Agent({
  subagent_type: "codebase-explorer",
  description: "코드베이스 탐색",
  run_in_background: false,
  prompt: "{Step 1-1 결과 첨부}. 관련 도메인의 기존 패턴, 의존성, 스키마 현황을 파악하여 텍스트로 보고. MD 생성 금지."
})
```

#### Step 1-3: Architect 호출

```
Agent({
  subagent_type: "architect",
  description: "기술 설계",
  run_in_background: false,
  prompt: "{Step 1-1, 1-2 결과 첨부}. API/도메인/컴포넌트/DDL 설계를 작성하여 텍스트로 보고. 기술 결정은 트레이드오프와 함께 제시. 외부 API 불변(docs/core/05-api.md) 준수. MD 생성 금지."
})
```

Architect가 사용자 확인이 필요한 결정을 보고하면, 콘솔이 `AskUserQuestion`으로 답을 받아 반영을 요청한다 (필요 시 `SendMessage`로 동일 에이전트에 이어서 전달).

#### Step 1-4: Design Reviewer 호출 (MD 생성 권한 부여)

```
Agent({
  subagent_type: "design-reviewer",
  description: "설계 검증 및 MD 생성",
  run_in_background: false,
  prompt: "{Step 1-1~1-3 결과 전체 첨부}. 3관점(고객/프로젝트/성능) 검증 + 체크리스트 검증 수행 후, 통과 시 docs/design/{주제}-design.md 파일을 생성. 실패 시 어떤 단계로 돌아가야 하는지 명시."
})
```

| Reviewer 결과 | 동작 |
|--------------|------|
| 통과 → MD 생성됨 | 경로(`$DESIGN_DOC`) 기록 후 Step 1-5 진행 |
| 실패 → 설계 반환 | Step 1-3부터 재실행 (재시도 최대 3회) |
| 실패 → 분석/탐색 반환 | 해당 Step부터 재실행 |
| 3회 초과 실패 | 사용자에게 보고 후 중단 |

#### Step 1-5: 사용자 최종 승인

```
AskUserQuestion("설계 문서 docs/design/...md 가 생성되었습니다. 다음 단계(TDD 구현)로 진행할까요?", options: ["진행", "수정 요청", "중단"])
```

| 응답 | 동작 |
|------|------|
| 진행 | Phase 2 |
| 수정 요청 | Design Reviewer에게 피드백 전달 → MD 재생성 |
| 중단 | 전체 루프 중단 |

---

### Phase 2: TDD 구현

#### Step 2-1: Task 분할 — TDD Team Lead 호출 (분석만)

```
Agent({
  subagent_type: "tdd-team-lead",
  description: "Task 분할 분석",
  run_in_background: false,
  prompt: "설계 문서 {$DESIGN_DOC} 를 읽고, 컴포넌트별로 Red/Green/Refactor Task를 분할하여 텍스트로 응답하세요. 의존성 순서(blockedBy)와 병렬 가능 여부도 명시. 직접 다른 에이전트를 호출하지 말고 분할 결과만 보고하세요."
})
```

반환된 Task 목록을 콘솔이 `TaskCreate`로 등록한다 (blockedBy 관계 포함).

#### Step 2-2: TDD 사이클 실행

각 컴포넌트마다 Red → Green → Refactor 순서로 콘솔이 직접 호출한다. 독립 컴포넌트의 사이클은 병렬 실행 가능 (같은 메시지 내 다중 Agent 호출).

```
Agent({
  subagent_type: "red",
  description: "Red: {컴포넌트}",
  prompt: "Task ID: {id}. 설계 문서 {$DESIGN_DOC} 의 {섹션}을 기반으로 실패하는 테스트를 작성하고 ./gradlew test --tests 로 실패를 확인하세요. 완료 시 Task를 completed로 변경."
})
```

Red 완료 후 Green, Green 완료 후 Refactor를 같은 패턴으로 호출한다 (Green에는 Red가 작성한 테스트 파일 경로, Refactor에는 구현/테스트 파일 경로를 첨부).

#### Step 2-3: 사이클 완료 검증

```bash
./gradlew test 2>&1 | tail -30
```

| 조건 | 동작 |
|------|------|
| 모든 테스트 통과 | Phase 3 진행 |
| 실패 발생 | 실패 원인의 Task를 다시 Green 또는 Refactor에게 할당 |

변경된 파일 목록(`$CHANGED_FILES`)을 `git diff dev --name-only`로 기록한 채 Phase 3으로 진입.

---

### Phase 3: 리뷰 + 수정 핑퐁 루프

#### Step 3-1: 반복 변수 초기화

| 변수 | 초기값 | 용도 |
|------|--------|------|
| `$ITER` | 1 | 현재 반복 회차 |
| `$MAX_ITER` | 5 | 최대 반복 횟수 |

#### Step 3-2: Code Quality Auditor 호출

```
Agent({
  subagent_type: "code-quality-auditor",
  description: "코드 품질 리뷰 회차 {$ITER}",
  run_in_background: false,
  prompt: "설계 문서 {$DESIGN_DOC} 대비 구현 검증. 변경 파일: {$CHANGED_FILES}. 10차원 평가 후 docs/reviews/{브랜치}-iter{$ITER}.md 파일로 보고서 저장."
})
```

생성된 보고서 경로를 `$REVIEW_REPORT`에 기록.

#### Step 3-3: Pragmatic Action Decider 호출

```
Agent({
  subagent_type: "pragmatic-action-decider",
  description: "FIX/DEFER/PASS 판정 회차 {$ITER}",
  run_in_background: false,
  prompt: "보고서 {$REVIEW_REPORT} 를 읽고 각 이슈에 대해 7가지 원칙 기반으로 FIX/DEFER/PASS 판정. 보고서 파일 아래에 판정 결과 섹션을 추가하고, FIX Task 목록을 명시. 응답에는 FIX 개수와 보고서 경로를 반환."
})
```

#### Step 3-4: 종료 조건 검사

| 조건 | 동작 |
|------|------|
| FIX 0건 | 🎉 Production Ready → Phase 4 |
| FIX > 0 && $ITER < $MAX_ITER | Step 3-5 (FIX 적용 사이클) |
| $ITER >= $MAX_ITER | 사용자에게 잔여 FIX 보고 + 진행 여부 질문 |

#### Step 3-5: FIX Task → Red/Green/Refactor 사이클

`$REVIEW_REPORT`의 FIX Task 각각에 대해:

1. 새 테스트 케이스가 필요한 FIX → `red` → `green` → `refactor` 순서로 호출
2. 리팩토링만 필요한 FIX → `refactor`만 호출 (기존 테스트로 회귀 검증)
3. 검증: `./gradlew test 2>&1 | tail -30`

모든 테스트 통과 시 `$ITER += 1` 후 Step 3-2로 점프 (재리뷰).

---

### Phase 4: 테스트 검증

#### Step 4-1: Test Team Lead 호출 (변경 분석만)

```
Agent({
  subagent_type: "test-team-lead",
  description: "변경사항 분석",
  run_in_background: false,
  prompt: "git diff dev --name-only 로 변경 파일 분류 후, Unit Test Runner / Build Validator / HTTP Test Generator 가 받아야 할 작업 명세를 텍스트로 응답. 직접 다른 에이전트를 호출하지 말 것."
})
```

#### Step 4-2: Unit Test Runner + Build Validator 병렬 호출

같은 메시지에 두 Agent 호출을 함께 보내 병렬 실행한다.

```
Agent({ subagent_type: "unit-test-runner", description: "단위 테스트 실행",
  prompt: "{Test Team Lead가 정의한 테스트 파일 목록}. 실행 후 실패 분석/수정 보고." })

Agent({ subagent_type: "build-validator", description: "빌드 검증",
  prompt: "compileJava → compileTestJava → test → build 순서로 검증 후 보고. (통합 테스트는 Testcontainers — Docker 필요)" })
```

#### Step 4-3: HTTP Test Generator 호출 (Step 4-2 완료 후)

```
Agent({
  subagent_type: "http-test-generator",
  description: "HTTP E2E 생성",
  run_in_background: false,
  prompt: "변경된 컨트롤러(*Api.java) 엔드포인트 분석 → 사용자/기술 관점 테스트 케이스 정의 → http/ 아래에 .http 파일 생성."
})
```

---

### Phase 5: 최종 보고

콘솔이 사용자에게 최종 보고를 작성한다:

```markdown
## 🏁 Auto Team Loop 완료

| 단계 | 산출물 | 상태 |
|------|--------|------|
| Phase 1 (설계) | `docs/design/{...}.md` | ✅ |
| Phase 2 (TDD) | {변경 파일 수} | ✅ |
| Phase 3 (리뷰) | `docs/reviews/{...}-iter{N}.md` | ✅ ({N}회차에 Production Ready) |
| Phase 4 (테스트) | 빌드 ✅ / 테스트 ✅ / HTTP {N}개 | ✅ |

### 다음 액션 제안
- [ ] /split-pr 로 커밋 분리 + PR 생성
- [ ] DEFER Task를 별도 티켓으로 분리
```

## 에러 처리

| 상황 | 동작 |
|------|------|
| Agent 호출 실패 (subagent_type 없음) | `.claude/agents/*.md`의 frontmatter `name:` 필드와 일치하는지 재확인 |
| 빌드/테스트 무한 실패 | 3회 시도 후에도 실패 시 사용자에게 핵심 원인과 함께 보고 |
| Testcontainers 기동 실패 | Docker 데몬 상태 확인 후 사용자에게 보고 (코드 문제와 구분) |
| Review 핑퐁 5회 초과 | 잔여 FIX 목록을 보고하고 사용자 판단 요청 |
| 사용자 거부 (단계 진행 거부) | 진행 상황 요약 후 중단 |

## 주의사항

- **리드 에이전트가 다른 에이전트를 호출하지 못하게 한다**: 프롬프트에 "직접 다른 에이전트를 호출하지 말 것. 분석/판단 결과만 텍스트로 응답할 것"을 명시 (에이전트 정의에서 Agent 도구도 제거되어 있음).
- **MD 파일 생성 권한**: 설계 MD는 Design Reviewer만, 리뷰 보고서는 Code Quality Auditor만 생성한다. 다른 에이전트에게는 "MD 생성 금지" 명시.
- **사용자 확인 체크포인트**: Phase 1 종료 후(설계 승인), Phase 3 5회 초과 시(루프 중단 여부), Phase 4 종료 후(최종 보고). 이외 단계는 멈추지 않고 진행.
- **산출물 경로는 정확히 전달**: `docs/design/...md`, `docs/reviews/...md`, `http/...http` 경로를 후속 Phase 프롬프트에 반드시 포함.
- **에이전트는 이전 맥락을 모른다**: 매 호출 프롬프트에 필요한 이전 단계 결과를 요약·첨부한다. 같은 에이전트와 대화를 이어가려면 `SendMessage`를 사용한다.
- **CLAUDE.md·docs/core 컨벤션 준수**: Auditor가 컨벤션 위반을 잡으면 FIX로 흘러 자동 수정된다.
