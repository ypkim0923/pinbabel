---
title: REST, A2A, A2UI 프로토콜 동등성 Adapter
status: completed
date: 2026-08-24
---

# 목표

기존 비동기 `SubmitInfluencerAnalysisUseCase`와 `QueryAnalysisRunsUseCase`를 REST, A2A, A2UI의 독립 Inbound Adapter로 노출한다. 세 프로토콜은 같은 실행·조회 계약과 correlation/run ID를 공유하지만 Web/Protocol DTO와 mapper는 공유하지 않는다.

# 현재 상태와 호환성 결정

- 실행 접수와 상태 조회 Application Port, H2 영속화, bounded executor가 이미 존재한다.
- REST는 신규 `/api/v1/influencer-analyses` 계약이므로 기존 소비자 호환성 영향이 없다.
- Embabel Agent `1.5.0` 공식 A2A module은 A2A Java SDK `0.3.2.Final`과 protocol `0.3.0`을 사용한다. 현재 A2A 1.0으로 임의 업그레이드하지 않고 이 버전을 명시한다.
- Embabel의 기본 Autonomy A2A handler는 공통 Application Port를 우회하므로 사용하지 않는다. 공식 `embabel-agent-a2a` core의 endpoint registrar와 SDK 계약만 사용하고 Pinbabel handler를 구현한다.
- A2UI는 stable `v0.9` message envelope와 Basic Catalog를 사용하며 HTTP 응답은 `application/x-ndjson` snapshot이다.
- 모든 Endpoint는 `fixture & api` profile에서만 활성화하고 `application-api.yaml`에서 loopback(`127.0.0.1`)에 바인딩한다. 이번 실험 API는 인증이 없는 local-public API이며 외부 공개·운영 배포 대상이 아니다.

# API 계약

| 프로토콜 | 접수 | 조회/발견 | 성공 의미 |
| --- | --- | --- | --- |
| REST v1 | `POST /api/v1/influencer-analyses` | `GET /api/v1/influencer-analyses/{runId}` | 접수 `202`, 조회 `200` |
| A2A 0.3 | JSON-RPC `message/send` at `/a2a` | `tasks/get`, `GET /a2a/.well-known/agent.json` | run ID를 task ID로, correlation ID를 context ID로 사용 |
| A2UI v0.9 | `POST /a2ui/v0.9/analyses` | `GET /a2ui/v0.9/analyses/{runId}` | createSurface/updateComponents/updateDataModel NDJSON snapshot |

REST와 A2UI의 입력은 `instruction` 한 필드이며 최대 4,000자다. REST는 `Location`으로 조회 URI를 제공한다. A2A는 한 개의 text part만 허용하고 continuation, streaming, push notification, cancel을 지원하지 않는다. A2UI snapshot은 지속 연결 streaming이 아니다.

# Change-signal routing

| 신호 | 근거 | 필수 참조 | 적용 결과 | 상태 |
| --- | --- | --- | --- | --- |
| 스킬 정책·검사 자산 | 프로젝트 스킬 자산은 수정하지 않음 | rule ownership, architecture compliance, build verification | 기존 compliance task만 실행 | N/A |
| 모든 Java 백엔드 코드 | Controller, mapper, handler, test 추가 | architecture, coding, unit/negative test, compliance/build | Java 25 및 기존 slice 규칙 준수 | Required |
| HTTP Endpoint/Web 계약 | REST/A2UI HTTP와 A2A HTTP transport 추가 | full mapping, API design, Swagger/OAS, error, security | protocol별 DTO, 상태/미디어 타입/오류 contract test | Required |
| 인증·인가/사용자 경계 | 인증 없는 local-public 실험 API | identity authorization, error, Swagger, negative test | `fixture & api`, loopback 한정. 외부 공개는 별도 보안 설계 필요 | Required |
| Application/Domain 모델 | 공개 조회 resource가 Domain report/metrics를 직접 포함 | DDD, hexagonal, vertical slice, full mapping | Application read model로 명시적 변환 | Required |
| Slice/module 경계 | 같은 influenceranalysis slice 내부 adapter 추가 | module structure, modulith, vertical slice, hexagonal | 새 cross-slice 호출 없이 package architecture 검증 | Required |
| transaction/복수 변경 자원 | 기존 submit 실행 경계만 호출하며 transaction 수정 없음 | module structure, saga, industry, error | 새 transaction·Saga 없음 | N/A |
| 비동기/published event | 기존 bounded executor를 통한 `202` operation | durable events, saga, modulith | 이번 변경은 event contract를 추가하지 않고 기존 상태 조회 사용 | Required |
| 외부 시스템 I/O | A2A SDK는 inbound protocol model이며 outbound 호출 없음 | hexagonal, saga, industry, error, negative | provider I/O 없음 | N/A |
| Persistence/DB | 기존 query port만 사용, repository/entity 미변경 | persistence/error/internal code/negative | DB 변경 없음 | N/A |
| DB schema/migration | schema 변화 없음 | Liquibase/build | changelog 미변경, 기존 검사 실행 | N/A |
| 검색·필터·목록 | 단일 run ID 조회만 추가 | RSQL/paging/full mapping | collection/search 없음 | N/A |
| Projection/비동기 Operation 조회 | `202 Accepted` 및 run status 조회 | full mapping, API design, security, negative | Application Query Port/Read Model과 상태 계약 검증 | Required |
| 외부 URL·고비용 입력 | JSON instruction/body 및 A2A text | security, hexagonal, industry, negative | 4,000자 semantic limit와 request body budget 적용 | Required |
| 예외/오류 경로 | validation, invalid ID, not found, capacity rejection | internal code, error handling, negative | 공개 오류와 A2A JSON-RPC 오류를 안전하게 변환 | Required |
| Internal Code/error catalog | 신규 HTTP unexpected/validation 경로 | internal code, error, build | 기존 registry namespace에 단일 semantic occurrence로 추가 | Required |
| dependency/build | `embabel-agent-a2a:1.5.0` 추가 | coding, security, compliance, build | resolution/SDK version/취약점 검사 | Required |
| 구조 이동/대규모 refactor | 기존 detail resource의 Domain field를 Application resource로 교체 | architecture/module/DDD/hexagonal/modulith/full mapping | 미공개 내부 contract 정리 및 CLI 소비 회귀 test | Required |
| 문서·정책만 | 계획/OpenAPI/동등성 문서 추가지만 실행 코드도 변경 | 주제 소유 문서/build | 코드 검증에 포함 | N/A |

