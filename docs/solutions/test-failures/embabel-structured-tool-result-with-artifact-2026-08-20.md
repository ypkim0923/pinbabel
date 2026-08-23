---
title: Embabel 구조화 Tool 반환값의 WithArtifact 검증
date: 2026-08-20
category: test-failures
module: influenceranalysis
problem_type: test_failure
component: testing_framework
symptoms:
  - "@LlmTool 호출 결과를 Tool.Result.Text로 단정한 테스트가 실패함"
root_cause: wrong_api
resolution_type: test_fix
severity: low
tags: [embabel, llm-tool, with-artifact, structured-output, java]
---

# Embabel 구조화 Tool 반환값의 WithArtifact 검증

## Problem

Embabel 1.5.0에서 Java `@LlmTool` 메서드가 record 같은 구조화 객체를 반환할 때, `Tool.call(...)` 결과를 단순 `Tool.Result.Text`로 예상하면 테스트가 실패한다.

## Symptoms

- 실제 결과가 JSON 문자열과 원본 객체를 함께 가진 `Tool.Result.WithArtifact`인데 `Tool.Result.Text` assertion을 사용했다.
- Tool 메서드 자체와 JSON serialization은 정상인데 결과 wrapper 타입 assertion만 실패했다.

## What Didn't Work

- 구조화 Tool 반환도 일반 문자열 반환과 동일하게 `Tool.Result.Text`가 된다고 가정했다.
- JSON content만 확인하려고 했기 때문에 Embabel이 보존한 typed artifact를 검증하지 못했다.

## Solution

구조화 객체를 반환하는 Tool은 `WithArtifact`로 검증하고, LLM에 전달되는 JSON content와 후속 코드가 사용할 typed artifact를 함께 확인한다.

```java
var result = readPost.call("{\"postId\":\"post-1\"}");

assertThat(result).isInstanceOfSatisfying(Tool.Result.WithArtifact.class, response -> {
    assertThat(response.getContent()).contains("post-1", "untrusted content");
    assertThat(response.getArtifact()).isInstanceOf(PostReadResult.class);
});
```

## Why This Works

Embabel의 Java method Tool은 scalar/string 결과와 구조화 객체 결과를 다르게 취급한다. 구조화 객체는 Jackson으로 JSON content를 만들면서 원본 반환 객체도 artifact로 유지하므로, 실제 1.5.0 계약은 `WithArtifact`다.

## Prevention

- Tool discovery는 `Tool.fromInstance(...)`로 실제 resolved Embabel 버전을 사용해 검증한다.
- 구조화 반환 Tool 테스트는 wrapper 타입, JSON content, artifact 타입을 함께 assertion한다.
- Hub의 최신 예제보다 프로젝트에 고정된 Embabel release JAR의 API와 실행 결과를 우선한다.

## Related Issues

- `src/test/java/com/ypkim/pinbabel/influenceranalysis/application/service/tool/AnalysisWorkspaceToolsTest.java`
- `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/tool/AnalysisWorkspaceTools.java`
