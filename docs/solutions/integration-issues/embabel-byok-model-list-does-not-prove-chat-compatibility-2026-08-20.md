---
title: Embabel BYOK 모델 목록과 Chat Completion 호환성을 별도로 검증하기
date: 2026-08-20
category: integration-issues
module: influenceranalysis
problem_type: integration_issue
component: assistant
symptoms:
  - "OpenAI-compatible gateway의 /models 요청은 성공하지만 Embabel buildValidated()가 실패함"
  - "모델 목록에 존재하는 모델도 gateway의 Chat Completion 변환 오류로 HTTP 500을 반환함"
root_cause: config_error
resolution_type: config_change
severity: medium
tags: [embabel, byok, openai-compatible, model-validation, chat-completion, gateway]
---

# Embabel BYOK 모델 목록과 Chat Completion 호환성을 별도로 검증하기

## Problem

Embabel 1.5.0의 `OpenAiCompatibleModelFactory`로 사내 OpenAI-compatible gateway를 연결할 때 인증과 `/models` 조회가 정상이어도 애플리케이션 시작 시 `buildValidated()`가 실패할 수 있다. 모델이 목록에 있다는 사실은 해당 모델의 Chat Completion 호출과 gateway 응답 변환까지 동작한다는 뜻이 아니다.

## Symptoms

- 환경 변수의 API key와 base URL이 존재하고 `/models`가 HTTP 200을 반환한다.
- 설정한 `gpt-4.1-mini`는 gateway 모델 목록에 없어 Embabel validation이 실패했다.
- 목록에 있던 `chatgpt-5.6-luna`는 최소 Chat Completion에서도 gateway upstream 변환 오류로 HTTP 500을 반환했다.
- `LiveLlmConfiguration`의 안전한 오류 변환 때문에 사용자 출력에는 `OpenAI-compatible LLM validation failed`만 보인다.

## What Didn't Work

- Embabel 예제에서 흔히 쓰는 모델명을 gateway도 제공할 것이라고 가정했다. OpenAI-compatible은 프로토콜 호환성을 뜻할 뿐 모델 카탈로그까지 동일하다는 뜻이 아니다.
- `/models` 결과에 나타난 첫 경량 모델을 바로 기본 모델로 선택했다. 목록 등록 상태는 실제 completion 경로의 정상 동작을 보장하지 않았다.
- Spring Shell 프로필을 `bootRun --args='--spring.profiles.active=...'`로 넘겼다. Shell이 이 값을 애플리케이션 설정이 아니라 실행할 명령으로 해석했으므로, 프로필은 `SPRING_PROFILES_ACTIVE` 환경 변수로 전달해야 했다.

## Solution

Secret은 `.envrc`와 환경 변수에만 두고, live 프로필에서 Embabel factory가 실제 completion을 호출해 검증하도록 구성한다.

```java
return OpenAiCompatibleModelFactory.Companion.byok(
    settings.baseUrl().toString(),
    settings.apiKey(),
    settings.model(),
    settings.provider()
).buildValidated();
```

검증 순서는 다음과 같다.

1. API key와 base URL의 존재 여부만 확인하고 값을 출력하지 않는다.
2. `/models`에서 후보 모델이 실제로 존재하는지 확인한다.
3. 후보 모델로 작은 Chat Completion을 호출해 HTTP 성공과 응답 변환을 확인한다.
4. 성공한 모델만 `application-live-openai.yaml`의 모델과 Embabel default LLM에 동일하게 지정한다.
5. 애플리케이션을 시작해 `buildValidated()`와 Shell의 `models` 명령으로 최종 등록 상태를 확인한다.

현재 gateway에서는 `gemini-3.6-flash`의 최소 Chat Completion과 Embabel validation이 성공했으므로 다음처럼 고정했다.

```yaml
pinbabel:
  llm:
    model: gemini-3.6-flash
    provider: OpenAI-compatible

embabel:
  models:
    default-llm: gemini-3.6-flash
```

CLI는 프로필을 환경 변수로 전달한다.

```sh
direnv exec . env SPRING_PROFILES_ACTIVE=fixture,cli,live-openai ./gradlew bootRun
```

## Why This Works

Embabel의 `buildValidated()`는 단순한 key 형식 검사나 모델 목록 조회가 아니라 선택한 모델로 실제 `Hi` 메시지를 전송한다. 따라서 gateway 라우팅, 모델 배포, Chat Completion 프로토콜과 응답 변환이 모두 동작해야 `LlmService`가 등록된다. application 설정과 `embabel.models.default-llm`을 같은 검증된 이름으로 맞추면 intent 추출과 post 평가가 동일한 서비스로 실행된다.

Provider 예외의 원문과 cause를 외부 예외에 보존하지 않으면 gateway 응답 본문이나 credential 관련 정보가 stack trace에 섞이는 것도 막을 수 있다. 대신 내부 오류 코드로 구성 실패를 추적한다.

## Prevention

- OpenAI-compatible gateway를 바꾸거나 모델명을 변경할 때 `/models` 조회와 최소 Chat Completion을 별도 검증한다.
- 일반 `build`는 fake 환경과 scripted LLM으로 결정적으로 유지하고, 실제 provider 검증은 명시적인 live 프로필 smoke test로 분리한다.
- `OPENAI_API_KEY`와 `OPENAI_BASE_URL`은 source, YAML 기본값, 테스트 fixture, 명령 인자와 로그에 복사하지 않는다.
- live 설정 테스트에서 HTTP URL, user-info, fragment가 있는 base URL과 key 누락을 네트워크 호출 전에 거절한다.
- Spring Shell 실행 프로필은 command argument가 아니라 `SPRING_PROFILES_ACTIVE`로 전달한다.

## Related Issues

- [LLM 구조화 평가를 검증한 뒤 도메인 리포트로 변환하기](../architecture-patterns/validate-llm-assessments-before-domain-reporting-2026-08-20.md)
- [Embabel GOAP 분기 조건을 Action 후조건으로 연결하기](embabel-goap-branch-conditions-require-postconditions-2026-08-20.md)
