---
title: "feat: Add AnalysisRun flight recorder"
type: feat
status: completed
date: 2026-08-24
origin: docs/brainstorms/2026-08-24-analysis-run-flight-recorder-requirements.md
---

# feat: Add AnalysisRun flight recorder

## 1. Outcome

모든 CLI 분석 요청에 `runId`를 먼저 부여하고, 동기 Embabel 실행의 수명주기와 안전한 실행 metadata를 H2 인메모리 DB에 기록한다. 기존 `pinbabel` 결과에는 추적 가능 여부를 표시하고, `pinbabel-runs`, `pinbabel-run --id`로 최근 실행과 상세 이벤트 및 최종 보고서를 조회할 수 있게 한다.

기록 계층의 장애는 분석 결과의 성공·실패를 바꾸지 않는다. Prompt, LLM 응답, Tool 입력·출력과 credential은 저장하지 않으며, token·비용은 Embabel/provider가 제공한 값만 기록한다.

## 2. Planning-time requirement refinement

요구사항 문서의 F2/R4/AE2는 모든 업무 범위 밖 요청이 Embabel을 시작하지 않는 것으로 표현한다. 그러나 현재 구현에서 업무 범위 판정은 두 종류다.

- 빈 입력과 최대 길이 초과는 결정론적으로 판정할 수 있으므로 Embabel 실행 전에 `REJECTED`로 종료한다.
- “오늘 날씨 알려줘” 같은 의미 기반 범위 밖 요청은 Embabel의 `interpretInput` 결과가 있어야 판정되므로 Embabel을 실행하고, 안전한 intent-classification trace를 남긴 뒤 업무 상태를 `REJECTED`로 종료한다.

이 구분은 사용자와 계획 단계에서 확정했다. 따라서 본 계획에서 R4는 “결정론적 입력 거절은 Embabel 미실행, 의미 기반 범위 거절은 Embabel 실행 후 `REJECTED`”로 정제하여 구현한다. Embabel의 process-completed 이벤트는 기술 trace일 뿐 업무 완료 상태를 결정하지 않는다.

## 3. Actors, flows, and acceptance source

### Actors

- A1 CLI 사용자
- A2 Pinbabel 분석 Application Service
- A3 Embabel Agent 실행기
- A4 실행 기록 저장소

### Key flows

| Flow | Trigger | Outcome | Requirements |
| --- | --- | --- | --- |
| F1 정상 분석 | 유효한 fixture 분석 요청 | 보고서, `runId`, 완전한 safe trace | R1, R2, R3, R5, R8, R10-R13 |
| F2 거절 | 빈 입력·길이 초과 또는 의미 기반 범위 밖 요청 | `REJECTED`; 의미 기반 판정만 Embabel trace 포함 | R1, R4, R6-R8, R13 |
| F3 기록 실패 | run/event/report 저장 중 H2 오류 또는 event cap 도달 | 분석 의미 유지, `traceAvailable=false`, 경고 | R1, R7-R9 |

### Acceptance examples

| ID | Expected behavior | Coverage |
| --- | --- | --- |
| AE1 | 정상 fixture 분석은 `CREATED → RUNNING → COMPLETED`, safe Embabel events와 보고서를 연결한다. | R1, R2, R5, R8, R10, R11 |
| AE2 | 결정론적 거절은 Embabel 이벤트 없이, 의미 기반 거절은 intent trace와 함께 `REJECTED`가 된다. 어느 경우도 입력 원문을 저장하지 않는다. | R1, R4, R6, R7 |
| AE3 | Embabel/LLM 실패는 `FAILED`와 안전한 실패 분류만 남기며 prompt·응답·credential을 노출하지 않는다. | R3, R5, R7, R8 |
| AE4 | event 저장 실패 또는 cap 도달 후에도 성공 보고서를 반환하고 `traceAvailable=false` 및 경고를 표시한다. | R7-R9 |
| AE5 | 20건 초과 시 최근 20건만 결정적 순서로 조회하고, 상세 이벤트는 sequence 오름차순이다. | R12, R13 |
| AE6 | 없는/malformed `runId`는 다른 실행이나 내부 오류 없이 not-found/invalid 결과만 표시한다. | R14 |
| AE7 | 재시작 뒤 이력이 사라져도 최초 범위에서는 정상이다. | R15 |

## 4. Scope

### In scope

