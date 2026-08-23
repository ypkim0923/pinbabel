---
title: Embabel 실행 추적 listener 실패를 Agent 실행에서 격리하기
date: 2026-08-24
category: integration-issues
module: influenceranalysis
problem_type: integration_issue
component: assistant
symptoms:
  - Embabel event metadata가 예상과 다르면 실행 추적 mapper 예외가 전역 listener 밖으로 전파될 수 있음
  - 관측 기능의 실패가 원래 Agent 분석의 성공 또는 실패 의미를 바꿀 수 있음
root_cause: missing_validation
resolution_type: code_fix
severity: high
tags: [embabel, agentic-event-listener, observability, failure-isolation, hexagonal-architecture]
---

# Embabel 실행 추적 listener 실패를 Agent 실행에서 격리하기

## Problem

Embabel `AgenticEventListener`에서 안전한 실행 metadata를 추출해 저장할 때 event가 항상 완전하다고 가정하면, null metadata나 새 event 형태 때문에 발생한 mapper 예외가 Agent 실행 경로로 역전파될 수 있다. 실행 추적은 관측 보조 기능이므로 그 실패가 분석 결과를 바꾸면 안 된다.

## Symptoms

- `LlmInvocationEvent`의 invocation 또는 model metadata가 예상과 다를 때 recorder callback에서 런타임 예외가 발생한다.
- 저장소 오류는 격리했더라도 event routing이나 mapping 오류는 격리되지 않아 분석 호출 자체가 실패할 여지가 있다.

## What Didn't Work

- event 저장 `append`만 `try/catch`로 감싸는 방식은 DB 오류만 막는다. 그 전에 실행되는 process ID 추출, metrics 계산과 allowlist mapping도 예외를 낼 수 있다.
- Application outbound port가 `AgenticEventListener`를 직접 상속하게 하면 Embabel callback 타입이 port 계약으로 새어 나가고, 실제 global listener routing 책임이 불분명해진다.

## Solution

Application port는 recorder 상태 조회와 종료만 표현하고 Embabel event 타입을 제거한다.

```java
public interface AnalysisRunFlightRecorder extends AutoCloseable {
    String LISTENER_WARNING = "TRACE_LISTENER_FAILED";

    boolean traceAvailable();
    String warningCode();
    String processId();
    AnalysisRunMetrics metrics();
}
```

Embabel adapter 내부 session이 callback 전체를 보호하고, 오류가 나면 이후 event 수집을 중단하면서 trace만 불완전 상태로 바꾼다.

```java
void onProcessEvent(AgentProcessEvent event) {
    try {
        recordEvent(event);
    }
    catch (RuntimeException exception) {
        degrade(LISTENER_WARNING);
        log.warn("Analysis trace event mapping failed: runId={}", runId.value());
    }
}
```

전역 router도 context ID 추출 경계를 별도로 보호한다. 로그에는 run ID와 안전한 분류만 남기며 event 객체, raw exception, prompt 또는 tool payload를 기록하지 않는다.

마지막으로 malformed Embabel event를 주입해 callback이 예외를 던지지 않고 `traceAvailable=false`, `TRACE_LISTENER_FAILED`를 반환하는 회귀 테스트를 둔다.

## Why This Works

Embabel callback 타입과 실패 처리가 outbound adapter 안에 머무르므로 Application 계층은 framework-neutral recorder 계약만 사용한다. 관측 경계에서 발생한 예외를 adapter가 흡수하고 trace health를 업무 상태와 독립적으로 관리하므로, 기록 실패는 사용자에게 경고되지만 Agent 분석 결과를 덮어쓰지 않는다.

Spring 환경에서 Embabel은 모든 `AgenticEventListener` bean을 platform listener로 합성한다. 실행마다 `contextId=runId`를 지정하고 global listener가 해당 session으로 routing하면 planning과 terminal event까지 같은 run에 연결하면서 session 종료 시 routing map에서도 제거할 수 있다.

## Prevention

- listener callback 전체를 실패 격리 경계로 취급하고 저장 호출만 보호하는 데 그치지 않는다.
- 알려진 event subtype과 scalar metadata만 allowlist로 변환하며 unknown event는 기본적으로 무시한다.
- raw event의 `toString()`, prompt, response, tool input/output과 exception message를 DB나 로그에 남기지 않는다.
- malformed event, 저장 실패, event cap을 각각 주입하고 모두 원래 업무 결과를 변경하지 않는지 테스트한다.
- Application port에는 Embabel event, JPA entity 또는 provider SDK 타입을 노출하지 않는다.

## Related Issues

- [Embabel GOAP 분기 조건을 Action 후조건으로 연결하기](./embabel-goap-branch-conditions-require-postconditions-2026-08-20.md)
