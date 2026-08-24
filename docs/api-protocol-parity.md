# 분석 API 기능 동등성

REST, A2A, A2UI와 Template Engine SSR Adapter는 분석 유형별 공통 Application Port만 호출하며 protocol DTO와 Web View Model을 Application/Domain model로 사용하지 않는다. 기간 분석은 `SubmitInfluencerAnalysisUseCase`/`QueryAnalysisRunsUseCase`, 최근 X 분석은 `SubmitRecentXAnalysisUseCase`/`QueryRecentXAnalysisUseCase`를 공유한다. 현재 REST/A2A/A2UI는 구현되어 있고 SSR은 다음 UI 단계에서 추가한다.

| 기능 | REST v1 | A2A 0.3 | A2UI v0.9 | Template Engine SSR |
| --- | --- | --- | --- | --- |
| 분석 접수 | POST, 202 | `message/send` | POST, 202 + NDJSON snapshot | 미구현: form POST + redirect 예정 |
| 상태 조회 | GET by run ID | `tasks/get` | GET by run ID + NDJSON snapshot | 미구현: server-rendered result 예정 |
| correlation | response field | context ID | data model field | request/view model field 예정 |
| 실행 ID | run ID | task ID | run ID/surface ID | route/view model field 예정 |
| validation/refusal | 400 response | rejected task/JSON-RPC error | 400 snapshot/error | 동일 화면의 안전한 오류 상태 예정 |
| capacity | 429 response | rejected task metadata | 429 snapshot | 동일 화면의 재시도 안내 예정 |
| streaming | 미지원 | capability `false` | snapshot만 지원 | 초기에는 polling 또는 refresh 예정 |
| cancel/resume | 미지원 | unsupported | 미지원 | 미지원 예정 |
| discovery | OpenAPI file | Agent Card | OpenAPI file + catalog ID | route 및 화면 상태 문서 예정 |

## 최근 X 포스트 분석

| 기능 | REST v1 | A2A 0.3 | A2UI v0.9 | Template Engine SSR |
| --- | --- | --- | --- | --- |
| 분석 접수 | `POST /api/v1/x-influencer-analyses` | `message/send` DataPart | `POST /a2ui/v0.9/x-influencer-analyses` | 미구현: 같은 Port의 form POST 예정 |
| 상태·결과 조회 | GET by run ID | `tasks/get` artifact | GET by run ID + NDJSON snapshot | 미구현: 같은 Port의 server-rendered result 예정 |
| 입력 | `{ "account": "@handle" }` | `{ "operation": "analyzeRecentXCompanies", "account": "@handle" }` | `{ "account": "@handle" }` | 별도 Web View Model 예정 |
| 수집 범위 | 최대 10개, 답글·재게시 제외 | 동일 | 동일 | 동일 예정 |
| 비용 상한 | X 2회·LLM 1회 | 동일 artifact | 동일 data model | 동일 View Model 예정 |
| 결과 영속화 | H2 versioned artifact | 동일 run/artifact | 동일 run/data model | 동일 run/artifact 예정 |

최근 X 결과는 `recent_x_analysis_result`에 schema version과 JSON artifact로 저장한다. 실행 상태는 기존 `analysis_run`을 재사용하며, 네 Adapter가 각각 분석을 다시 구현하거나 외부 호출을 추가하지 않는다.

`fixture-social`과 `x` 입력은 구현된 세 Adapter에서 동일한 instruction 계약으로 공통 Application Use Case에 전달된다. SSR도 같은 Port를 호출하며 별도 분석 구현을 소유하지 않는다. 실제 수집 source는 실행 profile로 선택한다.

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