- `AnalysisRun` 수명주기와 안전한 trace domain/application model
- Embabel 1.5.0 per-run listener 및 `contextId=runId` 연결
- 명시적 allowlist event mapping, terminal aggregate usage/cost/model metadata
- H2 인메모리 + Liquibase 기반 run/event/report 저장
- 기존 동기 CLI 결과의 `runId`, `traceAvailable`, warning 표시
- `pinbabel-runs` 최신 20건과 `pinbabel-run --id` 상세 조회
- event 저장 실패 격리, 실행별 event cap, 안전한 오류/Internal Code
- 구조·domain·transaction·migration·security·CLI acceptance test

### Out of scope

- 비동기 submit, background worker, cancel, pause, resume
- REST, A2A, A2UI adapter와 protocol contract
- 재시작 후 보존, 외부 운영 DB와 archive/retention job
- prompt/response/tool payload 저장·재생
- 실제 SNS adapter, golden dataset, HITL, MCP
- provider가 주지 않는 token/cost 추정
- 범용 RSQL/filter/page API

## 5. Repository findings

- 분석 entry point는 `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/EmbabelInfluencerAnalysisService.java`이며, 현재 pre-validation 뒤 `AgentInvocation`을 동기 호출한다.
- Embabel agent/actions는 `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/agent` 아래에 있고, 최종 산출물 `InfluencerAnalysisReport`는 이미 `@AggregateRoot`다.
- CLI adapter는 `src/main/java/com/ypkim/pinbabel/influenceranalysis/adapter/in/cli/PinbabelShellCommands.java`이고, `PinbabelChatbot`은 현재 application package에 있어 transport concern과 함께 adapter로 이동할 필요가 있다.
- JPA와 H2 runtime 의존성은 있으나 Liquibase starter, changelog와 datasource/DDL validation 설정은 아직 없다.
- `gradle/backend-compliance.gradle.kts`가 architecture, Liquibase, Internal Code registry 검증을 `check`에 연결한다.
- Internal Code owner prefix는 `PIN-IAN-`이고 `config/internal-code/registry.json`에서 occurrence 단위로 관리한다.
- Embabel 통합 테스트는 `EventSavingAgenticEventListener`를 test support로 사용한다. production recorder는 project-owned listener여야 한다.

### Institutional learnings applied

- LLM 구조화 결과는 domain/report로 승격하기 전에 검증한다.
- GOAP 분기는 action postcondition과 결과 type으로 명확히 하며, process completed와 업무 성공을 혼동하지 않는다.
- Tool 결과 wrapper를 통째로 직렬화하지 않고 허용 metadata만 추출한다.
- provider secret은 환경 변수/BYOK 경계에 유지하고 로그·DB·fixture에 기록하지 않는다.
- 신규 catch/throw 발생 위치마다 고유 Internal Code를 registry와 함께 추가한다.

## 6. High-level design

### Lifecycle

```text
                         semantic refusal
CREATED ──> RUNNING ─────────────────────────> REJECTED
   │          │  └────────────────────────────> FAILED
   │          └───────────────────────────────> COMPLETED
   └── deterministic validation refusal ─────> REJECTED

traceAvailable: true | false  (업무 상태와 직교하는 관측 상태)
```

`AnalysisRunStatus`의 terminal 상태는 `COMPLETED`, `FAILED`, `REJECTED`다. Recorder 실패나 event cap은 이 상태를 바꾸지 않고 `traceAvailable=false`와 안전한 warning을 설정한다.

### Runtime sequence and transaction boundary

```text
CLI
 │  analyze(instruction)
 ▼
Application Service ── create run ──> AnalysisRunStore [short H2 tx]
 │
 ├─ deterministic reject ── finalize REJECTED [short H2 tx]
 │
 └─ mark RUNNING [short H2 tx]
        │
        └─ AgentInvocation(ProcessOptions: contextId + listener)
              │                (LLM/network: no DB transaction)
              ├─ safe event ──> TraceEventStore [one short H2 tx/event]
              └─ outcome/error
        │
        └─ finalize business status + report snapshot [short H2 tx]
 │
 └─ return runId + traceAvailable + result/warning
```

### Package ownership

```text
influenceranalysis
├── application
│   ├── domain
│   │   └── model/analysisrun
│   ├── port
│   │   ├── in/analysisrun
│   │   └── out/analysisrun
│   └── service/analysisrun
└── adapter
    ├── in/cli
    │   ├── PinbabelShellCommands
    │   ├── PinbabelRunShellCommands
    │   ├── PinbabelCliRenderer
    │   └── chat/PinbabelChatbot
    └── out
        ├── embabel/trace
        └── persistence/analysisrun
```

