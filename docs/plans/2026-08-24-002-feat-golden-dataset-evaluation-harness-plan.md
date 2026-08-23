---
title: "feat: Add golden dataset evaluation harness"
type: feat
status: completed
date: 2026-08-24
origin: docs/ideation/2026-08-24-pinbabel-next-development-ideation.md
---

# feat: Add golden dataset evaluation harness

## 1. Outcome

버전이 명시된 Golden Dataset을 기준으로 실제 Embabel 분석 결과의 종목 탐지, sentiment, 근거 post ID를 자동 채점한다. `pinbabel-evaluate`로 전체 dataset을 실행하고, H2에 평가 실행과 case 결과를 저장하며, `pinbabel-evaluations`와 `pinbabel-evaluation --id`로 재현 가능한 결과를 조회한다.

평가 계산기는 LLM과 Spring 없이 단위 테스트할 수 있어야 한다. 실제 평가 실행만 기존 `AnalyzeInfluencerPostsUseCase`를 호출하여 Embabel 실행 경로와 `AnalysisRun` trace를 그대로 사용한다.

## 2. Scope

### In scope

- classpath JSON Golden Dataset v1과 크기 제한/중복 검증
- expected instrument/sentiment/evidence post ID 계약
- 종목 precision/recall/F1, sentiment accuracy, evidence recall, exact match 계산
- dataset 전체 평가 실행과 case별 `analysisRunId` 연결
- H2/Liquibase 단일 aggregate snapshot 저장
- 최신 20건/상세 CLI 조회
- Domain, loader, persistence, CLI, architecture 테스트

### Out of scope

- REST, A2A, A2UI (평가 Harness는 개발자용 내부 품질 도구이며 외부 사용자 기능이 아님)
- 모델/프롬프트 자동 matrix 실행과 통계적 비교
- dataset 편집·업로드 API, 외부 저장소, 운영 DB 보존
- 비동기 실행, 취소/재개, 병렬 LLM 호출
- 실제 SNS 수집 및 golden label 생성 UI

## 3. Change-signal routing

| Signal | Decision | Applied rule |
| --- | --- | --- |
| Java backend | Required | modern Java, readability, unit/negative tests |
| Domain/Application | Required | DDD aggregate/value objects and pure scoring service |
| Hexagonal/Mapping | Required | classpath/JPA/CLI types stay in adapters; project-owned ports/resources |
| Slice/Modulith | Required | remain inside `influenceranalysis`; no cross-slice dependency |
| Persistence/DB | Required | short local transactions, adapter-specific mapping and failure codes |
| Schema/Liquibase | Required | additive table, `author: ypkim`, H2 rollback |
| Search/list | Required | fixed latest-20 deterministic ordering; no raw query language/pagination |
| External I/O | Required | existing Embabel analysis is read/computation only; no Saga or compensation |
| Security/high-cost input | Required | trusted classpath dataset only, bounded case/instruction/expectation counts, no prompt/result payload persistence beyond existing safe analysis report |
| HTTP/API | N/A | no network contract in this internal CLI increment |
| Auth/async/event | N/A | no new protected or durable async boundary |
| Dependency change | N/A | Jackson/JPA/Liquibase already available through existing starters |

## 4. Architecture and dependency direction

```text
CLI adapter -> Evaluate/Query inbound ports -> Evaluation application service
                                               |-> GoldenDatasetSource outbound port <- classpath JSON adapter
                                               |-> AnalyzeInfluencerPostsUseCase (existing Embabel path)
                                               `-> EvaluationRunStore outbound port <- JPA/H2 adapter

