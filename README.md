# Pinbabel

Pinbabel은 Embabel 1.5.0으로 주식 인플루언서의 공개 SNS 게시물을 분석하는 실험 프로젝트다. CLI에서는 재현 가능한 fixture 또는 X API의 공개 게시물을 대상으로 종목별 긍정·부정·중립·판단 불가 평가와 근거를 생성한다.

## 로컬 MySQL 실행

애플리케이션과 테스트는 기본적으로 Docker의 MySQL을 사용한다. 최초 한 번 `.env.example`을 `.env`로 복사해 로컬 비밀번호를 정한 뒤 MySQL을 시작한다. `.env`와 `data/`는 Git에서 제외된다.

```bash
cp .env.example .env
docker compose up -d mysql
docker compose ps
```

- 애플리케이션 DB: `pinbabel`
- 테스트 DB: `pinbabel_test`
- 영속 데이터: `data/mysql`
- 로컬 접속: `127.0.0.1:3306` (포트는 `.env`의 `PINBABEL_DB_PORT`로 변경 가능)

Liquibase가 두 schema의 테이블을 생성하고 Hibernate는 schema를 검증한다. `docker compose stop mysql` 또는 `docker compose down` 후에도 bind mount의 데이터는 유지된다. `data/mysql`을 삭제하면 로컬 데이터가 복구되지 않으므로 필요한 경우 먼저 백업한다.

IntelliJ에서는 프로젝트 루트를 Working directory로 두고 `PinbabelApplication.main()`을 실행하면 `application.yaml`이 루트의 `.env`를 읽어 같은 MySQL에 연결한다. 테스트 실행 전에도 MySQL 컨테이너가 healthy 상태여야 한다.

## Thymeleaf SSR 실행

외부 호출 없이 화면 전체 흐름을 확인하려면 다음 profile로 실행한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,web ./gradlew bootRun
```

브라우저에서 `http://127.0.0.1:8080`을 연 뒤 `인플루언서 조회`를 누른다. Serenity 1명과 Fixture 9명이 표시되며, Fixture 프로필은 각 10개의 고정 포스트를 X API·LLM 호출 없이 즉시 분석한다.

Serenity의 실제 최근 포스트 분석까지 실행하려면 환경 변수에 `X_BEARER_TOKEN`, `OPENAI_API_KEY`, `OPENAI_BASE_URL`을 등록하고 다음 profile을 사용한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,x,live-openai,web ./gradlew bootRun
```

Serenity 상세 화면 진입만으로는 비용이 발생하지 않는다. `최근 포스트 10개 분석` 버튼을 명시적으로 누를 때만 기존 비동기 실행 Port가 X API 최대 2회, LLM 최대 1회를 사용한다. 중복 POST는 10분 TTL의 일회성 세션 token으로 막고, 상태 조회는 2초 간격으로 최대 120초 동안 수행한다.

## CLI 실행

Java 25와 `OPENAI_API_KEY`, `OPENAI_BASE_URL` 환경 변수가 필요하다. 키는 소스나 설정 파일에 기록하지 않고 프로세스 환경으로만 전달한다. `OPENAI_BASE_URL`은 OpenAI-compatible HTTPS endpoint여야 한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,cli,live-openai ./gradlew bootRun
```

IntelliJ에서는 `PinbabelApplication.main()`을 실행하고 Active profiles를 `fixture,cli,live-openai`로 지정해도 된다.

실제 X 공개 게시물을 수집하려면 `X_BEARER_TOKEN`을 환경 변수로 등록하고 `x` profile을 추가한다. 이때 `fixture`는 종목 카탈로그와 공통 Embabel 실행 구성을 제공하며, SNS source만 X API Adapter로 교체된다.

```bash
SPRING_PROFILES_ACTIVE=fixture,x,cli,live-openai ./gradlew bootRun
```

```text
pinbabel --intent 'x의 XDevelopers가 2026-08-22T00:00:00Z부터 2026-08-24T00:00:00Z까지 작성한 NASDAQ 종목 관련 포스트를 UTC 기준으로 분석해줘'
```

X 수집은 공식 `api.x.com`의 읽기 전용 App-Only API만 사용한다. 한 분석에서 고유 게시물이 50건을 넘으면 일부 결과를 숨기지 않고 기간을 좁히도록 실패하며, API key나 원문 응답 body를 로그에 기록하지 않는다.

실제 X contract smoke test는 과금 가능한 API를 호출하므로 명시적으로 opt-in할 때만 실행한다.

```bash
direnv exec . env PINBABEL_X_LIVE_TEST=true ./gradlew test --tests '*XApiLiveSmokeTest'
```

Shell이 열리면 다음 명령을 사용할 수 있다.