Embabel event types and Spring Data/JPA types remain adapter-private. Application ports expose only project-owned commands and read models. `InfluencerAnalysisReport` aggregate를 `AnalysisRun` aggregate에 직접 참조하지 않고 versioned immutable report snapshot으로 persistence port에 전달한다.

### Safe event allowlist

| Embabel event family | Persisted metadata | Explicitly excluded |
| --- | --- | --- |
| process/plan/replan/goal | event type, time, process/context ID, safe plan/goal identifier | world state, arbitrary replan reason, object `toString()` |
| action | action name, start/end, duration, success | action inputs/outputs, state values |
| tool loop/call | tool name, start/end, duration, success | tool input, result |
| LLM | model/provider metadata, start/end, duration, success, invocation이 실제 제공한 nullable usage | messages, prompt, response, failure payload |
| terminal | status, process ID, running time, models used, recorder가 안전하게 누적한 nullable usage/cost | raw exception, prompt/response |

Mapper는 알려진 event subtype만 switch/visitor 방식으로 처리한다. 새 Embabel event는 기본적으로 무시하고 안전성 검토 뒤 allowlist에 추가한다. Embabel 1.5.0의 `AgentProcess.ownUsage()`는 미제공 token을 0으로 합산하고 `ownCost()`도 pricing/usage가 없으면 0으로 계산하므로, 두 aggregate method를 “provider 제공값”으로 저장하지 않는다. 대신 `LlmInvocationEvent.invocation.usage`의 nullable token과 `llmMetadata.pricingModel` 존재 여부를 확인해 recorder session에서 안전하게 누적한다. 하나라도 제공되지 않은 지표는 최종 snapshot에서도 `null`로 남기며, 모델 목록은 `ownModelsUsed()` 또는 동일한 safe invocation metadata에서 얻는다. 비용 단위는 Embabel 계약에 맞춰 USD로 명시한다.

### Event ordering and cap

- listener instance는 run마다 생성하고 `AtomicLong` sequence와 trace-health flag를 소유한다.
- 현재 SIMPLE process mode에서도 callback thread를 가정하지 않고 sequence 원자성을 보장한다.
- 실행별 최대 저장 event 수는 configuration property로 둔다. 기본값은 1,000으로 시작하며 1 이상인지를 검증한다.
- cap에 도달하면 가능한 경우 `TRACE_TRUNCATED` marker를 한 번 저장하고 이후 event append를 중단한다. 분석은 계속하며 최종 응답/run snapshot을 `traceAvailable=false`와 truncation warning으로 마무리한다.
- 첫 event persistence 실패 뒤 반복 실패를 만들지 않도록 이후 append를 중단한다. 단, DB가 회복됐을 가능성을 위해 최종 run/report 저장은 한 번 별도로 시도한다.

## 7. Data model and migration

### Domain/application model

- `AnalysisRunId`: application correlation identifier를 보존하는 UUID-string Value Object. 내부 Aggregate PK용 TSID 규칙을 외부·correlation ID에 잘못 적용하지 않는다.
- `AnalysisRun`: `@AggregateRoot`; status transition, timestamps, duration, trace health, safe outcome summary를 소유한다.
- `AnalysisRunStatus`: `CREATED`, `RUNNING`, `COMPLETED`, `FAILED`, `REJECTED`.
- `AnalysisTraceEvent`: sequence, event type/time, nullable safe metadata와 duration/success만 가진 immutable Value Object.
- `AnalysisRunMetrics`: nullable prompt/completion tokens, `costUsd`와 model identifiers. 값 미제공과 실제 0을 구분한다.
- `AnalysisRunSummary` / `AnalysisRunDetail`: CLI와 미래 adapter가 공유할 framework-neutral application read model.

### Persistence schema

`analysis_run`

- `run_id` varchar PK
- `status`, `created_at`, `started_at`, `completed_at`, `duration_ms`
- nullable `embabel_process_id`
- `trace_available`, nullable safe `warning_code`, `outcome_code`, `outcome_summary`
- nullable `prompt_tokens`, `completion_tokens`, `cost_usd`, model/provider metadata
- nullable `report_schema_version`, `report_json` CLOB

`analysis_run_event`

