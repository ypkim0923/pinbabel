# Pinbabel CLI 실행 가이드

Pinbabel CLI는 Embabel 1.5.0 Spring Shell을 개발 콘솔로 사용한다. 현재 단계에서는 `fixture-social` 데이터만 읽으며, 실제 SNS에는 접속하지 않는다.

## 환경 변수

프로젝트 루트의 `.envrc`에 다음 값을 두고 `direnv`로 주입한다. `.envrc`는 Git에서 제외되며 값 자체를 로그나 설정 파일에 복사하지 않는다.

```sh
export OPENAI_API_KEY="..."
export OPENAI_BASE_URL="https://..."
```

최초 한 번 허용한다.

```sh
direnv allow .
```

## CLI 시작

실제 모델을 연결하지 않고 Shell과 Agent metadata만 확인하려면 다음 프로필을 사용한다.

```sh
SPRING_PROFILES_ACTIVE=fixture,cli ./gradlew bootRun
```

실제 OpenAI-compatible 모델로 fixture 분석까지 실행하려면 `live-openai`를 추가한다.

```sh
direnv exec . env SPRING_PROFILES_ACTIVE=fixture,cli,live-openai ./gradlew bootRun
```

현재 gateway의 OpenAI-compatible chat completion으로 검증한 기본 모델은 `gemini-3.6-flash`다. `OPENAI_BASE_URL`이 이 모델을 지원해야 하며, live 프로필은 시작할 때 Embabel의 `buildValidated()`로 연결을 검증한다.

## 명령 예시

Pinbabel이 지원하는 분석, 실행 기록, Golden Dataset 평가 명령은 다음과 같다.

```text
pinbabel "fixture-social의 0007-market-voice가 2026-01-01T00:00:00Z부터 2026-01-03T00:00:00Z까지 UTC, NASDAQ 포스트를 분석해줘"
pinbabel-runs
pinbabel-run --id <pinbabel 결과의 runId>
pinbabel-evaluate
pinbabel-evaluations
pinbabel-evaluation --id <pinbabel-evaluate 결과의 evaluationRunId>
```

`pinbabel-runs`는 현재 프로세스의 최신 20건을, `pinbabel-run --id`는 안전하게 선별된 Embabel 이벤트와 최종 보고서를 보여준다. 실행 기록은 H2 인메모리 DB에만 있으므로 애플리케이션을 재시작하면 사라진다. Prompt, 응답, Tool 입력·출력과 credential은 실행 이벤트에 저장하지 않는다.

`pinbabel-evaluate`는 `golden-dataset-v1.json`의 모든 case를 기존 Embabel 분석 유스케이스로 순차 실행한다. 종목 탐지 precision/recall/F1, sentiment 정확도, 기대 근거 post ID recall과 exact match를 출력하며, 각 case의 `analysisRunId`로 실행 trace를 조회할 수 있다. `pinbabel-evaluations`는 최신 평가 20건, `pinbabel-evaluation --id`는 case별 점수와 불일치 사유를 보여준다. 평가 기록도 같은 H2 인메모리 DB에 저장된다.

다음 Embabel Shell 명령으로 Agent 구성과 모델 상태를 확인할 수 있다.

```text
agents
actions
goals
models
profiles
```

Embabel의 계획 선택을 직접 관찰할 때는 closed mode인 `x`만 진단용으로 사용한다.

```text
x "fixture-social의 0007-market-voice가 2026-01-01T00:00:00Z부터 2026-01-03T00:00:00Z까지 UTC, NASDAQ 포스트를 분석해줘"
```

`x -o` 또는 범용 open mode는 Pinbabel의 지원 경로가 아니다. `chat`도 Pinbabel 전용 inbound use case를 사용하므로 주식 인플루언서 포스트 분석 밖의 요청, 투자 자문, 매수·매도 추천, 가격 예측과 prompt injection 요청을 거절한다.

## 현재 fixture 범위

- 플랫폼: `fixture-social`
- 인플루언서: `0007-market-voice`
- 시장: `NASDAQ`
- 기간 계약: `[startInclusive, endExclusive)`
- 결과: 긍정, 부정, 중립, 판단 불가 분류와 원문 provenance

출력은 공개 SNS 발언에 대한 자동 분석이며 투자 자문이 아니다.
