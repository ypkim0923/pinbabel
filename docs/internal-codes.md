# Internal Code 규칙

Pinbabel의 Internal Code는 `PIN-<OWNER>-NNNN` 문자열 형식을 사용한다.

- `IAN`: `influenceranalysis` Slice
- 하나의 코드는 하나의 의미 있는 예외 발생 위치만 식별한다.
- 사용이 끝난 코드는 registry에서 `retired`로 보존하며 재사용하지 않는다.
- Internal Code는 사용자 오류 분류가 아니라 로그와 trace에서 실패 위치를 찾기 위한 진단 식별자다.
- 신규 코드는 `config/internal-code/registry.json`에 선언, Java enum 상수와 단일 발생 위치를 같은 변경에 추가한다.
- `generateInternalCodeInventory`가 Java AST에서 선언과 발생 위치 inventory를 생성하며 `validateInternalCodeRegistry`가 이를 registry와 대조한다.
