---
title: X API timeline 응답과 Agent 분석 계약을 안전하게 연결하기
date: 2026-08-24
category: integration-issues
module: influenceranalysis
problem_type: integration_issue
component: assistant
severity: high
symptoms:
  - HTTP 200 부분 오류가 완전한 수집 성공처럼 처리되어 누락 데이터가 보고서에 드러나지 않음
  - X user timeline의 최근 3,200개 제한 때문에 오래된 기간 분석이 완전한 결과처럼 보일 수 있음
  - long-form, repost와 quote 원문이 잘리거나 다른 작성자의 발언이 인플루언서 의견으로 잘못 귀속됨
  - 402, 429, 5xx와 401/403이 일반 실행 실패로 합쳐져 사용자가 복구 방법을 알 수 없음
  - 활성 SocialPostSource와 scope 및 A2A capability가 달라 지원하지 않는 플랫폼을 광고함
root_cause: wrong_api
resolution_type: code_fix
related_components:
  - testing_framework
tags:
  - x-api
  - timeline
  - partial-response
  - provenance
  - failure-taxonomy
  - profile-capability
  - pagination
  - social-post-adapter
---

# X API timeline 응답과 Agent 분석 계약을 안전하게 연결하기

## Problem

Fixture 기반 `SocialPostSource`를 실제 X 공개 게시물 수집으로 교체할 때 Bearer Token을 붙인 단일 HTTP 호출만으로는 안전하고 완전한 분석 계약을 만들 수 없었다. X App-Only API의 lookup/timeline 순서, Pay-Per-Use credit, 부분 오류, timeline 범위 제한, 장문과 참조 게시물의 발화자 귀속을 Adapter에서 명시적으로 처리해야 했다.

## Symptoms

- `X_BEARER_TOKEN`이 있는 실제 smoke call이 X까지 도달했지만 HTTP 402를 반환했다. 인증 실패가 아니라 X Developer Console의 API credit 부족이었다.
- HTTP 200 응답에 `data`와 `errors`가 함께 있어도 기존 흐름은 오류를 무시했다.
- top-level `text`만 사용하면 `note_tweet.text`가 잘리고, `referenced_tweets` expansion이 없으면 quote/repost 원문과 작성자를 확인할 수 없었다.
- 외부 HTTP body, 느린 body read, pagination token과 JSON 중첩 깊이를 제한하지 않으면 실행이 무한정 점유되거나 부분 결과가 숨겨질 수 있었다.
- Provider별 오류가 `AGENT_EXECUTION_FAILED`로 합쳐져 credit 충전, 기간 축소, 재시도와 권한 확인을 구분할 수 없었다.

## What Didn't Work

- Bearer Token의 존재를 실제 API capability로 간주했다. X API는 별도 prepaid credit이 없으면 402를 반환한다.
- username을 timeline path에 바로 사용하려 했다. 공식 API는 username lookup으로 numeric user ID를 얻은 뒤 user timeline을 호출해야 한다.
- 2xx 여부만 성공 기준으로 사용했다. X는 HTTP 200에도 부분 오류를 포함할 수 있다.
- repost/quote 원문을 인플루언서 본문과 구분 없이 합쳤다. 타인의 발언을 인플루언서 의견으로 오인하고 self-quote는 반대로 타인의 말로 처리할 수 있었다.
- 지원 플랫폼을 정책과 Agent Card에 정적으로 적었다. 실제 활성 Adapter와 발견 계약이 달라졌다.
- Tool이 50개만 노출하는데 Domain이 100개를 허용했다. 51번째 이후 게시물이 분석되지 않아도 완료 결과가 만들어질 수 있었다.

## Solution

X 연동은 `adapter/out/x`에 격리하고, 고정된 `https://api.x.com` host에서 다음 순서로 호출한다.

```text
GET /2/users/by/username/{username}
GET /2/users/{numericUserId}/tweets
  ?max_results=50
  &start_time={startInclusive}
  &end_time={endExclusive}
  &tweet.fields=author_id,created_at,referenced_tweets,note_tweet
  &expansions=referenced_tweets.id
```

`XSocialPostSource`는 post ID로 중복을 제거하고 `(publishedAt, postId)`로 정렬하며 Domain의 `[startInclusive, endExclusive)`를 다시 검증한다. timeline의 완전성을 보장할 수 없으므로 모든 X 수집 결과에 `X_TIMELINE_LIMITED_TO_3200_MOST_RECENT_POSTS`를 남기고, 200 응답의 `errors`는 provider detail 없이 `X_API_PARTIAL_RESPONSE`로 전달한다.

장문은 `note_tweet.text`를 우선한다. 참조 게시물은 원 작성자와 종류를 표시해 본문과 분리한다.

```text
INFLUENCER_COMMENTARY:
still true

QUOTED_PRIOR_INFLUENCER_POST_CONTEXT:
author_id=42
earlier $NVDA view
```

