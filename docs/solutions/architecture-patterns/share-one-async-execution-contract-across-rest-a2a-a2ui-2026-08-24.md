---
title: REST·A2A·A2UI가 하나의 비동기 실행 계약을 공유하기
date: 2026-08-24
last_updated: 2026-08-24
category: architecture-patterns
module: influenceranalysis
problem_type: architecture_pattern
component: assistant
severity: high
applies_when:
  - 하나의 장기 실행 Agent 유스케이스를 REST와 Agent 프로토콜과 UI 프로토콜로 함께 제공할 때
  - protocol별 task와 message 상태를 동일한 업무 실행에 연결해야 할 때
  - Embabel 실행 trace와 외부 요청을 end-to-end로 추적해야 할 때
tags: [embabel, a2a, a2ui, async-operation, correlation-id, hexagonal-architecture]
---

# REST·A2A·A2UI가 하나의 비동기 실행 계약을 공유하기

## Context

주식 인플루언서 분석은 LLM planning과 Tool 호출을 포함하므로 HTTP 요청 안에서 항상 끝난다고 가정할 수 없다. REST, A2A, A2UI가 각각 실행 로직과 상태를 소유하면 validation, 안전 정책, 결과와 provenance가 프로토콜마다 달라지고 같은 분석이 세 번 구현된다.

기존 동기 CLI는 개발과 Golden Dataset 평가에 유용하지만, A2A Task 수명주기와 향후 UI 진행 표시를 받치려면 먼저 추적 가능한 공통 비동기 operation 계약이 필요했다.

## Guidance

프로토콜보다 안쪽에 하나의 Application 실행 계약을 둔다.

1. `SubmitInfluencerAnalysisUseCase`가 비동기 접수를 소유한다.
2. 서버가 `AnalysisRunId`와 별도의 `AnalysisCorrelationId`를 발급한다.
3. `CREATED` 상태를 저장한 뒤에만 `AnalysisExecutionLauncher`에 작업을 전달한다.
4. 실행 상태는 `CREATED`, `RUNNING`, `COMPLETED`, `FAILED`, `REJECTED`로 관리한다.
5. REST, A2A, A2UI Adapter는 이 상태를 각 protocol 상태로 변환할 뿐 별도 상태 머신을 만들지 않는다.
6. Domain `AnalysisTraceEvent`는 `AnalysisProgressEventResource`로 변환한 뒤 Adapter에 전달한다.
7. protocol 자체 task, context, message, surface DTO는 각 Inbound Adapter에 격리한다.
8. 조회 결과도 Domain report/metrics를 직접 노출하지 않고 Application-owned read model로 변환한 뒤 각 Adapter DTO로 다시 매핑한다.

### Protocol version은 framework 호환선에 맞춘다

Embabel Agent `1.5.0`의 공식 `embabel-agent-a2a` module은 A2A Java SDK `0.3.2.Final`과 protocol `0.3.0`을 사용한다. 따라서 A2A 1.0 wire contract를 임의로 섞지 않고 Agent Card에 `protocolVersion: 0.3.0`을 명시한다. Embabel의 기본 Autonomy handler가 공통 Application Port를 우회한다면 공식 endpoint registrar와 SDK type만 재사용하고 유스케이스 전용 `AgentCardHandler`를 구현한다.

A2UI는 `v0.9` envelope를 사용한다. HTTP transport는 `application/x-ndjson`이며 다음 순서를 지킨다.

```text
createSurface -> updateComponents(root 포함) -> updateDataModel
```

`createSurface.catalogId`는 Basic Catalog URL을 명시하고, `updateDataModel.path`는 `/`에서 시작하는 JSON Pointer다. A2UI가 transport를 강제하지 않으므로 이번 HTTP endpoint는 지속 연결 streaming이 아니라 현재 상태 snapshot임을 계약에 기록한다.

공통 호출 방향은 다음과 같다.

```text
REST / A2A / A2UI adapter
          |
          v
SubmitInfluencerAnalysisUseCase
          |
          +--> AnalysisRunStore (CREATED를 먼저 저장)
          |
          `--> AnalysisExecutionLauncher (bounded worker + bounded queue)
                         |
                         v
                 Embabel Agent 실행
