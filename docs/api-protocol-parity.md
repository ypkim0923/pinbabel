# 분석 API 기능 동등성

세 Adapter는 `SubmitInfluencerAnalysisUseCase`와 `QueryAnalysisRunsUseCase`만 호출하며 protocol DTO를 Application/Domain model로 사용하지 않는다.

| 기능 | REST v1 | A2A 0.3 | A2UI v0.9 |
| --- | --- | --- | --- |
| 분석 접수 | POST, 202 | `message/send` | POST, 202 + NDJSON snapshot |
| 상태 조회 | GET by run ID | `tasks/get` | GET by run ID + NDJSON snapshot |
| correlation | response field | context ID | data model field |
| 실행 ID | run ID | task ID | run ID/surface ID |
| validation/refusal | 400 response | rejected task/JSON-RPC error | 400 snapshot/error |
| capacity | 429 response | rejected task metadata | 429 snapshot |
| streaming | 미지원 | capability `false` | snapshot만 지원 |
| cancel/resume | 미지원 | unsupported | 미지원 |
| discovery | OpenAPI file | Agent Card | OpenAPI file + catalog ID |

## 로컬 실행

```bash
SPRING_PROFILES_ACTIVE=fixture,live-openai,api ./gradlew bootRun
```

API는 `127.0.0.1`에만 바인딩되며 인증이 없다. 운영 또는 외부 network에 공개하지 않는다. REST/A2UI 계약은 `/openapi/pinbabel-v1.yaml`, A2A Agent Card는 `/a2a/.well-known/agent.json`에서 확인한다.

Embabel 1.5.0의 공식 A2A module이 사용하는 protocol version은 `0.3.0`이다. A2A 1.0 migration은 Embabel 호환 모듈을 확인한 뒤 별도로 진행한다.
