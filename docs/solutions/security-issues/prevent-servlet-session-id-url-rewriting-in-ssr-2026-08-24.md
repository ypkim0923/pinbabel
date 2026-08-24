---
title: Thymeleaf SSR에서 Servlet 세션 ID의 URL 노출 방지
date: 2026-08-24
category: security-issues
module: influenceranalysis
problem_type: security_issue
component: authentication
symptoms:
  - Thymeleaf가 생성한 내부 링크에 jsessionid 경로 파라미터가 추가될 수 있었다
  - 세션을 사용하는 상세 화면의 URL이 세션 식별자를 포함할 가능성이 있었다
root_cause: config_error
resolution_type: config_change
severity: medium
related_components:
  - assistant
tags:
  - servlet-session
  - thymeleaf
  - ssr
  - url-rewriting
  - spring-boot
---

# Thymeleaf SSR에서 Servlet 세션 ID의 URL 노출 방지

## Problem

SSR 상세 화면이 유료 분석 실행용 일회성 토큰을 `HttpSession`에 보관하면서, 쿠키 지원 여부가 확정되지 않은 초기 응답의 내부 링크에 `;jsessionid=...`가 추가될 수 있었다. 이 값은 브라우저 기록, 화면 캡처, 접근 로그 또는 복사된 URL을 통해 불필요하게 노출될 수 있다.

## Symptoms

- 상세 화면의 홈·목록 링크가 `/influencers;jsessionid=...` 형태로 렌더링됐다.
- 애플리케이션 로직은 세션 쿠키만을 전제로 했지만 Servlet 컨테이너의 URL rewriting fallback은 명시적으로 꺼져 있지 않았다.

## What Didn't Work

- Thymeleaf 링크 표현식만 일반 경로로 작성하는 것으로는 충분하지 않다. Servlet response의 URL encoding 단계가 세션 식별자를 다시 추가할 수 있다.
- 링크마다 문자열 URL을 직접 조립하면 일부 링크만 우회할 뿐이며, 새로운 링크에서 같은 문제가 재발하고 framework의 context-path 처리도 잃는다.

## Solution

`web` profile 설정에서 Servlet 세션 추적 방식을 cookie로 제한했다.

```yaml
server:
  servlet:
    session:
      tracking-modes: cookie
```

유료 실행은 기존과 동일하게 세션에 저장된 고엔트로피 일회성 토큰을 요구한다. 토큰은 10분 안에 한 번만 사용할 수 있고, 검증 성공 시 원자적으로 제거된다. URL 기반 세션 전달은 허용하지 않는다.

## Why This Works

Servlet 컨테이너가 세션을 URL로 전달하는 fallback을 사용하지 못하므로 `response.encodeURL(...)` 경로를 거치는 Thymeleaf 링크에도 세션 ID가 붙지 않는다. 세션은 `HttpOnly` 쿠키 경계 안에 남고, 커스텀 실행 토큰은 별도의 재전송 방지 장치로 동작한다.

## Prevention

- 세션을 사용하는 SSR profile은 `server.servlet.session.tracking-modes=cookie`를 명시한다.
- 통합 또는 브라우저 테스트에서 렌더링된 `href`와 `action`에 `jsessionid`가 없는지 확인한다.
- 유료 또는 외부 side effect를 일으키는 POST는 세션 존재만 믿지 않고, 짧은 TTL의 일회성 intent token과 서버 측 원자적 consume을 함께 사용한다.
- 외부 링크에는 세션 식별자나 실행 토큰을 query/path로 전달하지 않는다.

## Related Issues

- [X API timeline integration hardening](../integration-issues/harden-x-api-timeline-integration-contracts-2026-08-24.md)