```text
pinbabel --intent 'fixture-social의 0007-market-voice가 2026-01-01T00:00:00Z부터 2026-01-03T00:00:00Z까지 UTC, NASDAQ 포스트를 분석해줘'
pinbabel-recommend-x-accounts
pinbabel-x-companies --account @aleabitoreddit
pinbabel-x-sentiment --account @aleabitoreddit
pinbabel-runs
pinbabel-run --id <pinbabel 결과에 표시된 runId>
pinbabel-evaluate
pinbabel-evaluations
pinbabel-evaluation --id <pinbabel-evaluate 결과의 evaluationRunId>
```

- `pinbabel`은 분석 결과와 `runId`, 업무 상태, trace 가용성을 출력한다.
- `pinbabel-recommend-x-accounts`는 X API와 LLM을 호출하지 않고 사용자 지정 계정 목록을 보여준다.
- `pinbabel-x-companies`는 댓글과 단순 재게시를 제외한 최근 포스트 최대 10개에서 회사명과 cashtag의 원문 표현을 보여준다.
- `pinbabel-x-sentiment`는 같은 단일 LLM 분석에서 긍정·부정 회사 표현을 보여주며 15분 캐시가 있으면 X API와 LLM을 다시 호출하지 않는다.
- `pinbabel-runs`는 현재 프로세스에서 생성된 최신 실행 20건을 보여준다.
- `pinbabel-run --id`는 안전하게 선별된 Embabel 이벤트와 최종 보고서를 sequence 순서로 보여준다.
- `pinbabel-evaluate`는 버전이 고정된 Golden Dataset으로 현재 모델과 prompt를 실제 실행해 종목 F1, sentiment 정확도, 근거 recall을 계산한다.
- `pinbabel-evaluations`와 `pinbabel-evaluation --id`는 MySQL에 저장된 최근 평가와 case별 불일치 및 원본 `analysisRunId`를 보여준다.
- 빈 입력과 과도하게 긴 입력은 Embabel 실행 전에 거절하며, 주식 인플루언서 게시물 분석 범위를 벗어난 요청도 `REJECTED`로 종료한다.

## 실행 기록 정책

## 최근 X 비동기 API

`fixture,x,live-openai,api` profile에서는 CLI와 같은 최근 10개 분석을 REST, A2A, A2UI로 호출할 수 있다. REST 접수는 즉시 `202 Accepted`와 `runId`를 반환하고, 결과 조회는 같은 ID를 사용한다.

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/x-influencer-analyses \
  -H 'Content-Type: application/json' \
  -d '{"account":"@aleabitoreddit"}'

curl -sS http://127.0.0.1:8080/api/v1/x-influencer-analyses/<runId>
```

A2A는 `message/send`의 DataPart에 `operation=analyzeRecentXCompanies`와 `account`를 전달하며, A2UI는 `/a2ui/v0.9/x-influencer-analyses`에서 NDJSON snapshot을 제공한다. REST, A2A, A2UI와 Thymeleaf SSR은 동일한 비동기 Port와 MySQL 결과 artifact를 사용한다.

Postman에서 A2UI와 Embabel A2A를 직접 확인하려면 `postman/Pinbabel-A2UI.postman_collection.json`과 `postman/Pinbabel-Local.postman_environment.json`을 가져온다. Fixture 호출과 실제 X 호출의 실행 순서 및 유료 호출 보호 방법은 `postman/README.md`에 정리되어 있다.

실행 기록과 평가 결과는 MySQL에 저장되어 애플리케이션을 재시작해도 유지된다. 스키마는 Liquibase가 생성하고 Hibernate는 이를 검증한다.

Golden Dataset은 `src/main/resources/fixtures/influenceranalysis/golden-dataset-v1.json`에 있으며 fixture 입력과 기대 종목, sentiment, 근거 post ID만 보존한다. 평가 case마다 일반 분석 실행 기록도 하나 생성되므로 `pinbabel-run --id`로 Embabel trace를 이어서 확인할 수 있다.

저장 대상은 run 상태, 시각, duration, action/tool/model 식별자, 성공 여부와 provider가 실제 제공한 nullable token·비용 metadata다. Prompt, LLM 응답, Tool 입력·출력, 원문 전체, API key와 raw exception은 실행 이벤트에 저장하지 않는다. 이벤트 저장 실패나 최대 개수 도달은 `traceAvailable=false`로 표시하되 원래 분석의 성공·실패 의미를 바꾸지 않는다.

결과는 공개 SNS 발언에 대한 자동 분석이며 투자 자문, 매수·매도 추천 또는 수익 보장이 아니다.

## 검증

```bash
./gradlew clean build --rerun-tasks --no-build-cache
```
