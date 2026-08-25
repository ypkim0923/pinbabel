# Pinbabel A2UI·A2A Postman 실행

## 가져오기

Postman에서 다음 두 파일을 Import한다.

- `Pinbabel-A2UI.postman_collection.json`
- `Pinbabel-Local.postman_environment.json`

우측 상단 Environment에서 `Pinbabel Local`을 선택한다.

## Fixture A2UI 실행

애플리케이션을 다음 profile로 실행한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,live-openai,api ./gradlew bootRun
```

Collection에서 다음 순서로 요청한다.

1. `1. Fixture 기간 분석 - X API 비용 없음/분석 접수`
2. `1. Fixture 기간 분석 - X API 비용 없음/분석 상태·결과 조회`

접수 응답의 `updateDataModel.value.runId`는 Collection 변수 `fixtureRunId`에 자동 저장된다. 조회 결과가 `RUNNING`이면 잠시 후 상태·결과 조회 요청만 다시 실행한다.

Fixture 기간 분석은 X API를 호출하지 않지만 `live-openai` profile의 LLM은 호출한다. X API 비용과 LLM 호출 비용은 별개다.

A2UI 성공 응답은 하나의 JSON 객체가 아니라 다음 세 JSON 객체를 줄 단위로 전달하는 `application/x-ndjson`이다.

```text
createSurface
updateComponents
updateDataModel
```

## 실제 최근 X 분석

실제 X 분석을 사용하려면 환경 변수 `X_BEARER_TOKEN`, `OPENAI_API_KEY`, `OPENAI_BASE_URL`을 설정하고 다음 profile로 실행한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,x,live-openai,api ./gradlew bootRun
```

`최근 X 분석 접수`은 X API 최대 2회와 LLM 최대 1회를 사용할 수 있다. 우발적인 유료 호출을 막기 위해 기본적으로 실행이 차단되어 있다.

1. `Pinbabel Local` 환경의 `allowPaidXCall`을 `true`로 변경한다.
2. `2. 최근 X 분석 - 유료 호출 보호/최근 X 분석 접수`을 한 번만 실행한다.
3. 이후에는 `최근 X 분석 상태·결과 조회`만 다시 실행한다.

접수가 성공하면 Collection 변수의 `allowPaidXCall`은 다시 `false`로 변경된다. 상태 조회 GET은 저장된 MySQL 결과만 읽으며 X API나 LLM을 다시 호출하지 않는다.

API는 로컬 개발 전용으로 `127.0.0.1`에 바인딩된다. Collection도 `localhost`와 `127.0.0.1` 이외의 `baseUrl`은 실행 전에 거절한다.

## Embabel A2A 실행

A2A는 별도의 `@RestController` 대신 Embabel의 `A2AEndpointRegistrar`가 런타임에 다음 Endpoint를 등록한다.

- Agent Card: `GET /a2a/.well-known/agent.json`
- JSON-RPC: `POST /a2a`

Fixture 흐름은 Collection에서 다음 순서로 실행한다.

1. `3. Embabel A2A - Agent Card와 Fixture 분석/Agent Card 조회 - 무과금`
2. `3. Embabel A2A - Agent Card와 Fixture 분석/Fixture 분석 접수 - message/send`
3. `3. Embabel A2A - Agent Card와 Fixture 분석/Fixture 상태·결과 조회 - tasks/get`

`message/send` 응답의 `result.id`는 Collection 변수 `a2aFixtureTaskId`에 자동 저장된다. 조회 결과가 `working`이면 `tasks/get` 요청만 다시 실행한다.

실제 최근 X 분석은 다음 순서로 실행한다.

1. `Pinbabel Local` 환경의 `allowPaidXCall`을 `true`로 변경한다.
2. `4. Embabel A2A - 최근 X 분석 유료 호출 보호/최근 X 분석 접수 - message/send`를 한 번만 실행한다.
3. `4. Embabel A2A - 최근 X 분석 유료 호출 보호/최근 X 상태·결과 조회 - tasks/get`만 반복 조회한다.

최근 X A2A 접수도 X API 최대 2회와 LLM 최대 1회를 사용할 수 있다. 접수 직전에 `allowPaidXCall`이 자동으로 `false`로 돌아가며, `tasks/get`은 MySQL에 저장된 Task와 Artifact만 읽기 때문에 추가 외부 호출을 만들지 않는다.