- composite PK `(run_id, event_sequence)`
- FK to `analysis_run`
- `event_type`, `occurred_at`
- nullable action/tool/model/provider/process identifiers, `duration_ms`, success flag
- no raw content/payload/exception columns

Indexes:

- recent runs: `(created_at, run_id)` for deterministic descending lookup
- event PK supplies per-run ordered lookup

### Liquibase and H2 policy

- Add Boot-managed `org.springframework.boot:spring-boot-starter-liquibase` without a hand-written version.
- Create master changelog and one additive changeSet with `author: ypkim`.
- Configure explicit in-memory H2 datasource and `spring.jpa.hibernate.ddl-auto=validate`; Liquibase owns schema creation.
- No backfill, destructive alter, rename or external DB compatibility claim is needed in this increment.
- Rollback drops child event table before parent run table. This is acceptable only because the approved scope is process-lifetime H2 test data.

## 8. Error, transaction, and consistency policy

### Transaction eligibility

| Question | Decision |
| --- | --- |
| Participating Business Slices | `influenceranalysis` only |
| Mutable resources | one H2 DataSource/transaction manager |
| External system mutation | none; Embabel/provider call is computation/network I/O |
| Cross-Slice transaction | not applicable |
| Saga/compensation | not applicable; no independently committed business mutation |
| Network call inside DB transaction | prohibited |

Each persistence port operation owns one short local transaction. `saveAndFlush` or explicit flush occurs inside the adapter so constraint/commit-related failure can be classified near the operation. The best-effort recorder wrapper catches outside the transaction proxy boundary, logs only `runId`, operation and Internal Code, marks its in-memory trace health false, and never throws into Embabel or the business analysis path.

Analysis failure remains visible as `FAILED` even if recording also fails. Recording failure must not turn a successful outcome into `FAILED`, nor turn a failed outcome into success. Query persistence failure produces a safe CLI “실행 기록을 조회할 수 없습니다” result and logs a distinct Internal Code without DB details.

## 9. Implementation units

### U1. AnalysisRun domain and application contracts

**Goal:** Establish lifecycle invariants and technology-neutral write/query boundaries.

**Files:**

- Add `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/domain/model/analysisrun/AnalysisRun.java`
- Add `AnalysisRunId.java`, `AnalysisRunStatus.java`, `AnalysisTraceEvent.java`, `AnalysisRunMetrics.java` in the same package
- Add inbound query contracts/read models under `application/port/in/analysisrun`
- Add persistence commands/ports under `application/port/out/analysisrun`
- Add lifecycle/query services under `application/service/analysisrun`

**Behavior:**

- Enforce legal transitions and terminal immutability.
- Calculate duration from owned timestamps, never negative.
- Keep trace health orthogonal to business status.
- Expose latest-20 and detail capabilities without JPA/Embabel types.

**Tests:**

- Add `src/test/java/com/ypkim/pinbabel/influenceranalysis/application/domain/model/analysisrun/AnalysisRunTest.java`: all legal paths, invalid transition, terminal immutability, semantic refusal, trace degradation, duration.
- Add `src/test/java/com/ypkim/pinbabel/influenceranalysis/application/service/analysisrun/AnalysisRunQueryServiceTest.java`: fixed limit 20, not-found, persistence query failure translation.
- Extend architecture tests to assert Domain/Application do not depend on JPA, Spring Data or Embabel event packages.

**Traces:** R1-R4, R8, R12-R16; AE1, AE2, AE5, AE6.

### U2. Liquibase-owned H2 persistence adapter

**Goal:** Persist run, ordered safe events and versioned report snapshots with short transactions.

**Files:**

- Modify `build.gradle.kts` for Boot-managed Liquibase starter.
- Modify `src/main/resources/application.yaml` for explicit in-memory H2, Liquibase changelog and Hibernate validation.
- Add `src/main/resources/db/changelog/db.changelog-master.yaml`.
- Add `src/main/resources/db/changelog/changes/2026-08-24-001-create-analysis-run-tables.yaml` with `author: ypkim` and rollback.
- Add JPA entities, Spring Data repositories, JSON report serializer/mapper and adapter under `adapter/out/persistence/analysisrun`.
- Add unique occurrence entries to `config/internal-code/registry.json` for create/start/event/finalize/query/serialization failures.

**Behavior:**