# 구현 단위

1. `AnalysisRunDetailResource`의 metrics/report를 Application-owned resource로 변환하고 query service/CLI test를 갱신한다.
2. REST Request/Response, mapper, ControllerAdvice, Controller와 정적 OpenAPI를 추가한다.
3. 공식 Embabel A2A core dependency, Pinbabel `AgentCardHandler`, registrar configuration과 JSON-RPC mapping을 추가한다.
4. A2UI 전용 Request/NDJSON renderer/Controller를 추가하며 `root` component와 Basic Catalog 계약을 지킨다.
5. 동일 instruction이 세 Adapter에서 동일 submit port로 도달하고 같은 run/correlation/status 의미를 갖는지 contract/integration/architecture test로 검증한다.

# 제외 범위

- 운영 인증·인가, tenant, rate limiter, 외부 network bind
- A2A 1.0 migration, streaming/push/cancel/resume
- A2UI persistent streaming transport와 실제 UI renderer
- durable worker/outbox 재설계, DB schema 변경
- idempotency key(재시도는 새 run을 생성하며 운영 공개 전에 추가)

# Verification matrix

| 검증 영역 | Required/N/A | 근거 | 실행 task/command | final build 연결 |
| --- | --- | --- | --- | --- |
| Always-On | Required | Java/API/build 변경 | 관련 unit/integration, `check`, `clean build` | 예 |
| API·DTO·Mapping | Required | 세 inbound 계약과 read model | adapter contract/integration/architecture tests | 예 |
| RSQL·Pagination | N/A | 검색/목록 endpoint 없음 | 없음 | N/A |
| Persistence·Transaction | N/A | repository/entity/transaction 미변경 | 기존 persistence tests는 final build에서 실행 | 예 |
| Liquibase | N/A | schema/changelog 미변경 | 기존 validator는 final build에서 실행 | 예 |
| Cross-Slice | N/A | 단일 slice 내부 | Modulith verification | 예 |
| Durable Event | N/A | 새 event/publication 없음 | 없음 | N/A |
| Saga | N/A | 독립 변경 자원 없음 | 없음 | N/A |
| Identity·Authorization | Required | local-public 경계 | api profile/loopback config 및 비활성 profile test | 예 |
| Application Security | Required | body/text 입력과 bounded executor | 최대 길이, 초과 body, queue rejection tests | 예 |
| External API | N/A | outbound provider 호출 없음 | 없음 | N/A |
| Dependency·Build | Required | Embabel A2A dependency | `dependencyInsight`, resolved graph 취약점 검사, clean build | 일부 별도 |

최종 명령은 `./gradlew clean build --rerun-tasks --no-build-cache`이며, final build에 없는 dependency resolution·취약점 검사를 별도로 실행한다.

# 승인 게이트

기존 공개 API, schema, event, repository를 변경하지 않는다. 신규 local-public 프로토콜 세트를 한 번에 추가하라는 사용자 요청이 범위 승인을 제공한다. 외부 공개 또는 인증 framework 도입은 이번 범위를 넘어 별도 승인이 필요하다.