다른 작성자의 quote에는 `QUOTED_POST_CONTEXT_NOT_INFLUENCER_SPEECH`를 사용한다. repost만으로 endorsement를 추론하지 않도록 prompt가 `UNCERTAIN`을 우선하게 하고, expansion이 없으면 `X_REFERENCED_POST_UNAVAILABLE`을 남긴다.

`JdkXApiClient`에는 redirect 금지, connect timeout, body read를 포함한 전체 operation timeout, 1 MiB response limit을 적용했다. X 전용 parser는 JSON nesting depth를 32로 제한한다. post는 Domain과 Tool의 공통 한도인 50개로 맞추고, 반복 token, 32-page ceiling과 51번째 고유 post는 부분 성공 대신 실패시킨다.

활성 수집 Adapter를 capability의 단일 기준으로 사용한다.

```java
public interface SocialPostSource {
    CollectedPosts findPosts(InfluencerAnalysisRequest request);

    default Set<String> supportedPlatforms() {
        return Set.of("fixture-social");
    }
}
```

Application의 `AnalysisCapabilitiesService`가 이를 Inbound Port로 노출하고, A2A Adapter는 Outbound Port를 직접 참조하지 않고 해당 Inbound Port를 사용한다. ArchUnit은 `adapter.in..`에서 `application.port.out..` 의존을 금지한다.

마지막으로 `AnalysisFailurePolicy`가 Embabel 예외 체인에서 안전한 Domain 오류만 추출해 복구 가능한 public outcome으로 변환한다.

| X 실패 | Public outcome | 사용자 행동 |
| --- | --- | --- |
| 402 | `X_API_CREDITS_REQUIRED` | API credit 충전 |
| 429 | `X_API_RATE_LIMITED` | 잠시 후 재시도 |
| 5xx 또는 I/O | `X_API_TEMPORARILY_UNAVAILABLE` | 잠시 후 재시도 |
| 401/403 또는 token 누락 | `X_CONFIGURATION_REQUIRED` | token과 앱 권한 확인 |
| 50개/page 한도 초과 | `X_PERIOD_TOO_BROAD` | 기간 축소 |
| username 오류/not found | `X_INFLUENCER_NOT_FOUND` | 공개 username 확인 |

Provider 응답 body와 credential은 예외, 로그와 fixture에 넣지 않는다.

## Why This Works

공식 App-Only API 호출 순서를 따르면서 X wire contract를 Hexagonal Outbound Adapter에 가둔다. Provider가 표현하는 불완전성을 warning과 provenance로 끝까지 보존하므로 성공 상태가 완전한 데이터라는 잘못된 의미를 갖지 않는다.

장문과 참조 expansion을 수집하되 발화자 label을 prompt까지 전달해 sentiment 귀속을 보존한다. 시간, byte, JSON depth, page와 post 한도가 외부 입력의 자원 사용을 결정적으로 제한하고, 한도 도달 시 조용한 부분 성공을 만들지 않는다.

활성 Adapter, scope policy와 discovery가 같은 capability를 사용하고, 세 API가 공통 Application Use Case를 호출하므로 CLI, REST, A2A와 A2UI의 지원 범위가 일치한다. Provider 오류는 raw detail 없이 행동 가능한 outcome으로 변환되어 사용자가 실패를 반복하는 대신 credit 충전, 기간 축소 또는 재시도를 선택할 수 있다.

## Prevention

- `XSocialPostSourceTest`에서 lookup/timeline URI, pagination, dedupe, 기간 경계, partial errors, coverage warning, long-form, self-quote, repost/quote attribution, HTTP status 분류, malformed payload와 interrupt를 결정적으로 검증한다.
- `JdkXApiClientTest`는 loopback HTTP server로 Authorization/Accept, redirect 금지, 정확한 1 MiB 경계와 느린 body timeout을 실제 JDK transport에서 검증한다.
- `AnalysisFailurePolicyTest`는 Embabel wrapper 내부의 X 오류가 복구 outcome으로 유지되고 provider detail이 노출되지 않는지 확인한다.
- `ProtocolAdapterParityTest`는 Agent Card가 활성 플랫폼만 광고하고 모든 protocol이 같은 instruction을 전달하는지 검증한다.
- 일반 build는 credential과 과금 없이 결정적으로 유지한다. 실제 호출은 `PINBABEL_X_LIVE_TEST=true`인 opt-in smoke test로만 실행한다.
- X status, schema 또는 limit가 바뀌면 Internal Code registry, failure policy, contract test와 CLI/A2A 문서를 함께 갱신한다.

## Related Issues

- [Embabel BYOK 모델 목록과 Chat Completion 호환성을 별도로 검증하기](embabel-byok-model-list-does-not-prove-chat-compatibility-2026-08-20.md)
- [LLM 구조화 평가를 검증한 뒤 도메인 리포트로 변환하기](../architecture-patterns/validate-llm-assessments-before-domain-reporting-2026-08-20.md)
- [AST 기반 Internal Code inventory로 의미적 누락 검증하기](../tooling-decisions/ast-internal-code-inventory.md)
