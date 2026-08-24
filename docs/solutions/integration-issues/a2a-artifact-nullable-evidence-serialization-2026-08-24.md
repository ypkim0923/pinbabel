---
title: A2A artifact에서 nullable evidence 필드를 안전하게 직렬화하기
date: 2026-08-24
category: integration-issues
module: influenceranalysis
problem_type: integration_issue
component: assistant
severity: medium
symptoms:
  - A2A 분석 task가 완료된 뒤 결과 artifact를 구성할 때 internal error가 발생했다
  - UNCERTAIN evidence의 instrumentId 또는 ticker가 null이면 완료 결과를 반환하지 못했다
root_cause: logic_error
resolution_type: code_fix
related_components:
  - testing_framework
tags:
  - a2a
  - artifact-serialization
  - nullable-fields
  - embabel
  - protocol-adapter
---

# A2A artifact에서 nullable evidence 필드를 안전하게 직렬화하기

## Problem

실제 Gemini 분석은 정상 완료됐지만 A2A `tasks/get`으로 완료 task를 조회하면 `PinbabelA2AAgentCardHandler`가 report artifact를 생성하는 과정에서 JSON-RPC internal error를 반환했다. 종목을 식별하지 못한 evidence에는 `instrumentId`와 `ticker`가 합법적으로 없을 수 있는데, Adapter가 이를 필수 값처럼 immutable map에 넣고 있었다.

## Symptoms

- Embabel 실행 trace는 `PROCESS_COMPLETED`와 `GOAL_ACHIEVED`까지 기록됐다.
- REST에서는 같은 분석 결과를 정상 조회할 수 있었다.
- A2A `tasks/get`은 완료 보고서를 artifact로 변환할 때 `Pinbabel A2A request failed`를 반환했다.
- 종목 미식별 또는 prompt injection 문구처럼 `instrumentId`와 `ticker`가 `null`인 evidence가 있을 때 재현됐다.

## What Didn't Work

- 최초 A2A 호출에서 `message.kind`를 생략하거나 `tasks/get.params.historyLength`를 생략해 발생한 요청 역직렬화 오류는 A2A 0.3 클라이언트 요청 형식 문제였다. 올바른 요청으로 수정해도 완료 artifact 변환 오류는 별도로 남았다.
- 기존 구현의 `Map.entry("instrumentId", item.instrumentId())`와 `Map.entry("ticker", item.ticker())`는 값이 `null`이면 serializer에 도달하기 전에 예외를 발생시킨다. JSON serializer 설정으로 해결할 수 있는 문제가 아니다.

## Solution

`reportData`의 evidence 변환을 전용 helper로 분리하고, 선택적인 protocol 필드는 값이 있을 때만 포함했다.

```java
private Map<String, Object> evidenceData(EvidenceResource evidence) {
    var result = new LinkedHashMap<String, Object>();
    // required evidence fields
    result.put("postId", evidence.postId());
    result.put("sentiment", evidence.sentiment());

    if (evidence.instrumentId() != null) {
        result.put("instrumentId", evidence.instrumentId());
    }
    if (evidence.ticker() != null) {
        result.put("ticker", evidence.ticker());
    }
    return Map.copyOf(result);
}
```

회귀 테스트는 식별자가 모두 `null`인 `UNCERTAIN` evidence를 포함한 완료 run을 구성하고 다음 계약을 검증한다.

- A2A task 상태가 `COMPLETED`로 반환된다.
- report artifact와 `DataPart` 생성이 성공한다.
- `instrumentId`와 `ticker` 키는 evidence data에서 생략된다.

실제 `gemini-3.6-flash` 실행 후 A2A task가 `completed` 상태와 artifact를 반환하는 것을 확인했고, `./gradlew clean build --rerun-tasks --no-build-cache --no-daemon`도 통과했다.

## Why This Works

도메인의 `UNCERTAIN`은 실패가 아니라 정상 상태이며 canonical instrument를 찾지 못하면 두 식별자가 없을 수 있다. Adapter에서 이 선택성을 A2A data contract의 키 생략으로 표현하면 도메인 의미를 보존하면서 `Map.entry`, `Map.of`, `Map.copyOf`의 null 금지 제약도 지킬 수 있다.

## Prevention

- nullable domain 값을 `Map.entry`, `Map.of`, `Map.ofEntries`에 직접 전달하지 않는다.
- protocol Adapter는 optional 필드를 조건부로 포함하는 명시적인 mapper를 사용한다.
- 완료 결과 contract test에 미식별 종목과 nullable 식별자를 포함한다.
- 제출 성공뿐 아니라 A2A `tasks/get`의 최종 artifact 직렬화까지 검증한다.
- JSON-RPC 요청 형식 오류와 서버 내부 결과 변환 오류를 분리해 진단한다.

## Related Issues

- [REST, A2A, A2UI가 하나의 비동기 실행 계약을 공유하는 패턴](../architecture-patterns/share-one-async-execution-contract-across-rest-a2a-a2ui-2026-08-24.md)
- [Embabel listener 실패를 Agent 실행과 격리하기](isolate-embabel-listener-failures-from-agent-execution-2026-08-24.md)
- [LLM assessment를 도메인 보고 전에 검증하기](../architecture-patterns/validate-llm-assessments-before-domain-reporting-2026-08-20.md)