```

`AnalysisExecutionLauncher`는 Executor 구현을 Application에서 숨기는 Outbound Port다. fixture Adapter는 고정 worker와 유한 queue를 사용하고, 포화 시 무제한 대기나 thread 생성을 하지 않고 `false`를 반환한다. Application Service는 해당 실행을 `REJECTED`와 `EXECUTION_CAPACITY_EXCEEDED`로 영속화한다.

```java
var accepted = AnalysisSubmissionResource.from(run);
if (!executionLauncher.launch(run.id(), () -> execute(run, instruction))) {
    run.reject(now(), "EXECUTION_CAPACITY_EXCEEDED", "Analysis execution capacity is exhausted");
    runStore.save(run, null);
    return AnalysisSubmissionResource.from(run);
}
return accepted;
```

상태 매핑은 Adapter 계약 테스트에서 다음 의미를 보존한다.

| Pinbabel | REST operation | A2A Task | A2UI data model |
| --- | --- | --- | --- |
| `CREATED` | accepted/pending | `SUBMITTED` | pending |
| `RUNNING` | running | `WORKING` | running |
| `COMPLETED` | terminal result | `COMPLETED` | result |
| `FAILED` | terminal failure | `FAILED` | safe failure |
| `REJECTED` | validation/capacity rejection | `REJECTED` | safe rejection |

`runId`는 Pinbabel 실행 Aggregate의 식별자이고 `correlationId`는 프로토콜 횡단 추적 식별자다. Pinbabel A2A Adapter는 `runId -> taskId`, `correlationId -> contextId`를 명시적으로 매핑하고 A2UI surface ID는 `pinbabel-analysis-{runId}`로 파생한다. 이 매핑을 Adapter가 소유해야 protocol version 변경이 Domain에 전파되지 않는다.

## Why This Matters

- validation과 Embabel orchestration이 한 서비스에 남아 protocol별 동작 차이를 막는다.
- 접수 응답 전에 실행을 저장하므로 클라이언트가 받은 `runId`가 조회되지 않는 race를 피한다.
- bounded queue와 명시적 포화 상태가 자원 고갈을 통제한다.
- correlation ID를 영속화하므로 REST 요청, A2A Task, A2UI interaction과 Embabel run을 연결할 수 있다.
- Domain event와 protocol DTO를 분리해 A2A/A2UI spec 변경을 Adapter 교체로 제한한다.

현재 H2와 in-process executor는 학습용 실행 경계다. 프로세스 재시작 뒤 작업 복구, 취소와 재개, 외부 입력 대기, multi-instance claim은 제공하지 않는다. 운영 전환 시 durable queue/worker와 idempotency 계약을 별도 설계해야 한다.

## When to Apply

- LLM/Tool 실행 시간이 일반 HTTP timeout을 넘을 수 있는 유스케이스
- 같은 Agent capability를 REST, A2A, A2UI에 동등하게 노출하는 기능
- 사용자에게 진행 상태, 최종 결과, 안전한 실패와 trace를 제공해야 하는 작업
- 프로토콜 SDK나 wire version을 Domain/Application에서 격리해야 하는 시스템

## Examples

공개 Adapter를 추가할 때 다음 순서로 매핑한다.

```text
REST Request -> SubmitInfluencerAnalysisCommand -> AnalysisSubmissionResource -> REST Response
A2A Message  -> SubmitInfluencerAnalysisCommand -> AnalysisSubmissionResource -> A2A Task
A2UI Request -> SubmitInfluencerAnalysisCommand -> AnalysisSubmissionResource -> A2UI messages
```

각 Adapter의 request/response/task/message 타입을 Application command/resource로 재사용하지 않는다. 정상, validation 거절, capacity 거절, 실행 실패, timeout과 조회 누락을 세 프로토콜 contract test에서 같은 의미로 검증한다.

HTTP body는 JSON binding 전에 유한한 byte budget으로 읽고, 업무 instruction에는 별도의 문자 수 제한을 둔다. 로컬 실험 API라 인증을 생략하는 경우에도 별도 `api` profile과 loopback bind로 외부 노출을 차단하고, 운영 공개는 인증·인가·tenant·rate limit 설계 후에만 진행한다.

## Related

- [Golden Dataset 채점과 Embabel Agent 실행을 분리하기](./separate-golden-dataset-scoring-from-agent-execution-2026-08-24.md)
- [Embabel listener 실패를 Agent 실행에서 격리하기](../integration-issues/isolate-embabel-listener-failures-from-agent-execution-2026-08-24.md)
- [공통 실행 계약 구현 계획](../../plans/2026-08-24-003-feat-common-analysis-execution-contract-plan.md)
- [REST·A2A·A2UI Adapter 구현 계획](../../plans/2026-08-24-004-feat-rest-a2a-a2ui-adapters-plan.md)
