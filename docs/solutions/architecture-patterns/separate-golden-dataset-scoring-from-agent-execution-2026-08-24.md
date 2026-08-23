---
title: Golden Dataset 채점과 Embabel Agent 실행을 분리하기
date: 2026-08-24
category: architecture-patterns
module: influenceranalysis
problem_type: architecture_pattern
component: assistant
severity: medium
applies_when:
  - LLM 기반 분석 품질을 버전이 고정된 기대 결과와 반복 비교할 때
  - 실제 Agent 실행과 결정론적 품질 계산을 함께 제공해야 할 때
  - 평가 결과를 원본 Agent trace까지 역추적해야 할 때
tags: [embabel, golden-dataset, evaluation, sentiment, provenance, hexagonal-architecture]
---

# Golden Dataset 채점과 Embabel Agent 실행을 분리하기

## Context

LLM prompt와 모델을 변경할 때 단순히 “응답이 그럴듯하다”는 확인만으로는 종목 누락, 잘못된 sentiment, 근거 post ID 손실을 회귀로 탐지할 수 없다. 반대로 평가 로직 자체가 Embabel이나 provider에 결합되면 점수 계산을 재현하기 어렵고, 실패 원인이 Agent 실행인지 채점 규칙인지 구분하기 어려워진다.

## Guidance

평가 Harness를 두 경계로 분리한다.

1. `GoldenDatasetEvaluator`는 기대 case와 완성된 `InfluencerAnalysisReport`만 받아 점수를 계산하는 순수 Domain Service로 둔다.
2. `GoldenDatasetEvaluationService`만 기존 `AnalyzeInfluencerPostsUseCase`를 호출해 실제 Embabel 실행을 조정한다.
3. 각 case 결과에 기존 `analysisRunId`를 저장해 Agent planning/action/tool trace와 품질 점수를 연결한다.
4. Golden Dataset loader와 JPA 저장소는 Outbound Port 뒤에 두고 classpath JSON과 H2/JPA 타입을 Application/Domain에 노출하지 않는다.
5. 평가 저장 transaction은 Embabel/network 호출 뒤에 짧게 실행하며, 한 case 실패가 나머지 case 실행을 중단하지 않게 한다.

채점은 설명 가능한 원시 count를 먼저 만들고 ratio를 파생한다.

```text
instrument TP = expected instrumentId가 actual summary에 존재
instrument FN = expected에는 있지만 actual에는 없음
instrument FP = actual에는 있지만 expected에는 없음
sentiment accuracy = 올바른 sentiment 수 / instrument TP
evidence recall = 일치한 기대 post ID 수 / 전체 기대 post ID 수
exact match = 누락·추가 종목, sentiment 오류, 근거 누락이 모두 없음
```

잘못된 sentiment는 종목 탐지 자체는 성공했으므로 TP를 유지하되 sentiment accuracy와 exact match를 낮춘다. 이렇게 해야 entity extraction과 classification 품질을 분리해 해석할 수 있다.

## Why This Matters

순수 evaluator는 실제 API key나 LLM 없이 경계값과 오류 케이스를 빠르게 검증할 수 있다. 실제 평가 실행은 기존 Embabel 유스케이스를 그대로 통과하므로 production과 다른 우회 호출을 만들지 않는다. 또한 aggregate ratio뿐 아니라 case별 불일치와 `analysisRunId`를 보존하면 낮은 점수의 원인을 prompt, Tool, planner trace까지 추적할 수 있다.

Golden Dataset과 case 결과에는 크기 제한과 schema version을 둔다. Dataset은 prompt 입력이므로 case 수, instruction 길이, 기대 종목·근거 수를 제한하고, 저장된 JSON snapshot은 version 불일치를 명시적으로 거부해야 한다.

## When to Apply

- prompt, model 또는 Tool 구성을 바꾸기 전에 회귀 기준선이 필요할 때
- 구조화 출력의 entity detection과 classification을 별도 지표로 보고 싶을 때
- 평가 실패를 원본 Agent 실행 trace와 연결해야 할 때
- fixture에서 시작해 이후 실제 SNS 기반 curated dataset으로 확장할 때

## Examples

CLI의 `pinbabel-evaluate`는 현재 dataset을 실행하고 종목 F1, sentiment 정확도, 근거 recall을 출력한다. `pinbabel-evaluation --id`로 case별 mismatch를 보고, 표시된 `analysisRunId`를 `pinbabel-run --id`에 전달하면 해당 Embabel 실행을 확인할 수 있다.

새 `adapter/out` 또는 `application/port/out` 파일이 Git에서 보이지 않는다면 `.gitignore`의 `out/` 규칙을 확인한다. 이 패턴은 어느 깊이의 `out` 디렉터리에도 적용되므로 Hexagonal package까지 무시한다. IntelliJ 루트 출력 디렉터리만 제외하려면 `/out/`로 anchor한다.

## Related

- [LLM 구조화 평가를 검증한 뒤 도메인 리포트로 변환하기](validate-llm-assessments-before-domain-reporting-2026-08-20.md)
- [Embabel listener 실패를 Agent 실행과 격리하기](../integration-issues/isolate-embabel-listener-failures-from-agent-execution-2026-08-24.md)
- `docs/plans/2026-08-24-002-feat-golden-dataset-evaluation-harness-plan.md`
- `src/main/resources/fixtures/influenceranalysis/golden-dataset-v1.json`