- Use Boot-configured Jackson to serialize the final structured report with `report_schema_version=1`.
- Reject a report snapshot that violates the domain-owned collection/string bounds instead of persisting an unbounded JSON document; recorder failure policy then marks trace/report history incomplete without changing the analysis result.
- Map persistence projections to application read models; never expose entities/projections.
- Query latest 20 by `createdAt DESC, runId DESC`; detail events by sequence ascending.
- Flush within each adapter transaction and translate failures at the operation boundary.

**Tests:**

- Add `src/test/java/com/ypkim/pinbabel/influenceranalysis/adapter/out/persistence/analysisrun/AnalysisRunPersistenceAdapterTest.java`: lifecycle round trip, report JSON round trip, nullable metrics, deterministic latest 20, ordered events, missing ID.
- Add migration integration test for empty H2 startup, constraints/indexes, rollback validation where supported.
- Add a schema/data inspection assertion proving raw prompt/response/tool payload columns and values do not exist.
- Run compliance Liquibase and Internal Code registry validators.

**Traces:** R2, R3, R6-R10, R12-R16; AE1, AE3-AE7.

### U3. Embabel safe flight recorder

**Goal:** Correlate official Embabel 1.5.0 execution events to a run without persisting untrusted content.

**Files:**

- Add adapter components under `adapter/out/embabel/trace`, including per-run listener factory, explicit safe event mapper, recorder session and terminal metrics mapper.
- Add recorder configuration property for max events per run.
- Add Internal Codes for listener mapping, append and terminal metric failures.

**Behavior:**

- Build invocation with `ProcessOptions.withContextId(runId.value()).withListener(listener)`.
- Allowlist known plan/replan/action/tool/LLM/terminal event subtypes.
- Never call arbitrary event `toString()` and never access/persist messages, tool input/result, LLM response, world-state values or raw failure information.
- Capture nullable token usage from safe `LlmInvocationEvent.invocation.usage`; compute USD cost only when pricing metadata and required usage are present. Aggregate these safe values in the per-run recorder session and never convert “unavailable” to zero.
- Capture model identifiers from safe invocation metadata/terminal model metadata without persisting request or response content.
- Listener/storage exceptions are swallowed at the recorder boundary after safe logging and trace degradation.
- Apply sequence ordering, one truncation marker, configured cap and append short-circuit after failure.

**Tests:**

- Add `SafeEmbabelEventMapperTest`: one test per allowed family plus adversarial secret/prompt/tool payload proving exclusion.
- Add `EmbabelFlightRecorderTest`: sequence under concurrent callbacks, cap edge, single truncation marker, append failure short-circuit, terminal metric presence/absence, and proof that unavailable usage/cost remains `null` rather than fabricated zero.
- Extend `InfluencerAnalysisAgentIntegrationTest` to assert real Embabel planning/action/LLM/terminal event families are correlated by `runId` without raw content.

**Traces:** R5-R9; AE1, AE3, AE4.

### U4. Synchronous analysis orchestration and finalization

**Goal:** Make the existing use case own run lifecycle while preserving analysis semantics.

**Files:**

- Modify `EmbabelInfluencerAnalysisService.java` to allocate `runId` first, perform deterministic validation, establish recorder session and finalize status/report.
- Modify `AnalyzeInfluencerPostsResource.java` into a structured application result that includes `runId`, `traceAvailable` and warnings without CLI rendering responsibility.
- Modify `application/domain/model/AssessmentEvidence.java` and `application/domain/service/InfluencerAnalysisReportService.java` to add a domain-owned evidence excerpt capped at 500 characters.
- Update related application ports and tests.

**Behavior:**

- Allocate `runId` before validation/storage.
- Blank/oversize: `CREATED → REJECTED`, no Embabel invocation.
- Valid request: `CREATED → RUNNING`, synchronous Embabel invocation outside DB transaction.
- Outcome `COMPLETED`: store report snapshot and mark run `COMPLETED`.
- Construct the R10 evidence excerpt deterministically from the source post, cap it at 500 characters, and preserve the existing post ID/URL/rationale provenance. The persisted report never contains the full post solely for tracing.
- Outcome `REFUSED` or `INCOMPLETE`: mark business run `REJECTED`, even when Embabel process technically completed.
- Invocation failure: safe classification and `FAILED`; unwrap async wrapper only enough to classify, never expose raw provider payload.
- Any recorder write failure changes only trace health/warning.

**Tests:**

