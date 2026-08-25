# 분석 API 기능 동등성

REST, A2A, A2UI와 Template Engine SSR Adapter는 분석 유형별 공통 Application Port만 호출하며 protocol DTO와 Web View Model을 Application/Domain model로 사용하지 않는다. 기간 분석은 `SubmitInfluencerAnalysisUseCase`/`QueryAnalysisRunsUseCase`, 최근 X 분석은 `SubmitRecentXAnalysisUseCase`/`QueryRecentXAnalysisUseCase`를 공유한다.

| 기능 | REST v1 | A2A 0.3 | A2UI v0.9 | Template Engine SSR |
| --- | --- | --- | --- | --- |
| 분석 접수 | POST, 202 | `message/send` | POST, 202 + NDJSON snapshot | form POST + redirect/HTML fragment |
| 상태 조회 | GET by run ID | `tasks/get` | GET by run ID + NDJSON snapshot | server-rendered HTML fragment |
| correlation | response field | context ID | data model field | 접힌 실험 정보 |
| 실행 ID | run ID | task ID | run ID/surface ID | route + 실험 정보 |
| validation/refusal | 400 response | rejected task/JSON-RPC error | 400 snapshot/error | 안전한 full-page/fragment 오류 |
| capacity | 429 response | rejected task metadata | 429 snapshot | 원인별 복구 안내 |
| streaming | 미지원 | capability `false` | snapshot만 지원 | 2초 polling, 120초 자동 조회 budget |
| cancel/resume | 미지원 | unsupported | 미지원 | 미지원, 수동 상태 재조회 제공 |
| discovery | OpenAPI file | Agent Card | OpenAPI file + catalog ID | `/`, `/influencers` 화면 |

## 최근 X 포스트 분석

| 기능 | REST v1 | A2A 0.3 | A2UI v0.9 | Template Engine SSR |
| --- | --- | --- | --- | --- |
| 분석 접수 | `POST /api/v1/x-influencer-analyses` | `message/send` DataPart | `POST /a2ui/v0.9/x-influencer-analyses` | `POST /influencers/serenity/analyses` |
| 상태·결과 조회 | GET by run ID | `tasks/get` artifact | GET by run ID + NDJSON snapshot | `GET /influencers/serenity/analyses/{runId}` |
| 입력 | `{ "account": "@handle" }` | `{ "operation": "analyzeRecentXCompanies", "account": "@handle" }` | `{ "account": "@handle" }` | server-owned profile ID + 일회성 실행 token |
| 수집 범위 | 최대 10개, 답글·재게시 제외 | 동일 | 동일 | 동일 |
| 비용 상한 | X 2회·LLM 1회 | 동일 artifact | 동일 data model | 동일 View Model |
| 결과 영속화 | MySQL versioned artifact | 동일 run/artifact | 동일 run/data model | 동일 run/artifact |

최근 X 결과는 `recent_x_analysis_result`에 schema version과 JSON artifact로 저장한다. 실행 상태는 기존 `analysis_run`을 재사용하며, 네 Adapter가 각각 분석을 다시 구현하거나 외부 호출을 추가하지 않는다.

`fixture-social`과 `x` 입력은 REST/A2A/A2UI에서 동일한 instruction 계약으로 공통 Application Use Case에 전달된다. SSR live 실행도 같은 Submit/Query Port를 호출하며 별도 분석 구현을 소유하지 않는다. 화면 데모용 Fixture 프로필은 별도의 결정적 Fixture Query Port를 사용하고 외부 호출 수를 0으로 유지한다.

## 로컬 실행

```bash
SPRING_PROFILES_ACTIVE=fixture,live-openai,api ./gradlew bootRun
```

실제 X 공개 게시물을 구현된 API에서 분석하려면 `X_BEARER_TOKEN`과 X API credit을 준비하고 다음 profile을 사용한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,x,live-openai,api ./gradlew bootRun
```

API는 `127.0.0.1`에만 바인딩되며 인증이 없다. 운영 또는 외부 network에 공개하지 않는다. REST/A2UI 계약은 `/openapi/pinbabel-v1.yaml`, A2A Agent Card는 `/a2a/.well-known/agent.json`에서 확인한다.

X user timeline은 가장 최근 3,200개 게시물만 제공한다. 따라서 X 보고서에는 `X_TIMELINE_LIMITED_TO_3200_MOST_RECENT_POSTS` 경고가 항상 포함되며, HTTP 200의 부분 오류가 있으면 `X_API_PARTIAL_RESPONSE`도 포함된다. 수집된 게시물이 50건을 넘거나 pagination 안전 한계를 넘으면 부분 보고서를 반환하지 않고 기간 축소가 필요한 실패로 종료한다.

Embabel 1.5.0의 공식 A2A module이 사용하는 protocol version은 `0.3.0`이다. A2A 1.0 migration은 Embabel 호환 모듈을 확인한 뒤 별도로 진행한다.
