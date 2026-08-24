# Pinbabel CLI 실행 가이드

Pinbabel CLI는 Embabel 1.5.0 Spring Shell을 개발 콘솔로 사용한다. 기본 개발 흐름은 `fixture-social`을 사용하고, `x` profile을 선택하면 X API의 공개 게시물을 읽을 수 있다.

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

X API를 사용하려면 `.envrc` 또는 IntelliJ 실행 환경에 `X_BEARER_TOKEN`을 등록한 뒤 다음 profile로 실행한다.

```bash
direnv exec . env SPRING_PROFILES_ACTIVE=fixture,x,cli,live-openai ./gradlew bootRun
```

```text
pinbabel "x의 XDevelopers가 2026-08-22T00:00:00Z부터 2026-08-24T00:00:00Z까지 작성한 NASDAQ 종목 관련 포스트를 UTC 기준으로 분석해줘"
```

`fixture` profile은 이 실행에서도 Embabel Agent와 종목 카탈로그를 제공하지만, `x` profile이 fixture SNS source를 비활성화하고 X Adapter를 등록한다. X username은 `@` 포함 여부와 관계없이 입력할 수 있다. 수집 결과가 50건을 넘으면 기간을 좁혀 다시 실행해야 한다. X user timeline은 가장 최근 3,200개 게시물까지만 제공하므로 보고서에 범위 제한 경고가 포함된다. X가 HTTP 200과 부분 오류를 함께 반환하면 누락 가능성도 별도 경고로 표시된다.

현재 gateway의 OpenAI-compatible chat completion으로 검증한 기본 모델은 `gemini-3.6-flash`다. `OPENAI_BASE_URL`이 이 모델을 지원해야 하며, live 프로필은 시작할 때 Embabel의 `buildValidated()`로 연결을 검증한다.

## 명령 예시

Pinbabel이 지원하는 분석, 실행 기록, Golden Dataset 평가 명령은 다음과 같다.

```text
pinbabel "fixture-social의 0007-market-voice가 2026-01-01T00:00:00Z부터 2026-01-03T00:00:00Z까지 UTC, NASDAQ 포스트를 분석해줘"
pinbabel-recommend-x-accounts
pinbabel-x-companies --account @aleabitoreddit
pinbabel-x-sentiment --account @aleabitoreddit
pinbabel-runs
pinbabel-run --id <pinbabel 결과의 runId>
pinbabel-evaluate
pinbabel-evaluations
pinbabel-evaluation --id <pinbabel-evaluate 결과의 evaluationRunId>
```

`pinbabel-runs`는 현재 프로세스의 최신 20건을, `pinbabel-run --id`는 안전하게 선별된 Embabel 이벤트와 최종 보고서를 보여준다. 실행 기록은 H2 인메모리 DB에만 있으므로 애플리케이션을 재시작하면 사라진다. Prompt, 응답, Tool 입력·출력과 credential은 실행 이벤트에 저장하지 않는다.

`pinbabel-recommend-x-accounts`는 Embabel의 `recommend_x_stock_influencers` Tool과 동일한 Application Use Case를 사용한다. 현재는 사용자가 지정한 `@aleabitoreddit`만 반환하며 X API와 LLM을 호출하지 않는다. 따라서 실시간 인기 순위가 아니라 비용 없는 초기 실험용 목록이다.

`pinbabel-x-companies`와 `pinbabel-x-sentiment`는 각각 Embabel의 `list_recent_x_mentioned_companies`, `analyze_recent_x_company_sentiment` Tool과 동일한 Application Use Case를 사용한다. X User Posts Timeline에 `max_results=10`과 `exclude=replies,retweets`를 적용하므로 댓글과 단순 재게시를 제외하고, 본인 코멘트가 있는 인용 포스트는 포함한다. pagination과 retry는 하지 않는다. 회사명과 cashtag는 게시물의 원문 표현을 그대로 보존하며, 예를 들어 `Microsoft`와 `$MSFT`를 임의로 합치거나 ticker로 정규화하지 않는다.

첫 cache miss는 username lookup과 timeline 조회로 X API를 최대 2회, 최대 20,000자의 게시물 본문을 사용하는 단일 Embabel 구조화 분석으로 LLM을 최대 1회 호출한다. 본문 예산은 선택된 모든 게시물이 분석 입력에 포함되도록 공정하게 나누고, 잘린 원문 밖의 표현은 근거로 승인하지 않는다. 내부 종목 검색 Tool Loop는 사용하지 않으며 LLM timeout은 60초다. `live-openai` profile의 구조화 출력 최대 시도 횟수도 1회로 제한되며 이 설정은 해당 profile에서 실행되는 다른 구조화 분석에도 적용된다.

성공 결과는 현재 프로세스에서 15분 동안 최대 20계정까지 보관한다. 같은 계정의 동시 요청은 하나의 실행으로 합쳐지고, 실패 결과는 반복 과금을 막기 위해 1분 동안 보관한다. 같은 계정으로 두 명령을 연속 실행하면 두 번째 명령은 `cacheHit=true`, `xApiRequestsThisCall=0`, `llmCallsThisCall=0`을 반환한다. `xApiRequestBudget`과 `llmCallBudget`은 호출 상한이며, 실패해 실제 호출 수를 확정할 수 없으면 `unknown`으로 표시한다. 애플리케이션 재시작 또는 캐시 만료 후에는 다시 과금 가능한 호출이 발생할 수 있다. 모든 출력에는 공개 SNS 자동 분석이며 투자 자문이 아니라는 고지가 포함된다.

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