- Extend `EmbabelInfluencerAnalysisServiceTest` for successful report, blank/oversize deterministic rejection, semantic rejection with Embabel trace, agent failure, create/event/finalize storage failures and report snapshot failure.
- Assert `runId` is returned in every path and original analysis status remains authoritative when recorder fails.
- Assert evidence excerpt exact-boundary/truncation behavior and reject oversized or unbounded report fields before persistence.
- Assert no transaction is active while the fake Embabel/provider boundary is executing.

**Traces:** R1-R11, R16; AE1-AE4.

### U5. CLI run discovery and detail

**Goal:** Expose the run contract without coupling CLI rendering to Application.

**Files:**

- Modify `adapter/in/cli/PinbabelShellCommands.java` to use a CLI renderer and show run fields.
- Add `adapter/in/cli/PinbabelRunShellCommands.java` for `pinbabel-runs` and `pinbabel-run --id`.
- Add `adapter/in/cli/PinbabelCliRenderer.java`.
- Move `application/service/chat/PinbabelChatbot.java` to `adapter/in/cli/chat/PinbabelChatbot.java` and reuse the CLI/text renderer.
- Update CLI integration and package architecture tests.

**Behavior:**

- Preserve existing visible status/message/report/disclaimer content and add `runId`, `traceAvailable`, warnings.
- `pinbabel-runs` has no paging flags in this increment and always shows latest 20.
- `pinbabel-run --id` validates the lexical UUID, shows safe run/report fields and events by ascending sequence.
- Missing/malformed IDs and storage outage return safe Korean messages with no stack trace or cross-run data.

**Tests:**

- Extend `PinbabelShellCommandsTest` for additive output and trace warning.
- Add `PinbabelRunShellCommandsTest` for latest 20, deterministic tie order, completed/rejected/failed details, event order, missing/malformed ID and query outage.
- Extend `CliProfileIntegrationTest` to verify all three commands are registered and chatbot still delegates to the same use case.
- Extend `InfluencerAnalysisPackageArchitectureTest` to enforce chatbot transport ownership and adapter/application dependency direction.

**Traces:** R8, R10-R14, R16; AE1-AE6.

### U6. Cross-layer verification and operator documentation

**Goal:** Prove safety, compatibility and future adapter reuse before declaring the increment complete.

**Files:**

- Add/update focused acceptance fixtures under `src/test/resources` without credentials or raw production content.
- Update `README.md` or existing CLI run guide with command examples, in-memory lifetime, safe metadata policy and non-advisory disclaimer.
- Update architecture/compliance tests where new annotations/package rules require explicit coverage.

**Behavior and evidence:**

- Exercise AE1-AE7 end to end with scripted/test Embabel behavior.
- Verify environment-based provider configuration remains unchanged and secrets never enter DB/log assertions.
- Capture final dependency resolution for Embabel 1.5.0, Boot 4.1.0 and Boot-managed Liquibase.
- Record that REST/A2A/A2UI will reuse the inbound query/analysis ports in later increments.

**Traces:** R1-R16; AE1-AE7.

## 10. Requirement traceability

| Requirement | Implementation units | Primary verification |
| --- | --- | --- |
| R1 | U1, U4 | every-path runId service tests |
| R2 | U1, U2, U4 | lifecycle + persistence integration |
| R3 | U1, U2, U4 | report/failure summary round trip |
| R4 refined | U1, U4 | deterministic vs semantic rejection tests |
| R5 | U3, U4 | real Embabel event integration |
| R6 | U2, U3 | allowlist mapper and nullable metrics |
| R7 | U2, U3, U6 | adversarial secret/raw content absence |
| R8 | U1, U4, U5 | CLI output and storage-failure tests |
| R9 | U3, U4 | listener/store failure isolation |
| R10 | U2, U4, U5 | versioned report snapshot/detail rendering |
| R11 | U5 | existing command compatibility test |
| R12 | U1, U2, U5 | deterministic latest-20 test |
| R13 | U1, U2, U5 | detail/status/event ordering test |
| R14 | U1, U5 | missing/malformed ID test |
| R15 | U2, U6 | in-memory datasource/restart acceptance |
| R16 | U1-U5 | architecture tests and adapter-only types |

All requirements and AE1-AE7 have at least one implementation unit and explicit test target.

## 11. Change-signal routing

