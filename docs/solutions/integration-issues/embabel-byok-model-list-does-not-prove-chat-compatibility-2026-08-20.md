---
title: Embabel BYOK 모델 목록과 Chat Completion 호환성을 별도로 검증하기
date: 2026-08-20
last_updated: 2026-08-24
category: integration-issues
module: influenceranalysis
problem_type: integration_issue
component: assistant
symptoms:
  - "OpenAI-compatible gateway의 모델 목록 조회는 성공하지만 실제 Chat Completion은 실패하거나 지연됨"
  - "buildValidated()가 Spring Bean 생성 중 실제 요청을 보내 Tomcat 준비를 수분간 차단함"
  - "SSR 분석 버튼이 비활성 상태로 보이거나 웹 서버 자체에 연결할 수 없음"
root_cause: wrong_api
resolution_type: code_fix
severity: high
tags: [embabel, byok, openai-compatible, model-validation, startup-network-call, ssr-readiness]
---

# Embabel BYOK 모델 목록과 Chat Completion 호환성을 별도로 검증하기

## Problem

Embabel 1.5.0의 `OpenAiCompatibleModelFactory`로 사내 OpenAI-compatible gateway를 연결할 때 인증과 모델 목록 조회가 정상이어도 Chat Completion 호환성은 보장되지 않는다. 더 큰 문제는 `buildValidated()`가 단순 설정 검사가 아니라 실제 `Hi` 메시지를 동기 전송하므로, Spring Bean 생성 중 사용하면 provider 연결 지연이 ApplicationContext와 Tomcat 준비까지 막는다는 점이다.

SSR 실행 프로필을 `fixture,x,web,live-openai`로 맞춘 뒤 `restartedMain` 스레드가 OkHttp connect → Chat Completion → `buildValidated()`에서 3분 이상 대기했다. 그동안 8080 포트가 열리지 않아 분석 버튼을 사용할 수 없었다.

## Symptoms

- API key와 HTTPS base URL이 설정되어도 `Tomcat started` 로그가 나타나지 않는다.
- 스레드 덤프에서 `LiveLlmConfiguration.pinbabelOpenAiLlm()`이 `OpenAiCompatibleModelFactory.buildValidated()`의 실제 Chat Completion 연결을 기다린다.
- 브라우저는 SSR 페이지에 연결할 수 없거나 이전 JVM이 렌더링한 비활성 안내를 계속 보여준다.
- provider가 늦게 실패하면 `OpenAI-compatible LLM validation failed`가 나타나지만, 그 전까지 startup이 차단된다.

## What Didn't Work

- 모델 목록에 보이는 이름을 선택하는 것만으로 Chat Completion 호환성을 판단했다. 목록 등록은 gateway 라우팅과 응답 변환 성공을 보장하지 않는다.
- stale JVM을 종료하고 IntelliJ profile을 교정하는 데서 멈췄다. 실행 환경 충돌은 제거됐지만 startup network validation은 그대로 남았다.
- live Bean에서 `buildValidated()`를 사용했다. provider 오류를 안전한 내부 코드로 변환해도 connect가 끝날 때까지 애플리케이션 readiness가 계속 차단된다.
- Spring Shell profile을 command argument로 넘겼다. Shell에서는 `SPRING_PROFILES_ACTIVE` 환경 변수로 전달해야 하며, SSR 실행에는 `cli`가 아니라 `web` profile이 필요하다.

## Solution

Bean 생성 시에는 key와 base URL의 형식만 로컬에서 확인하고, 외부 요청 없이 `LlmService`를 등록한다.

```java
return new OpenAiCompatibleModelFactory(
    settings.baseUrl().toString(),
    settings.apiKey(),
    null,
    null,
    Map.of(),
    ObservationRegistry.NOOP,
    ObjectProviders.INSTANCE.empty(),
    ObjectProviders.INSTANCE.empty()
).openAiCompatibleLlm(
    settings.model(),
    PricingModel.getALL_YOU_CAN_EAT(),
    settings.provider(),
    null
);
```

실제 Chat Completion은 사용자가 `분석 실행`을 요청한 뒤 Embabel Action 경로에서만 수행한다. 이 경로는 기존 LLM timeout과 호출 횟수 제한을 적용하므로 startup readiness와 provider 가용성이 분리된다.

SSR용 IntelliJ 실행 profile은 다음처럼 구성한다.

```text
fixture,x,web,live-openai
```

CLI를 사용할 때만 `web` 대신 `cli`를 선택한다. `cli` profile은 web application type을 `none`으로 설정하므로 두 profile을 동시에 활성화하지 않는다.

## Why This Works

애플리케이션 기동에는 결정적인 로컬 설정 검증만 남고, 비용과 지연을 동반하는 provider 호출은 명시적인 사용자 실행 시점으로 이동한다. 따라서 provider가 느리거나 일시적으로 사용할 수 없어도 Tomcat과 SSR 화면은 준비된다. 실제 분석 실패는 이미 정의된 timeout과 안전한 failure mapping을 통해 반환된다.

연결이 닫힌 `https://127.0.0.1:1`을 사용하는 단위 테스트에서 `pinbabelOpenAiLlm()`이 즉시 서비스를 반환하도록 검증하면 startup network call의 재도입을 비용 없이 탐지할 수 있다.

## Prevention

- Spring Bean 생성, readiness, 일반 build에서 유료 provider 요청을 실행하지 않는다.
- key 존재와 HTTPS base URL 형식은 로컬에서 검증하되 실제 모델 호환성은 명시적인 opt-in smoke test 또는 사용자 실행 경로에서 확인한다.
- startup 테스트는 닫힌 loopback endpoint를 사용해 외부 연결 없이 모델 등록이 완료되는지 검증한다.
- SSR은 `fixture,x,web,live-openai`, CLI는 `fixture,x,cli,live-openai`처럼 실행 모드를 분리한다.
- 실제 LLM 호출에는 timeout, 호출 횟수와 비용 budget, 안전한 오류 변환을 유지한다.
- 변경 후 `./gradlew clean build --rerun-tasks --no-build-cache`와 실제 SSR의 `executionToken` 및 `분석 실행` 렌더링을 확인하되 smoke 검증에서 버튼은 누르지 않는다.

## Related Issues

- [REST, A2A, A2UI에서 하나의 비동기 실행 계약 공유하기](../architecture-patterns/share-one-async-execution-contract-across-rest-a2a-a2ui-2026-08-24.md)
- [X API timeline 통합 계약 강화하기](harden-x-api-timeline-integration-contracts-2026-08-24.md)
