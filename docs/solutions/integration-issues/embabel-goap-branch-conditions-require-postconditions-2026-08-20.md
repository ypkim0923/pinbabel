---
title: Embabel GOAP 분기 조건을 Action 후조건으로 연결하기
date: 2026-08-20
category: integration-issues
module: influenceranalysis
problem_type: integration_issue
component: assistant
symptoms:
  - "AgentInvocation이 Action을 하나도 실행하지 않고 STUCK으로 종료됨"
  - "Planner가 hasPosts와 noPosts 조건을 Goal까지 만드는 Action이 없다고 보고함"
root_cause: missing_workflow_step
resolution_type: code_fix
severity: medium
tags: [embabel, goap, planner, condition, postcondition, agent-integration-test]
---

# Embabel GOAP 분기 조건을 Action 후조건으로 연결하기

## Problem

Embabel 1.5.0 Agent가 `CollectedPosts`를 만든 뒤 `hasPosts`와 `noPosts` 조건으로 정상 보고서와 빈 보고서 Goal을 선택하도록 구성됐다. 개별 Action과 Condition 단위 테스트는 통과했지만, 실제 `AgentInvocation`은 첫 Action도 실행하지 못하고 `STUCK`으로 끝났다.

## Symptoms

- `AgentProcess.resultOfType(...)`가 `Cannot get result of process that is not completed: Status=STUCK`을 던진다.
- Planner 로그는 `hasPosts` 또는 `noPosts`가 Goal로 이어지는 Action의 output/post condition이 아니라고 알린다.
- 실행 이력이 비어 있으므로 LLM이나 Tool 문제가 아니라 최초 계획의 도달 가능성 계산 문제다.

## What Didn't Work

- `@Condition` 메서드를 선언하고 후속 Action의 `pre`에 조건 이름만 적었다. 조건을 평가하는 방법은 생겼지만, 어떤 Action 이후 그 조건을 다시 평가할 수 있는지 GOAP 그래프에 나타나지 않았다.
- `IntegrationTestUtils.dummyAgentPlatform(scriptedLlm)`만 사용했다. 기본 dummy 플랫폼은 Spring이 만든 표현식 파서를 갖지 않아 application의 조건 메서드를 동일하게 평가할 수 없다.
- 테스트별 `ProcessOptions.withListener(...)`만으로 dummy 플랫폼의 planning event까지 수집하려 했다. 해당 harness에서는 플랫폼 리스너에도 같은 listener를 연결해야 plan event가 보였다.

## Solution

분기의 기반 데이터를 만드는 Action에 두 조건을 가능한 후조건으로 선언한다. 실제 Action 실행 후에는 `@Condition` 메서드가 현재 `CollectedPosts`를 보고 상호 배타적인 실제 값을 결정하며 Planner가 재계획한다.

```java
@Action(
    description = "Collect bounded posts for the requested influencer and period",
    post = {"hasPosts", "noPosts"},
    readOnly = true
)
public CollectedPosts collectPosts(InfluencerAnalysisRequest request) {
    return socialPostSource.findPosts(request);
}

@Condition(name = "hasPosts")
public boolean hasPosts(CollectedPosts posts) {
    return !posts.isEmpty();
}

@Condition(name = "noPosts")
public boolean noPosts(CollectedPosts posts) {
    return posts.isEmpty();
}
```

Scripted LLM을 쓰는 통합 테스트 플랫폼에는 Spring context의 조건 파서와 플랫폼 이벤트 리스너를 전달한다.

```java
var platform = IntegrationTestUtils.dummyAgentPlatform(
        scriptedLlm,
        listener,
        null,
        agentPlatform.getPlatformServices().getLogicalExpressionParser()
    )
    .deploy(influencerAnalysisAgent);

var options = ProcessOptions.DEFAULT.withListener(listener);
var process = AgentInvocation.builder(platform)
    .options(options)
    .build(InfluencerAnalysisReport.class)
    .run(request);
```

정상 fixture는 `collectPosts -> assessPosts -> buildReport`, 빈 기간은 `collectPosts -> buildEmptyReport`를 실행하는지 assertion한다. 빈 기간에는 prompt와 Tool 호출이 모두 없어야 한다.

## Why This Works

`@Condition`은 현재 world state를 판정하는 실행 로직이고, `@Action(post=...)`는 Planner가 미래 상태의 도달 가능성을 계산하는 그래프 정보다. 두 역할이 모두 있어야 최초 계획에서 수집 Action을 선택하고, 실행 후 실제 데이터에 따라 정상 또는 빈 결과 경로로 재계획할 수 있다.

Spring에서 생성한 logical expression parser를 dummy 플랫폼에 전달하면 annotation 기반 Condition을 운영 context와 같은 방식으로 평가한다. 플랫폼 리스너는 plan formulation event를, per-process listener는 호출 단위 lifecycle event를 관찰하게 해 테스트가 Action 결과뿐 아니라 planning trace도 검증할 수 있다.

## Prevention

- 사용자 정의 Condition을 `pre`로 쓰면 그 조건을 도달 가능하게 만드는 선행 Action의 `post`도 통합 테스트로 고정한다.
- 분기 조건은 동일 world state에서 동시에 참이 아님을 단위 테스트하고, 각 fixture가 반대편 Action을 실행하지 않음을 Planner 테스트로 확인한다.
- `STUCK`과 빈 history가 함께 보이면 LLM stub보다 먼저 Goal 조건과 Action input/output/postcondition 그래프를 확인한다.
- dummy 플랫폼에서 annotation Condition과 plan event를 검증할 때 application의 logical expression parser와 플랫폼 listener를 명시적으로 전달한다.

## Related Issues

- [LLM 평가를 도메인 보고서 전에 검증하는 경계](../architecture-patterns/validate-llm-assessments-before-domain-reporting-2026-08-20.md)
- [Embabel 구조화 Tool 반환값의 WithArtifact 검증](../test-failures/embabel-structured-tool-result-with-artifact-2026-08-20.md)
- `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/InfluencerAnalysisAgent.java`
- `src/test/java/com/ypkim/pinbabel/influenceranalysis/application/service/InfluencerAnalysisAgentIntegrationTest.java`
