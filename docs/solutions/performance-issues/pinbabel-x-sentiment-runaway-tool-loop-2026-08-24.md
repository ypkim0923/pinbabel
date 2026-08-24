---
title: 최근 X 분석의 반복 Tool Loop와 유료 호출을 제한하기
date: 2026-08-24
category: performance-issues
module: influenceranalysis
problem_type: performance_issue
component: assistant
severity: high
symptoms:
  - "pinbabel-x-sentiment가 같은 종목 검색과 게시물 읽기를 반복했다"
  - "X 수집이 끝난 뒤에도 CLI가 30초 이상 프롬프트로 돌아오지 않았다"
  - "구조화 출력 재시도가 겹쳐 LLM 지연과 유료 호출 비용이 증가할 수 있었다"
root_cause: logic_error
resolution_type: code_fix
related_components:
  - tooling
  - development_workflow
tags:
  - embabel
  - x-sentiment
  - tool-loop
  - llm-cost
  - timeout
  - single-flight
  - source-grounding
  - pagination
---

# 최근 X 분석의 반복 Tool Loop와 유료 호출을 제한하기

## Problem

최근 X 포스트 10개에서 회사 표현과 sentiment만 찾으면 되는 경로가 기간 분석용 종목 정규화와 내부 Tool을 재사용했다. 라이브 포스트의 회사가 fixture 종목 카탈로그에 없으면 Embabel이 `search_instruments`와 `read_post`를 반복하고, 구조화 출력 바인딩 재시도까지 겹쳐 실행 시간과 유료 LLM 비용을 예측하기 어려웠다.

## Symptoms

- X 수집은 끝났지만 CLI가 장시간 프롬프트로 돌아오지 않았다.
- 로그에 같은 instrument 검색, post 읽기, structured-output binding 시도가 반복됐다.
- 사용자는 실행 중인지 완료된 것인지, 실제 외부 호출이 몇 회 발생했는지 알기 어려웠다.
- 한 게시물의 중복 LLM 행이 여러 sentiment 표로 집계되거나, 잘린 원문 밖의 표현이 근거로 승인될 수 있었다.

## What Didn't Work

- 기간 분석 Agent를 최근 10개 분석에도 사용해 ticker 정규화와 카탈로그 조회를 강제하는 방식
- LLM에 내부 도구를 제공하고 필요한 정보를 찾을 때까지 반복하도록 두는 방식
- LLM이 본 제한된 입력이 아니라 전체 원문을 source-grounding 기준으로 사용하는 방식
- 동일한 `(postId, mention)` 출력 행을 그대로 모두 집계하는 방식
- 동시 요청과 직전 실패마다 같은 유료 분석을 새로 시작하는 방식

## Solution

최근 X 분석을 별도 Embabel Agent로 분리하고 목표를 “10개 bounded snapshot에 실제로 보이는 회사 표현과 sentiment 추출”로 좁혔다.

- 수집과 평가 Action은 `ActionRetryPolicy.FIRE_ONCE`를 사용한다.
- 평가 Action에는 Tool을 제공하지 않고 `RecentCompanyMentionAssessments`를 한 번의 Embabel 구조화 호출로 생성한다.
- LLM timeout은 단일 호출이 정상 완료될 여유를 주도록 60초이며, `live-openai` profile의 data-binding `max-attempts`를 1로 설정한다. 이 설정은 profile 전역이므로 다른 structured-output 경로에도 적용된다.
- 입력은 게시물당 최대 4,000자, 전체 최대 20,000자로 제한한다. 게시물마다 공평한 기본 몫을 먼저 배분한 뒤 남은 예산을 재분배해 뒤쪽 게시물이 빈 입력이 되지 않게 한다.
- `postId`가 실제 batch에 존재하고 `mention`이 LLM에 전달된 정확한 bounded snapshot의 대소문자 구분 substring일 때만 결과를 채택한다.
- `Microsoft`와 `$MSFT` 같은 원문 표현은 정규화하거나 합치지 않는다.
- 같은 `(postId, mention)`의 동일 sentiment 중복은 한 번만 집계한다. 서로 충돌하면 한 개의 `UNCERTAIN` 표로 축약하고 warning을 남긴다.
- 같은 계정의 동시 요청은 single-flight로 하나의 실행에 합친다. 성공 결과는 15분, 실패 결과는 반복 과금을 막기 위해 1분 동안 보관한다.
- 기간 수집이 이미 50건에 도달하고 다음 페이지가 남아 있으면 추가 유료 page 요청 전에 기간 축소 오류를 반환한다.
- Java 호출 경계에서는 Kotlin `Future.get()`이 던지는 checked `ExecutionException`까지 처리하고 cause chain의 timeout을 안전한 `LLM_TIMEOUT` 결과로 변환한다.

## Why This Works

최근 분석에서는 정규화된 ticker가 필수가 아니므로 planner가 외부 지식을 찾을 이유를 제거하는 것이 근본 해결이다. `FIRE_ONCE`, no-tools, timeout, `max-attempts=1`은 각각 Action 반복, Tool Loop, 장기 대기, 구조화 재바인딩을 제한한다.

검증 기준을 전체 원문이 아니라 LLM이 실제로 본 snapshot과 일치시켜 보이지 않은 tail이나 hallucinated company를 승인하지 않는다. post와 mention 단위의 중복 축약은 LLM 출력 행 수가 sentiment vote를 부풀리는 것을 막는다. single-flight와 failure cooldown은 동시에 들어오거나 연속으로 실패하는 요청이 같은 유료 호출을 반복하지 못하게 한다.

## Prevention

- Action annotation의 `FIRE_ONCE`, LLM timeout 60초, 빈 Tool 목록을 테스트로 고정한다.
- 10개의 긴 게시물이 모두 prompt에 포함되고 총 입력이 20,000자를 넘지 않는지 검증한다.
- unknown post ID, hallucinated mention, 잘린 tail에만 있는 mention, 동일·충돌 중복 행을 도메인 테스트에 포함한다.
- 동일 계정 동시 요청이 한 invocation으로 합쳐지고 cache hit와 failure cooldown에서 외부 호출 수가 0인지 검증한다.
- X post limit 경로가 불필요한 다음 page를 호출하지 않는지 request URI 수를 검증한다.
- 기본 회귀 테스트는 fixture와 fake client/model로 실행한다. 비용이 드는 live smoke는 명시적 승인 없이는 실행하지 않는다.
- 최종 검증은 `./gradlew clean build --rerun-tasks --no-build-cache`로 수행한다.

## Related Issues

- [LLM 구조화 평가를 검증한 뒤 도메인 리포트로 변환하기](../architecture-patterns/validate-llm-assessments-before-domain-reporting-2026-08-20.md)
- [X API timeline 응답과 Agent 분석 계약을 안전하게 연결하기](../integration-issues/harden-x-api-timeline-integration-contracts-2026-08-24.md)
