# REST·A2A·A2UI 공통 분석 실행 계약 구현 계획

## 상태

- 상태: Completed
- 작성일: 2026-08-24
- 범위: `influenceranalysis` Slice의 공통 비동기 실행·조회 계약

## 목표

REST, A2A, A2UI Inbound Adapter가 서로 다른 분석 구현을 만들지 않고 하나의 Application Use Case를 호출하도록 공통 실행 계약을 만든다.

- 서버가 `runId`와 `correlationId`를 발급한다.
- 유효한 요청은 추적 가능한 실행 Resource로 먼저 저장한 뒤 bounded executor에 접수한다.
- 상태와 진행 이벤트는 protocol-neutral Application Resource로 조회한다.
- 기존 CLI와 Golden Dataset용 동기 분석 포트는 호환성을 위해 유지한다.

## 공식 규격 조사 결과

- A2A는 비동기 Task 수명주기와 `SUBMITTED`, `WORKING`, `COMPLETED`, `FAILED`, `REJECTED` 등의 상태를 제공한다.
- Embabel 1.5.0에는 A2A starter와 `/a2a` 지원 코드가 있으나 실제 Wire 계약은 A2A Adapter 단계에서 공식 source 기반 contract test로 확정한다.
- A2UI는 안정 버전 v0.9.1의 `createSurface`, `updateComponents`, `updateDataModel`, `deleteSurface` 메시지를 사용한다. 이번 단계에서는 A2UI 메시지를 Core에 넣지 않는다.

## 설계 결정

### 공통 수명주기

| Pinbabel 상태 | REST | A2A | A2UI v0.9.1 |
| --- | --- | --- | --- |
| `CREATED` | `202 Accepted` operation | `SUBMITTED` | data model에 pending 상태 반영 |
| `RUNNING` | operation 조회/stream | `WORKING` | data model에 running 상태 반영 |
| `COMPLETED` | terminal resource | `COMPLETED` + artifact | 결과 component/data model 갱신 |
| `FAILED` | terminal error state | `FAILED` | 실패 상태와 안전한 메시지 갱신 |
| `REJECTED` | validation/수용 거절 | `REJECTED` | 거절 상태와 안전한 메시지 갱신 |

- `runId`: Pinbabel이 발급하고 저장하는 분석 실행 식별자다.
- `correlationId`: 세 프로토콜 요청과 Embabel trace를 연결하는 서버 발급 기술 식별자다.
- protocol 자체 task/message/context ID는 각 Adapter가 소유하며 Core 식별자와 명시적으로 매핑한다.
- 취소, 재개, 외부 입력 대기, durable worker recovery는 이번 범위에서 지원하지 않는다.

### 실행 경계

- `SubmitInfluencerAnalysisUseCase`가 공통 비동기 접수 포트다.
- `AnalysisExecutionLauncher`는 Application이 executor 구현을 알지 않도록 하는 Outbound Port다.
- fixture 환경의 Adapter는 고정 worker 수와 유한 queue를 사용하고 포화 시 신규 실행을 거절한다.
- 접수 성공을 반환하기 전에 `CREATED` 실행을 저장한다. 저장 실패를 성공 접수로 숨기지 않는다.
- 실행 중 trace 저장 실패는 기존 정책대로 분석 자체를 실패시키지 않고 warning으로 격리한다.

### 경계 매핑

- Domain `AnalysisTraceEvent`를 Inbound Adapter에 노출하지 않고 `AnalysisProgressEventResource`로 변환한다.
- REST Request/Response, A2A DTO, A2UI message는 후속 Adapter 작업에서 각각 별도 타입으로 만든다.
- 현재 `InfluencerAnalysisReport`와 `AnalysisRunMetrics`가 Application Resource에 직접 포함된 부분은 기존 부채다. 공개 Adapter 구현 전에 별도 Port Resource로 완전 매핑한다.

## 변경 신호

| 신호 | 판정 | 대응 |
| --- | --- | --- |
| 공개 REST API | N/A | 아직 Controller/OAS를 추가하지 않음 |
| A2A/A2UI 공개 계약 | N/A | protocol DTO/endpoint는 후속 작업 |
| 비동기 operation | Required | 추적 Resource, 상태, 포화 동작 정의 |
| DB schema | Required | `correlation_id` 추가, Liquibase author `ypkim` |
| Queue/concurrency | Required | bounded executor와 saturation test |
| 인증/인가 | N/A | 외부 Endpoint가 아직 없음 |
| 외부 HTTP/SSRF | N/A | 신규 outbound destination 없음 |
| Dependency | N/A | 신규 dependency 없음 |
| Module boundary | Required | Port/Adapter 및 Modulith/ArchUnit 검증 |
| Internal Code | N/A | 새 예외 발생 위치 없이 명시적 REJECTED 상태 사용 |

## 구현 순서

1. `AnalysisCorrelationId` Value Object와 Aggregate 불변식을 추가한다.
2. 공통 submit Port, submission Resource, progress event Resource를 추가한다.
3. 기존 Embabel 실행 서비스를 동기/비동기 포트의 공통 실행 경로로 정리한다.
4. bounded execution Outbound Adapter를 추가한다.
5. correlation ID를 AnalysisRun persistence와 query Resource에 연결한다.
6. Liquibase changeSet과 계약/포화/상태 전이 테스트를 추가한다.

## 검증 행렬

| 검증 | 기대 결과 |
| --- | --- |
| Domain unit test | correlation 필수, 상태 전이 보존 |
| Service unit test | 저장 후 scheduling, validation 거절, queue 포화 거절 |
| Executor unit test | 유한 queue 포화 시 `false`, shutdown 안전성 |
| Persistence test | correlation ID 저장·조회 |
| Query service test | Domain event가 progress Resource로 매핑 |
| Architecture tests | Hexagonal, DDD, Modulith 경계 통과 |
| Liquibase/Internal Code checks | changeSet과 registry 검증 통과 |
| Final build | `./gradlew clean build --rerun-tasks --no-build-cache` 통과 |

## 후속 범위

- REST operation Controller와 OAS3 계약
- Embabel A2A starter 기반 Agent Card, task/message/artifact Adapter
- A2UI v0.9.1 JSONL surface와 진행 상태 Adapter
- 세 프로토콜 contract/equivalence test
- 인증·인가, tenant, rate limit, idempotency key
- 취소·재개와 durable worker/restart recovery
