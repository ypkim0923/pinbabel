---
title: LLM 구조화 평가를 검증한 뒤 도메인 리포트로 변환하기
date: 2026-08-20
category: architecture-patterns
module: influenceranalysis
problem_type: architecture_pattern
component: assistant
severity: medium
applies_when:
  - LLM이 Tool로 조회한 데이터에 대한 구조화 평가를 반환할 때
  - 결과에 canonical 식별자와 원본 provenance가 반드시 보존되어야 할 때
  - prompt injection과 hallucinated identifier를 도메인 경계에서 차단해야 할 때
tags: [embabel, structured-output, provenance, prompt-injection, validation, canonical-id]
---

# LLM 구조화 평가를 검증한 뒤 도메인 리포트로 변환하기

## Context

Embabel Agent가 SNS 포스트와 종목 기준정보를 read-only Tool로 탐색한 뒤 `PostAssessments`를 생성한다. 구조화 출력은 JSON 형식을 안정시키지만, LLM이 현재 실행에 없는 post ID나 확인되지 않은 ticker를 반환하지 않는다는 보장은 아니다. 이 결과를 곧바로 최종 리포트로 노출하면 provenance가 끊기거나 존재하지 않는 종목이 분석 결과에 포함될 수 있다.

## Guidance

LLM 출력은 신뢰 경계 밖의 제안으로 취급하고, 결정적인 Java Action이 다음 순서로 최종 도메인 결과를 만든다.

1. 요청마다 `AnalysisWorkspaceTools`를 생성해 현재 실행의 포스트와 허용 시장만 노출한다.
2. prompt에는 전체 SNS 원문을 직접 넣지 않고 post ID와 최소 metadata만 제공한다. 원문은 `read_post`로 읽게 하며, 원문 속 문장은 지시가 아니라 신뢰할 수 없는 데이터라고 명시한다.
3. LLM이 반환한 모든 `postId`를 현재 `CollectedPosts`에 대조한다.
4. 모든 `instrumentId`를 `InstrumentCatalog.findById`로 다시 조회하고 ticker와 허용 시장을 대조한다.
5. canonical 종목을 확인하지 못한 평가는 ticker를 만들지 않고 `UNCERTAIN`과 null identifier로 표현한다.
6. 최종 evidence는 LLM이 반환한 URL이나 작성자 정보가 아니라 검증된 `CollectedPost`에서 다시 조립한다.

핵심 경계는 다음 형태다.

```java
var instrument = instrumentCatalog.findById(assessment.instrumentId())
    .orElseThrow(() -> new InfluencerAnalysisException(
        InfluencerAnalysisInternalCode.ASSESSMENT_INSTRUMENT_NOT_FOUND,
        "Assessment references an unknown canonical instrument"
    ));

if (!instrument.ticker().equalsIgnoreCase(assessment.ticker())) {
    throw new InfluencerAnalysisException(
        InfluencerAnalysisInternalCode.ASSESSMENT_TICKER_MISMATCH,
        "Assessment ticker does not match the canonical instrument"
    );
}
```

## Why This Matters

Tool 권한 제한과 prompt 지침은 공격면과 오류 가능성을 줄이지만 완전한 무결성 보장은 아니다. 최종 Action에서 실행 범위, canonical 기준정보와 provenance를 다시 검증하면 prompt injection이나 hallucination이 발생해도 잘못된 식별자가 신뢰된 도메인 결과로 승격되지 않는다. 또한 최종 리포트는 원본 post URL, 작성자, 게시 시각과 source로 역추적할 수 있다.

## When to Apply

- LLM이 DB, 검색 인덱스, 파일 또는 외부 API에서 조회한 객체의 식별자를 구조화 출력에 포함할 때
- 결과가 감사, 설명 가능성 또는 출처 추적을 요구할 때
- LLM이 읽는 원문에 사용자 작성 지시나 prompt injection 문구가 포함될 수 있을 때
- 명칭이나 ticker를 추측하지 않고 canonical catalog만 사용해야 할 때

## Examples

잘못된 흐름은 LLM의 `postId`, URL과 ticker를 그대로 report DTO에 복사한다. 권장 흐름은 LLM에서 최소한의 평가 키와 판단만 받고, 최종 report의 provenance와 canonical 표시값을 신뢰된 workspace와 catalog에서 다시 만든다.

테스트에서는 다음을 함께 검증한다.

- 현재 workspace 밖의 post ID가 `ASSESSMENT_POST_NOT_FOUND`로 거부된다.
- 존재하지 않는 instrument, ticker 불일치와 허용 시장 밖 종목이 각각 고유 Internal Code로 거부된다.
- prompt에 신뢰 경계 문구와 Tool 사용 규칙이 포함되며 SNS 원문 전체는 포함되지 않는다.
- 충돌하는 긍정·부정 evidence가 모두 보존되고 전체 평가는 `UNCERTAIN`으로 집계된다.

## Related

- [Embabel 구조화 Tool 반환값의 WithArtifact 검증](../test-failures/embabel-structured-tool-result-with-artifact-2026-08-20.md)
- `src/main/java/com/ypkim/pinbabel/influenceranalysis/application/service/InfluencerAnalysisAgent.java`
- `src/main/resources/prompts/influenceranalysis/assess-posts.jinja`
