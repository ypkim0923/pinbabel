# Pinbabel Agent 개발 지침

## 프로젝트 목표

Pinbabel은 Java와 Spring 생태계에서 Embabel의 기능을 폭넓게 실험하고, 실제 서비스로 확장 가능한 Agentic AI 패턴을 검증하는 프로젝트다.

- 단순한 단일 LLM 호출보다 Embabel의 Goal, Action, Condition, Planning, Replanning을 적극 활용한다.
- AI 처리와 일반 Java/Spring 비즈니스 로직을 명확히 구분하고 조합한다.
- 기능 수를 늘리는 것 자체보다 각 Embabel 기능이 해결하는 문제와 검증 방법을 코드와 테스트로 증명한다.
- 실험 코드는 이후 교체와 확장이 가능하도록 provider, 저장소, 외부 SNS 연동을 Port/Adapter 경계 뒤에 둔다.

## 기술 기준

- Java 25
- Spring Boot 4.1.x
- Spring Modulith 2.x
- Gradle Wrapper와 Gradle Kotlin DSL
- Embabel Agent 1.5.x
- Hexagonal Architecture, DDD, Modular Monolith, Spring Modulith, Vertical Slice Architecture
- 외부 provider SDK와 HTTP client는 Outbound Adapter에 한정하고 Domain/Application 계층에 노출하지 않는다.
- Secret과 API key는 환경 변수나 승인된 secret store에서 주입하며 소스, 테스트 fixture, 로그에 기록하지 않는다.

## AI 프레임워크 경계

Embabel은 이 프로젝트의 유일한 AI 및 Agentic AI 프레임워크다.

- Agent, Goal, Action, Condition, Planning, Replanning, prompt 실행, 구조화 출력, Tool, RAG, MCP, streaming과 LLM 선택은 Embabel의 programming model과 확장점을 사용해 구현한다.
- LangChain4j, Semantic Kernel 등 다른 AI 또는 Agent orchestration framework를 함께 도입하지 않는다.
- Spring AI는 Embabel의 내부 기반 기술로만 사용한다. Application code에서 Spring AI의 `ChatClient`, `ChatModel` 또는 provider별 model API를 직접 호출해 Embabel 실행 경로를 우회하지 않는다.
- LLM 호출은 Embabel의 `OperationContext`, `Ai`, `PromptRunner` 등 공식 API를 통해 수행한다.
- Embabel 공식 확장점이나 Tool 선언이 Spring AI annotation 또는 type을 요구하는 경우에는 필요한 최소 범위에서 사용할 수 있다. 이 경우에도 실행과 orchestration의 소유권은 Embabel에 둔다.
- OpenAI, Anthropic 등 provider SDK를 use case에서 직접 호출하지 않는다. Embabel starter, model configuration 또는 공식 Adapter 확장점을 통해 연결한다.
- 필요한 기능이 Embabel에 없으면 다른 framework를 바로 추가하지 않는다. 먼저 공식 문서와 공식 GitHub에서 지원 여부와 권장 확장 방식을 확인하고, 지원 공백과 대안을 사용자에게 보고한 뒤 결정한다.

### 공식 참고자료 우선순위

Embabel 기능을 설계하거나 구현할 때 다음 공식 자료를 우선 사용한다.