Pure domain: GoldenDataset + EvaluationRun + EvaluationScore + GoldenDatasetEvaluator
```

- `EvaluationRun` is a technical run/correlation aggregate and uses a UUID string Value Object, matching `AnalysisRun`.
- `EvaluationRun` persists as one row with a versioned case-results JSON snapshot. The child results have no independent lifecycle or repository.
- One evaluation case invokes one analysis synchronously; no database transaction spans an Embabel/network call.
- A failed/rejected analysis becomes a failed case result and the remaining cases continue. Dataset load or evaluation-store failure terminates the CLI request with a safe message.

## 5. Contracts and scoring

Golden Dataset v1:

- `datasetId`, `version`, non-empty unique `cases`
- case: `caseId`, `instruction`, expected instruments
- expected instrument: `instrumentId`, `sentiment`, non-empty unique `evidencePostIds`
- initial resource contains at most 20 cases, 50 expected instruments per case, and instruction length no greater than the existing analysis input limit.

Scoring rules:

- instrument identity is exact `instrumentId` equality.
- detection TP is an expected instrument present in actual summaries; missing expected is FN; unexpected actual is FP.
- a wrong sentiment remains a detected instrument but fails sentiment accuracy and exact match.
- evidence recall is expected evidence IDs found in the matching actual instrument summary divided by all expected evidence IDs.
- aggregate counts are micro-averaged across cases; ratios are zero when their denominator is zero.
- exact match requires no missing/unexpected instrument, all sentiments equal, and all expected evidence present.
- failed/rejected/null-report analysis produces a failed case with zero scores and records the underlying `analysisRunId` when available.

## 6. Data and compatibility

Additive `evaluation_run` table:

- `evaluation_run_id` varchar(36) PK
- dataset id/version, status, created/started/completed timestamps, duration
- case totals and aggregate TP/FP/FN/correct sentiment/evidence counts
- ratio columns and exact-match case count
- `result_schema_version`, `case_results_json` CLOB
- recent index `(created_at desc, evaluation_run_id desc)`

No existing table or public contract changes. Rollback drops only the new in-memory evaluation table. Snapshot schema version is checked before deserialization.

## 7. Error and Internal Code policy

Allocate distinct `PIN-IAN-` codes for dataset read, result serialization, unsupported schema, result deserialization, evaluation save, and evaluation query boundaries. Preserve causes, never log dataset instructions, prompts, model responses, credentials, or persistence details. Domain dataset invariants use occurrence-specific codes where validation is introduced.

## 8. Implementation units

### U1. Golden Dataset and deterministic evaluator

- Add evaluation domain model and pure `GoldenDatasetEvaluator`.
- Test exact match, missing/unexpected instruments, wrong sentiment, partial evidence, empty actual report, duplicate/invalid dataset entries.

### U2. Dataset source and evaluation use case

- Add outbound dataset port and bounded classpath JSON adapter.
- Add evaluate inbound port/resources and sequential application service.
- Test continuation after a failed case and `analysisRunId` correlation.

### U3. Persistence and query

- Add evaluation store port, JPA entity/repository/codec/adapter and Liquibase changeSet.
- Add query inbound port/resources/service.
- Test round trip, latest ordering/limit, unsupported snapshot schema and safe persistence classification.

### U4. CLI and verification

- Add `pinbabel-evaluate`, `pinbabel-evaluations`, `pinbabel-evaluation --id` and renderer output.
- Update CLI profile integration and command tests.
- Run focused tests, architecture/Internal Code/Liquibase compliance, then `./gradlew clean build --rerun-tasks --no-build-cache`.

## 9. Verification matrix

| Area | Required verification |
| --- | --- |
| Domain | pure scoring unit + invariant negative tests |
| Application | fake dataset/analyzer/store test including failed case continuation |
| Adapter | fixture loader and JPA round-trip integration tests |
| CLI | command and profile context tests |
| Architecture | existing jMolecules, Hexagonal, Modulith, full-mapping tests |
| Schema | Liquibase compliance and application context migration |
| Internal Code | generated declaration/occurrence inventory and registry validator |
| Security | bounded fixture tests; dependency vulnerability scan unchanged and separately reported if unavailable |
| Final | clean no-cache Gradle build |

## 10. Approval gates

This increment is additive, uses the already approved H2 and direct-main workflow, adds no dependency/repository/outbound destination, and changes no public API or source of truth. No additional approval gate is triggered. Commit and push remain excluded until explicitly requested.
