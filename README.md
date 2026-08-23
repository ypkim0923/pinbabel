# Pinbabel

Pinbabel은 Embabel 1.5.0으로 주식 인플루언서의 공개 SNS 게시물을 분석하는 실험 프로젝트다. 현재 CLI에서는 fixture 게시물을 대상으로 종목별 긍정·부정·중립·판단 불가 평가와 근거를 생성한다.

## CLI 실행

Java 25와 `OPENAI_API_KEY`, `OPENAI_BASE_URL` 환경 변수가 필요하다. 키는 소스나 설정 파일에 기록하지 않고 프로세스 환경으로만 전달한다. `OPENAI_BASE_URL`은 OpenAI-compatible HTTPS endpoint여야 한다.

```bash
SPRING_PROFILES_ACTIVE=fixture,cli,live-openai ./gradlew bootRun
```

IntelliJ에서는 `PinbabelApplication.main()`을 실행하고 Active profiles를 `fixture,cli,live-openai`로 지정해도 된다.

Shell이 열리면 다음 명령을 사용할 수 있다.

```text
pinbabel --intent 'fixture-social의 0007-market-voice가 2026-01-01T00:00:00Z부터 2026-01-03T00:00:00Z까지 UTC, NASDAQ 포스트를 분석해줘'
pinbabel-runs
pinbabel-run --id <pinbabel 결과에 표시된 runId>
pinbabel-evaluate
pinbabel-evaluations
pinbabel-evaluation --id <pinbabel-evaluate 결과의 evaluationRunId>
```

- `pinbabel`은 분석 결과와 `runId`, 업무 상태, trace 가용성을 출력한다.
- `pinbabel-runs`는 현재 프로세스에서 생성된 최신 실행 20건을 보여준다.
- `pinbabel-run --id`는 안전하게 선별된 Embabel 이벤트와 최종 보고서를 sequence 순서로 보여준다.
- `pinbabel-evaluate`는 버전이 고정된 Golden Dataset으로 현재 모델과 prompt를 실제 실행해 종목 F1, sentiment 정확도, 근거 recall을 계산한다.
- `pinbabel-evaluations`와 `pinbabel-evaluation --id`는 H2에 저장된 최근 평가와 case별 불일치 및 원본 `analysisRunId`를 보여준다.
- 빈 입력과 과도하게 긴 입력은 Embabel 실행 전에 거절하며, 주식 인플루언서 게시물 분석 범위를 벗어난 요청도 `REJECTED`로 종료한다.

## 실행 기록 정책

실행 기록은 H2 인메모리 DB에 저장되므로 애플리케이션을 종료하거나 재시작하면 사라진다. 스키마는 Liquibase가 생성하고 Hibernate는 이를 검증한다.

Golden Dataset은 `src/main/resources/fixtures/influenceranalysis/golden-dataset-v1.json`에 있으며 fixture 입력과 기대 종목, sentiment, 근거 post ID만 보존한다. 평가 case마다 일반 분석 실행 기록도 하나 생성되므로 `pinbabel-run --id`로 Embabel trace를 이어서 확인할 수 있다.

저장 대상은 run 상태, 시각, duration, action/tool/model 식별자, 성공 여부와 provider가 실제 제공한 nullable token·비용 metadata다. Prompt, LLM 응답, Tool 입력·출력, 원문 전체, API key와 raw exception은 실행 이벤트에 저장하지 않는다. 이벤트 저장 실패나 최대 개수 도달은 `traceAvailable=false`로 표시하되 원래 분석의 성공·실패 의미를 바꾸지 않는다.

결과는 공개 SNS 발언에 대한 자동 분석이며 투자 자문, 매수·매도 추천 또는 수익 보장이 아니다.

## 검증

```bash
./gradlew clean build --rerun-tasks --no-build-cache
```
