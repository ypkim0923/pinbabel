---
date: 2026-08-24
topic: pinbabel-next-development
focus: Embabel 기능 확장 우선순위
mode: repo-grounded
---

# Pinbabel 다음 개발 아이디어

## Grounding Context

- Pinbabel은 Embabel 1.5.x의 Agentic AI 기능을 실제 주식 인플루언서 분석 유스케이스로 검증한다.
- 현재 fixture 기반 분석, CLI 실행, 범위 외 요청 거절, 구조화 출력과 provenance 검증이 구현되어 있다.
- 다음 개발은 기능 시연뿐 아니라 실행 재현성, 품질 측정, 외부 API 동등성과 실제 데이터 연동을 단계적으로 확보해야 한다.
- 데이터 저장이 필요하면 우선 H2 Embedded 인메모리 DB를 사용한다.

## Evaluation Axes

- Embabel 고유 기능을 얼마나 명확하게 검증하는가
- 현재 fixture/CLI 흐름에서 점진적으로 확장 가능한가
- 결과 품질과 실패 원인을 재현할 수 있는가
- 향후 REST, A2A, A2UI에 공통 기반을 제공하는가
- 외부 SNS와 모델 의존성 없이 자동 테스트할 수 있는가

## Ranked Ideas

### 1. Durable `AnalysisRun`과 Embabel Flight Recorder

- **Status:** Explored
- **핵심:** 모든 분석 실행에 `runId`를 부여하고 실행 상태, 계획, 재계획, Action, Tool, LLM, 완료·실패 이벤트를 시간순으로 기록한다.
- **Embabel 활용:** 실행별 `ProcessOptions.withListener(...)`와 `AgenticEventListener`를 사용해 Embabel의 공식 이벤트 경계를 관찰한다.
- **가치:** CLI 디버깅, 결과 provenance, 비용·token·latency 관측과 이후 REST/A2A/A2UI의 공통 실행 수명주기를 제공한다.
- **초기 저장소:** H2 Embedded 인메모리 DB와 교체 가능한 Outbound Port/Adapter를 사용한다.

### 2. Golden Dataset 기반 Sentiment Evaluation Harness

- **Status:** Unexplored
- **핵심:** 대표 fixture에 기대 종목, sentiment, 근거를 부여하고 모델·prompt 변경 시 정확도와 회귀를 자동 비교한다.
- **가치:** 모델이 그럴듯하게 답하는지를 넘어 실제 분석 품질을 수치로 관리한다.

### 3. REST, A2A, A2UI 공통 실행 계약

- **Status:** Unexplored
- **핵심:** 동일한 Application Use Case와 `AnalysisRun`을 REST, A2A, A2UI Inbound Adapter가 공유한다.
- **가치:** 프로토콜별 중복 구현을 막고 correlation ID, 상태, 진행 이벤트와 결과의 기능 동등성을 확보한다.

### 4. Recorded Contract 기반 실제 SNS Adapter

- **Status:** Unexplored
- **핵심:** 최초 SNS 플랫폼을 선정하고 공식 API 응답을 기록한 fixture로 pagination, 기간 경계, 중복 제거, rate limit과 부분 실패를 먼저 검증한다.
- **가치:** 실제 credential에 의존하지 않는 contract test를 유지하면서 실데이터 연동으로 확장한다.

### 5. 모호한 종목을 위한 Human-in-the-loop 보정

- **Status:** Unexplored
- **핵심:** 종목 정규화가 모호하거나 confidence가 낮을 때 실행을 대기시키고 사용자의 확인 후 재개한다.
- **가치:** Embabel wait/pause와 재계획을 활용하면서 잘못된 ticker 추측을 줄인다.

## 제외된 아이디어

- MCP 연동은 사용자의 현재 개발 범위에서 제외했다. 향후 명시적인 요구가 생기기 전에는 진행하지 않는다.

## Selected Direction

첫 구현 주제는 **Durable `AnalysisRun`과 Embabel Flight Recorder**다. 상세 요구사항을 먼저 확정하고, 이후 구현 계획과 코드 작업으로 넘긴다.
