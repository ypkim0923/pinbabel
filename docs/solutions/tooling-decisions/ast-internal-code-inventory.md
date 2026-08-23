---
title: AST 기반 Internal Code 인벤토리와 레지스트리 검증
date: 2026-08-19
category: tooling-decisions
module: backend-compliance
problem_type: tooling_decision
component: tooling
severity: medium
applies_when:
  - Java 예외 코드의 선언과 실제 사용 위치를 CI에서 완전하게 대조해야 할 때
  - 코드 위치 metadata의 누락과 중복을 Gradle check에서 차단해야 할 때
tags: [internal-code, javac-ast, gradle, compliance, error-registry]
---

# AST 기반 Internal Code 인벤토리와 레지스트리 검증

## Context

Internal Code registry만 수작업으로 관리하면 선언됐지만 사용되지 않는 코드, 등록되지 않은 발생 위치, 한 코드의 다중 사용을 놓칠 수 있다. 빈 inventory 파일도 형식 검증만 통과할 수 있으므로 실제 Java source에서 선언과 semantic occurrence를 생성하는 project-owned 검증이 필요했다.

## Guidance

Gradle task가 JDK compiler API의 `JavacTask`, `Trees`, `TreePathScanner`를 사용해 다음 두 inventory를 생성하게 한다.

- `*InternalCode` enum의 상수 선언 위치
- `InfluencerAnalysisException` 생성자에 전달된 Internal Code의 발생 위치와 종류

enum 상수는 `VariableTree.type == null`이라고 가정하지 않는다. 현재 javac tree에서는 enum 상수도 type을 가질 수 있으므로, initializer가 `NewClassTree`이고 type 이름이 현재 enum과 같은지로 판별한다.

```kotlin
val isEnumConstant = node.initializer is NewClassTree && node.type?.toString() == type
if (type != null && isEnumConstant) {
    declarations += InternalCodeLocation(
        symbol = "$type.${node.name}",
        path = projectPath,
        line = lineOf(node),
    )
}
```

생성된 inventory는 source control 대상이 아니라 `build/internal-code/` 산출물로 유지한다. source control에는 owner namespace, active/retired 상태, 선언·발생 위치를 가진 registry와 generator를 둔다. `validateInternalCodeRegistry`가 generator에 의존하고, 최종적으로 Gradle `check`가 validator에 의존하게 연결한다.

## Why This Matters

source text 정규식은 주석이나 문자열을 오인하고 Java 구문 의미를 증명하지 못한다. AST와 source position을 이용하면 선언·발생 위치를 재현 가능하게 만들 수 있으며, registry가 실제 코드와 달라지는 순간 build가 실패한다. 이 방식은 duplicate, unregistered, unused, retired reuse와 multiple occurrence를 수동 리뷰보다 앞에서 차단한다.

## When to Apply

- 새 Internal Code owner namespace를 도입할 때
- enum 상수나 예외 발생 위치를 추가·이동할 때
- registry 검증이 실제 source inventory 없이 수동 JSON에만 의존할 때

## Examples

`validateInternalCodeRegistry`는 먼저 `generateInternalCodeInventory`를 실행해야 한다.

```kotlin
val validateInternalCodeRegistry = tasks.register<Exec>("validateInternalCodeRegistry") {
    dependsOn(tasks.named("generateInternalCodeInventory"))
    // registry와 생성된 declarations/occurrences를 validator에 전달한다.
}

tasks.named("check") {
    dependsOn(validateInternalCodeRegistry)
}
```

현재 Pinbabel 검증 결과는 26개 active code, 26개 선언, 26개 semantic occurrence가 정확히 1:1로 일치한다.

## Related

- `docs/internal-codes.md`
- `config/internal-code/registry.json`
- `gradle/internal-code-inventory.gradle.kts`
- `gradle/backend-compliance/validate_internal_code_registry.py`