| Change signal | Route | Required? | Evidence |
| --- | --- | --- | --- |
| Java code | baseline Java/backend standards | Required | U1-U5, compilation/style tests |
| Application/Domain | Hexagonal + DDD + jMolecules | Required | U1 domain tests, architecture tests |
| Slice/module | Modulith boundaries | Required | U1/U5 package architecture test |
| Transaction | transaction discipline | Required | U2/U4 short-tx and no-network-in-tx tests |
| External I/O | outbound-port/adapter boundary | Required | U3 Embabel adapter, U4 invocation boundary |
| Persistence | JPA mapping/error translation | Required | U2 persistence integration tests |
| Schema | Liquibase | Required | U2 changelog validators |
| Search/list | deterministic bounded collection | Required | latest 20 + tie-breaker tests; no general RSQL |
| Projection/read model | mapping boundary | Required | U1/U2 application read models, no entity leakage |
| Exception/catch | error handling | Required | U2-U5 unique safe classifications |
| Internal Code | registry validation | Required | U2/U3/U5 registry additions |
| Dependency/build | managed dependency + resolution | Required | U2/U6 dependency evidence |
| Structural move | scoped package correction | Required | U5 chatbot move + architecture regression |
| Skill/compliance asset change | skill-maintenance workflow | N/A | no skill-owned asset modified |
| HTTP/API/Swagger | REST contract standards | N/A | REST deferred; CLI only |
| Auth/identity | auth standards | N/A | no authenticated boundary added |
| Async/published event | durable event rules | N/A | listener callback is synchronous observation, not published business event |
| External URL/file retrieval | SSRF/file safety | N/A | no new fetcher/file input |
| Saga | Saga/compensation | N/A | one mutable resource, no remote mutation |

## 12. Compatibility and system-wide impact

### Public contract

- Existing `pinbabel` command remains synchronous.
- Existing status/message/report/disclaimer content remains visible; run fields are additive. The requirements explicitly approve this CLI contract addition.
- Two new read-only commands are added. No existing command or option is renamed.
- No REST, A2A, A2UI or published event contract changes in this increment.

### Data and operations

- Schema change is additive and limited to new in-memory tables.
- H2 contents disappear on restart by design; CLI must document this clearly.
- Event count is bounded per run to prevent memory/DB growth from unbounded agent loops.
- Report snapshot contains only the already-approved, 500-character-bounded evidence excerpt needed for provenance. Report collection/string bounds are validated before serialization; event storage remains metadata-only.
- 최초 CLI는 애플리케이션을 실행할 수 있는 동일 OS 사용자만 접근하는 local trust boundary를 사용한다. 향후 REST/A2A/A2UI에서 run 조회를 노출할 때는 tenant/owner authorization을 별도 계약으로 추가해야 하며, 현재 로컬 경계를 네트워크 API의 인가로 간주하지 않는다.

### Failure propagation

- Analysis failure propagates as the existing safe failed result and terminal run status when recordable.
- Recorder failure does not propagate into the agent; it degrades trace availability.
- Query failure remains inside the CLI query path and cannot reveal other runs or persistence details.
- A process crash may lose or leave a nonterminal in-memory run; crash recovery is explicitly outside R15.

### Focused threat model

| Threat | Planned control |
| --- | --- |
| SNS/prompt injection text appears in an Embabel event and is persisted | explicit scalar allowlist; no generic serialization/`toString`; adversarial content tests |
| API key, provider response or stack trace leaks through failure handling | environment-only secret source; safe failure classification; Internal Code logging with no raw cause/payload |
| Agent loop or oversized structured report exhausts in-memory H2 | per-run event cap, post/report collection bounds, 500-character evidence excerpt, persistence serialization guard |

## 13. Verification matrix

| Standard row | Required? | Test/task evidence |
| --- | --- | --- |
| Always-on | Required | focused unit/integration tests; `backendArchitectureTest`; final clean build |
| API/DTO/Mapping | Required | application-result/CLI mapping tests; no protocol/JPA type leakage |
| RSQL/Pagination | Required (sorting/limit subset) | fixed limit 20, `createdAt + runId` deterministic sort; filter/page selectors N/A |
| Persistence/Transaction | Required | JPA round trip, flush failure classification, no active tx across Embabel call |
| Liquibase | Required | `validateLiquibaseChangeSets`, empty-H2 startup, rollback/constraint evidence |
| Cross-Slice | N/A | one Business Slice and one DataSource |
| Durable Event | N/A | no application/domain event publication |
| Saga | N/A | no independently committed remote mutation |
| Identity/Auth | N/A | local CLI only |
| Application Security | Required | adversarial raw-data/secret exclusion, event cap, safe errors, dependency scan evidence |
| External API | Required | scripted Embabel/provider success, failure, missing metrics and timeout-classification tests |
| Dependency/Build | Required | dependency insight/resolution plus `./gradlew clean build --rerun-tasks --no-build-cache` |