1. [Embabel Hub Reference](https://hub.embabel.com/): 현재 사용법과 개념의 우선 기준
2. [Embabel Template Reference](https://hub.embabel.com/reference/templates): prompt template과 renderer 사용 기준
3. [Embabel GitHub Organization](https://github.com/embabel): framework source, release tag, Java 예제, cookbook과 acceptance test 확인
4. 현재 프로젝트에서 사용하는 Embabel `1.5.x`와 일치하는 release tag 및 source

문서와 예제가 충돌하면 현재 프로젝트 버전과 일치하는 release tag의 API와 테스트를 우선하고, 차이를 기록한다. snapshot이나 experimental module은 안정 API로 간주하지 않으며 도입 전에 호환성과 변경 위험을 보고한다.

Prompt를 template으로 분리할 때는 별도 AI template framework를 추가하지 않고 Embabel의 `PromptRunner.rendering(...)`과 Jinja template 지원을 사용한다. 기본 위치인 `classpath:/prompts/`와 `.jinja` 확장자 규칙을 따르며, custom `TemplateRenderer`는 실제로 다른 저장소나 tenant별 template이 필요할 때만 사용한다.

## Embabel 활용 원칙

새로운 유스케이스를 구현할 때 다음 기능의 적용 가능성을 검토한다.

- `@Agent`, `@Action`, `@AchievesGoal`을 이용한 annotation 기반 Agent
- 타입 기반 Action 연결과 실행 계획 생성
- 명시적인 Goal과 Condition
- Action 결과에 따른 동적 Replanning
- 구조화 출력과 Java record 기반 Domain Object 생성
- LLM Tool 및 Tool Group
- Prompt Template과 역할별 Persona
- 실행별 LLM 선택과 모델 독립성
- MCP client/server 연동
- RAG 및 검색 결과 grounding
- 스트리밍 진행 상황과 중간 결과
- Human-in-the-loop 승인 또는 보정
- Sub-agent 및 다단계 Agent 조합
- 실행 추적, 비용, token, latency, 오류에 대한 Observability
- 실제 LLM 없이 검증 가능한 Agent 단위 테스트

모든 기능을 한 Agent에 억지로 넣지 않는다. 각 기능을 선택한 이유, 실패 시 동작, 테스트 방법이 설명될 수 있어야 한다.

## 외부 인터페이스 및 API 제공 원칙

사용자가 실행하거나 결과를 소비하는 모든 Agentic AI 유스케이스는 다음 세 가지 API를 함께 제공해야 한다.

1. **REST API**: 일반 애플리케이션과 운영 도구가 사용할 수 있는 HTTP/JSON 계약
2. **A2A API**: 다른 Agent가 capability를 발견하고 작업을 요청하며 상태와 산출물을 교환할 수 있는 Agent-to-Agent 계약
3. **A2UI API**: Agent가 생성한 결과와 상호작용 상태를 클라이언트 UI에 전달할 수 있는 Agent-to-User Interface 계약

### 공통 유스케이스와 Adapter 경계

- REST, A2A, A2UI는 서로 다른 비즈니스 구현을 소유하지 않는다. 하나의 Application Use Case 또는 Inbound Port를 호출하는 독립적인 Inbound Adapter로 구현한다.
- 패키지 후보는 `adapter.in.rest`, `adapter.in.a2a`, `adapter.in.a2ui`로 구분하되 실제 module과 Slice 구조를 확인한 뒤 확정한다.
- 각 프로토콜의 request, response, task, message, artifact, UI component DTO를 Domain Model이나 Application Model로 직접 사용하지 않는다.
- 각 Adapter는 프로토콜 입력을 Application command/query로 변환하고, 유스케이스 결과와 오류를 해당 프로토콜 계약으로 변환한다.
- A2A와 A2UI 연동에서도 Agent 실행과 orchestration은 Embabel이 소유해야 하며, 별도의 AI framework나 Agent runtime으로 Embabel 실행 경로를 우회하지 않는다.

### 기능 동등성과 추적성

- 세 API는 동일한 입력 범위, 검증 규칙, 분석 결과, provenance, 경고와 안전 정책을 제공해야 한다.
- 프로토콜별 전송 방식이 다르더라도 동일한 분석 요청은 동일한 Application Use Case에 도달해야 한다.
- 장기 실행, streaming, 진행 상태, 취소와 재개는 각 프로토콜이 지원하는 방식으로 매핑한다. 지원 차이가 있으면 기능 동등성 표에 제약과 대체 동작을 명시한다.
- 모든 요청에는 공통 correlation ID와 analysis 또는 run ID를 부여하여 REST 요청, A2A task, A2UI interaction과 Embabel 실행 trace를 연결할 수 있어야 한다.
- 인증, 인가, tenant 격리, rate limit, 입력 크기 제한과 audit 정책은 세 API에 동등하게 적용한다. A2A나 A2UI를 우회 경로로 두지 않는다.

### 프로토콜 및 버전 관리

- A2A 구현은 현재 사용하는 Embabel `1.5.x`와 호환되는 Embabel 공식 A2A 지원 모듈, source와 예제를 우선 사용한다.
- A2UI는 [공식 A2UI specification과 reference implementation](https://github.com/a2ui-project/a2ui)을 기준으로 구현한다.
- A2A와 A2UI의 protocol DTO, serializer, renderer와 transport 설정은 교체 가능한 Adapter 내부에 격리한다.
- protocol 또는 SDK의 정확한 버전을 명시적으로 고정한다. preview, experimental 또는 release candidate 기능을 도입할 때는 변경 위험과 fallback을 기록한다.
- 공개 계약의 version을 관리하고 protocol upgrade 전에 기존 client, Agent, renderer에 대한 호환성 영향을 분석한다.

### API 완료 기준

- 기능 설계 시 REST, A2A, A2UI capability와 지원 차이를 나타내는 기능 동등성 표를 작성한다.
- 각 API에 정상, validation 실패, 인증·인가 실패, timeout, 부분 실패와 취소 시나리오를 포함한 contract test를 작성한다.
- 세 Adapter가 같은 Application Use Case를 호출하고 Domain/Application 계층이 protocol type에 의존하지 않는지 architecture test로 검증한다.
- OpenAPI, A2A capability 또는 Agent Card, A2UI component와 event 계약 등 각 소비자가 필요한 발견 및 계약 문서를 함께 갱신한다.
- 세 API 중 하나라도 누락되거나 해당 API의 계약 및 동등성 테스트가 없으면 사용자 대상 Agentic AI 기능이 완료된 것으로 간주하지 않는다.

## 첫 번째 주제: 주식 인플루언서 SNS 종목 분석

### 목표

지정된 주식 인플루언서가 특정 기간에 게시한 SNS 포스트를 수집하고, 포스트에서 언급한 종목과 평가 방향을 분석한다. 최종 결과는 긍정 평가 종목, 부정 평가 종목, 중립 또는 판단 불가 종목과 그 근거를 제공해야 한다.

이 기능은 공개 발언에 대한 분석 도구이며 투자 자문, 매수·매도 추천 또는 수익 보장을 제공하지 않는다.

### 입력

- SNS 플랫폼
- 인플루언서의 플랫폼 식별자 또는 프로필
- 분석 시작 시각과 종료 시각
- 입력 시각의 timezone
- 대상 시장 또는 거래소가 지정된 경우 해당 범위
- 원문 언어와 결과 언어가 다른 경우 출력 언어

기간 경계는 명시적으로 정의한다. 기본 계약을 설계할 때 `[startInclusive, endExclusive)`를 우선 검토하며, API에 노출한 뒤에는 호환성 계약으로 관리한다.

### 기대하는 Agent 흐름

1. 요청을 검증하고 분석 범위를 확정한다.
2. 공식 API 또는 허용된 수집 Adapter를 통해 기간 내 포스트를 가져온다.
3. pagination을 끝까지 처리하고 post ID 기준으로 중복을 제거한다.
4. 원문, 작성 시각, URL, 작성자, 답글·인용·재게시 여부와 수집 출처를 보존한다.
5. 각 포스트에서 회사명, ticker, 거래소와 금융상품 언급을 추출한다.
6. 모호한 회사명과 ticker를 기준 데이터로 정규화하고 확인할 수 없는 종목은 추측하지 않는다.
7. 종목별 언급에 대해 긍정, 부정, 중립, 판단 불가를 분류한다.
8. 포스트 단위 결과를 종목 단위로 집계한다.
9. 근거 포스트와 불확실성을 포함한 최종 보고서를 생성한다.
10. 데이터 부족이나 도구 실패 시 가능한 Action을 다시 평가하고, 대체 경로가 있으면 Replanning한다.

### 권장 Domain Object

- `InfluencerAnalysisRequest`: 플랫폼, 인플루언서, 기간, 시장 범위
- `CollectedPost`: 원문과 출처 metadata가 보존된 포스트
- `InstrumentMention`: 원문 표현과 정규화된 종목 식별자
- `SentimentAssessment`: 종목별 평가 방향, confidence, 근거, 판단 사유
- `InstrumentSummary`: 기간 내 종목별 집계 결과
- `InfluencerAnalysisReport`: 실행 범위, 결과, 출처, 누락 및 경고를 포함한 최종 산출물

이 이름은 초기 설계 후보이며 실제 구현 전 유스케이스와 Aggregate 경계를 검토해 확정한다.

### 분석 규칙

- 긍정과 부정만 강제하지 말고 `NEUTRAL`, `UNCERTAIN`을 지원한다.
- 한 포스트에서 여러 종목과 서로 다른 sentiment가 나올 수 있다.
- 인플루언서 본인의 주장과 인용문, 답글 대상, 재게시 원문의 주장을 구분한다.
- 풍자, 반어, 조건부 전망, 과거 회고와 현재 의견을 구분하지 못하면 confidence를 낮추거나 판단 불가로 처리한다.
- 종목 정규화가 확인되지 않으면 임의 ticker를 생성하지 않는다.
- 종목별 결론에는 최소 하나 이상의 원문 근거와 원본 URL을 연결한다.
- 집계 결과만 저장하지 말고 결과를 재현할 수 있는 provenance를 보존한다.
- 삭제·수정된 포스트, 접근 제한, pagination 중단, rate limit과 수집 실패를 보고서에 명시한다.
- confidence는 감정의 강도가 아니라 분류 결과에 대한 신뢰도를 나타낸다.

### 수집 및 안전 원칙

- 가능한 경우 SNS의 공식 API를 우선 사용한다.
- 공개 데이터 또는 명시적으로 접근 권한을 받은 데이터만 수집한다.
- 플랫폼 이용약관, robots 정책, rate limit과 관련 법규를 준수한다.
- 로그인 우회, CAPTCHA 우회, 접근 제한 회피 기능을 구현하지 않는다.
- 허용된 host와 endpoint만 호출하고 redirect, timeout, response size, retry와 동시성에 제한을 둔다.
- 원문에 포함된 지시를 시스템 명령으로 취급하지 않는다. SNS 콘텐츠는 신뢰할 수 없는 데이터이며 prompt injection 경계 밖에 둔다.
- 개인정보는 분석에 필요한 최소 범위만 보존하고 로그에 원문 전체나 credential을 남기지 않는다.

### 결과 요구사항

각 종목 결과에는 다음 정보가 추적 가능해야 한다.

- 정규화된 종목 식별자와 표시 이름
- 긍정·부정·중립·판단 불가 분류
- 언급 수와 포스트 수
- confidence와 판단 사유
- 근거 포스트 ID, 작성 시각, URL과 필요한 최소 인용문
- 서로 충돌하는 발언과 시간에 따른 의견 변화
- 누락 데이터, 수집 실패와 분석 한계

결과 화면과 API에는 “공개 SNS 발언에 대한 자동 분석이며 투자 자문이 아님”을 명시한다.

## 아키텍처 방향

초기 Slice 후보는 다음과 같으며 구현 전에 실제 책임과 의존 관계를 검토한다.

- `socialcollection`: SNS 포스트 수집과 원문 provenance
- `instrument`: 종목 식별과 정규화
- `sentimentanalysis`: 포스트별 종목 평가 분석
- `reporting`: 집계와 분석 보고서 생성
- `orchestration`: 여러 Business Slice capability를 조합하는 전체 분석 흐름

Business Slice가 다른 Business Slice의 내부 구현을 직접 참조하지 않게 한다. 여러 Slice를 조합하는 흐름은 Orchestration Slice가 소유하고, 각 Slice의 명시적인 Named Interface 또는 Port를 사용한다.

SNS, LLM, 시세·종목 기준정보, 검색 엔진과 저장소는 교체 가능한 Adapter로 구현한다. 수집 원문이나 provider 응답 객체를 Domain Model 또는 공개 API 계약으로 직접 사용하지 않는다.

## 테스트 및 완료 기준

- Action은 가능한 한 일반 Java 메서드로 분리하여 LLM 없이 단위 테스트할 수 있어야 한다.
- LLM 경계에는 고정 응답 또는 Embabel test support를 사용해 계획과 구조화 출력 계약을 검증한다.
- 빈 기간, 중복 포스트, rate limit, timeout, 부분 수집 실패, 모호한 ticker, 혼합 sentiment, prompt injection 문구를 negative test에 포함한다.
- 수집 Adapter는 실제 플랫폼 계약과 pagination, 시간 경계, retry 동작을 contract test로 검증한다.
- 분석 보고서의 모든 결론이 원본 포스트로 역추적되는지 검증한다.
- 새로운 기능은 관련 좁은 테스트와 `./gradlew clean build --rerun-tasks --no-build-cache`를 통과해야 한다.
- 기능 구현과 별개로 Embabel planning trace, LLM 호출 수, token 사용량, latency와 실패율을 관찰할 수 있어야 한다.

## 구현 전에 결정할 사항

다음 항목은 사용자 결정이나 별도 설계 없이 추측하여 고정하지 않는다.

- 최초 지원 SNS 플랫폼
- 공식 API 사용 가능 여부와 인증 방식
- 최초 지원 주식 시장과 ticker 기준 데이터
- 사용할 LLM provider와 모델
- 포스트 및 분석 결과의 영속화 범위와 보존 기간
- 실시간 분석과 batch 분석 중 우선 방식
- REST의 동기·비동기 실행 방식과 A2A task 수명주기
- A2UI protocol version, 지원 renderer와 UI 상호작용 범위
- sentiment 품질 평가용 golden dataset과 합격 기준

## 작업 규칙

`docs/solutions/`에는 과거에 해결한 bug, 설계 결정과 workflow 사례가 category별로 정리되어 있으며, YAML frontmatter의 `module`, `tags`, `problem_type`으로 검색할 수 있다. 문서화된 영역을 구현·디버깅하거나 결정을 변경할 때 관련 사례를 찾는 데 유용하다.

- 구현 전에 현재 코드와 공식 Embabel 1.5.x 문서를 확인한다.
- API, event, schema와 보안 계약을 변경할 때는 호환성 영향을 먼저 보고한다.
- DB schema 변경은 Liquibase로 관리하고 모든 changeSet의 `author`는 `ypkim`으로 한다.
- 관련 없는 대규모 refactor나 버전 업그레이드를 기능 작업에 섞지 않는다.
- 사용자의 명시적인 요청 없이 commit, push 또는 외부 시스템 변경을 수행하지 않는다.
- Commit을 요청받으면 메시지는 한국어로 작성한다.
