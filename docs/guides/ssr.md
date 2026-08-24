# Thymeleaf SSR 가이드

## 무비용 화면 실행

```bash
SPRING_PROFILES_ACTIVE=fixture,web ./gradlew bootRun
```

`http://127.0.0.1:8080`에서 인플루언서 조회 버튼을 누르면 10명 Catalog가 HTMX fragment로 표시된다. JavaScript가 꺼져 있어도 `/influencers` 일반 링크가 전체 페이지를 반환한다. 9개 Fixture 프로필은 각각 10개의 고정 원문과 수동 assessment를 `RecentCompanyAnalysisService`로 집계하며 X API와 LLM을 호출하지 않는다.

## Serenity live 실행

환경 변수 값은 문서나 source에 기록하지 않는다. 로컬 실행 환경에 `X_BEARER_TOKEN`, `OPENAI_API_KEY`, `OPENAI_BASE_URL`을 주입한 뒤 실행한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,x,live-openai,web ./gradlew bootRun
```

프로필 GET은 외부 호출을 시작하지 않는다. Serenity 상세의 실행 버튼만 `SubmitRecentXAnalysisUseCase`를 호출하며, 세션당 하나의 10분 TTL token을 원자적으로 소비한다. 같은 token 재사용, 다른 세션 token, Fixture profile POST는 실행 Port 호출 전에 거절된다.

진행 상태는 `QueryRecentXAnalysisUseCase`만 사용해 2초마다 갱신한다. terminal 상태에서는 polling 속성이 제거된다. run 생성 후 120초가 지나도 non-terminal이면 자동 조회만 멈추고 run을 실패 처리하지 않으며, 사용자는 같은 run의 상태를 수동으로 다시 확인할 수 있다.

## 실패와 비용 표시

화면은 저장된 public outcome만 복구 안내로 변환하며 provider body, credential, exception message를 표시하지 않는다. 접힌 실험 정보에는 run/Fixture 참조, 포스트 수, X/LLM 실제 호출 수와 budget, duration, cache/Fixture 여부와 warning을 표시한다.

Fixture evidence는 `urn:pinbabel:fixture:...` reference로만 표시한다. 클릭 가능한 외부 근거는 정확한 `https://x.com/...` host만 허용한다.

## Frontend dependency

htmx 2.0.10은 CDN 대신 `src/main/resources/static/vendor/htmx/`에 license와 함께 고정했다.

- `htmx-2.0.10.min.js` SHA-256: `71ea67185bfa8c98c39d31717c6fce5d852370fcdfd129db4543774d3145c0de`
- `LICENSE` SHA-256: `d3d2456f76414f2456104660ebd65aff1c04cd7966b942bdabd63f3cdb316a38`

운영 또는 외부 network 공개는 현재 범위가 아니다. 인증·인가, tenant, rate limit과 durable worker를 별도로 설계하기 전까지 server는 loopback에만 bind한다.