The repository currently has no detected dependency vulnerability scan task. This row cannot be marked N/A: implementation handoff must run an organization-approved scanner if available or report the security verification as blocked/unverified rather than inventing a pass. Adding a new scanner plugin or repository requires separate approval if it changes build policy.

## 14. Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Embabel event API changes within 1.5.x | mapper compile/runtime drift | pin 1.5.0; explicit subtype mapper; unknown-event ignore test |
| An event object contains raw data | privacy/secret leakage | allowlist scalar extraction; prohibit `toString`; adversarial DB/log assertions |
| Listener failure changes analysis | incorrect business outcome | per-run best-effort session; listener never throws; failure-injection tests |
| Per-event DB writes add latency | slower CLI execution | short H2 transactions, bounded event count; measure latency in integration test before considering batching |
| Terminal process event says completed for refusal | wrong run status | outer service owns business terminal status from `InfluencerAnalysisOutcome` |
| Final report schema evolves | unreadable history | `report_schema_version`; mapper compatibility test; in-memory scope limits initial risk |
| Concurrent callbacks reorder events | misleading trace | atomic sequence per run; query by sequence |
| H2 restarts erase history | user confusion | CLI/README process-lifetime notice; no durability claim |
| DB recovers after event failure | final outcome absent | stop repeated event writes but attempt one final run/report write marked incomplete |

## 15. Approval gates

No blocking approval gate is identified for the planned implementation:

- The schema is additive and rollback only removes process-lifetime H2 test data.
- The new dependency is an official Spring Boot 4.1.0 managed starter from the existing repository policy.
- The CLI additions and semantic-vs-deterministic rejection refinement were explicitly accepted during requirements/planning.
- No external system mutation, Saga, public HTTP contract, new repository, authentication policy or secret-handling change is introduced.

Stop and request approval if implementation discovers that Embabel 1.5.0 requires storing raw prompt/tool data, a new artifact repository, a breaking CLI change, an external persistent DB, or a new security scanner/plugin to satisfy policy.

## 16. Implementation order

1. U1 domain/application contracts
2. U2 schema and persistence adapter
3. U3 Embabel recorder
4. U4 use-case lifecycle integration
5. U5 CLI discovery/detail and transport move
6. U6 cross-layer verification/documentation

U3 can be unit-tested against a fake trace store after U1, but U4 should not integrate it until U2 persistence failure behavior is proven. U5 depends on the stable U1 read model and U4 result contract.

## 17. References

### Internal

- `docs/brainstorms/2026-08-24-analysis-run-flight-recorder-requirements.md`
- `docs/ideation/2026-08-24-pinbabel-next-development-ideation.md`
- `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/EmbabelInfluencerAnalysisService.java`
- `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/agent/InfluencerAnalysisAgent.java`
- `src/main/java/com/ypkim/pinbabel/influenceranalysis/adapter/in/cli/PinbabelShellCommands.java`
- `src/test/java/com/ypkim/pinbabel/influenceranalysis/application/service/agent/InfluencerAnalysisAgentIntegrationTest.java`
- `gradle/backend-compliance.gradle.kts`
- `config/internal-code/registry.json`

### Official external

- [Embabel Agent Process](https://hub.embabel.com/reference/agent-process)
- [Embabel Agent GitHub](https://github.com/embabel/embabel-agent)
- [Spring Boot managed dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [Spring Boot Liquibase auto-configuration API](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/liquibase/autoconfigure/package-summary.html)

## 18. Definition of done

- R1-R16 (including the approved R4 refinement) and AE1-AE7 are automated or explicitly evidenced.
- All new domain/port/adapter types follow the existing Hexagonal, DDD, Modulith and jMolecules rules.
- Liquibase is the only schema creator and every changeSet author is `ypkim`.
- Saved events and logs contain no prompt/response/tool payload/API key/credential.
- `pinbabel`, `pinbabel-runs`, `pinbabel-run --id` work under the CLI profile.
- Recorder/event-cap failures preserve analysis semantics and expose `traceAvailable=false`.
- Required compliance tasks and final clean build pass.
- Required security/dependency scan is either evidenced or explicitly reported as unavailable; it is never silently marked passed.
