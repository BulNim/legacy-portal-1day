# 리팩토링 진행 리포트 (refactor/start → HEAD)

- 비교 범위: 태그 `refactor/start`(92ae196, 4장 진단 + 5장 특성화 테스트 완료 시점) → `HEAD`(1c0537b)
- 포함 커밋 2개
  - `7ba0737` 6장 매직넘버+식별자 리팩토링 - enum 도입 + 약어 리네임
  - `1c0537b` 7장 메서드 추출 - processApproval 가드절 + 분기 위임

## 1. 메서드 길이 변화

| 대상 | Before | After |
|---|---|---|
| `ApprovalService.java` 전체 | 197줄 | 249줄 |
| `processApproval()` | 약 86줄 단일 메서드, if-else 중첩 4~5단 (상신/승인/반려/취소가 한 메서드에 뭉쳐 있음) | 진입점은 guard clause 2개(결재문서 없음/사용자 없음) + action 분기만 남기고, `submit()`/`approve()`/`reject()`/`cancel()`/`hasApprovalAuthority()` 5개 private 메서드로 위임. 각 메서드는 자기 책임의 가드 절만 가짐(중첩 1단) |
| `statusLabel()` | if-else 사다리 6단(0/1/2/3/9/else) | `ApprovalStatus.getLabel()`로 위임, 1줄 삼항 |

전체 줄 수는 늘었지만(주석·enum·private 메서드 분해로 인한 자연스러운 증가), 개별 메서드의 순환 복잡도와 중첩 깊이는 크게 낮아졌다.

## 2. 클래스(파일) 수 변화

- `src/main/java` 기준: 17개 → 26개 (+9)
- 신규 파일 9개
  - enum 5개: `ApprovalStatus`, `ApprovalType`, `ApprovalPriority`, `ApprovalAction`, `UserRole`
  - Converter 4개: `ApprovalStatusConverter`, `ApprovalTypeConverter`, `ApprovalPriorityConverter`, `UserRoleConverter` (`ApprovalAction`은 요청 파라미터라 DB 미저장 → Converter 없음)
- 수정 파일 3개: `Approval.java`(+51/-`), `User.java`(+26), `ApprovalService.java`(+314/-153 대규모 재작성)
- 신규 문서 2개: `docs/6장 enum 산출물.md`, `docs/7장 메서드추출 산출물.md`

## 3. 테스트 변화

- `ApprovalServiceCharacterizationTest.java`는 `refactor/start` 커밋(5장)에서 이미 작성 완료된 상태이며, 이후 6장·7장 리팩토링 커밋 두 개 모두에서 **한 글자도 수정되지 않음** (diff 없음, git log 기준 최초 커밋 이후 변경 이력 0건).
- 테스트 6개, 모두 `processApproval` 관찰 가능한 결과(상태값·반려사유·수정시각)를 고정:
  1. 상신 → 권한 있는 사용자 승인 → 승인 상태
  2. 상신 → 권한 있는 사용자 반려 → 반려 상태 + 사유 저장
  3. 기안자 취소 → 취소 상태
  4. 권한 없는 사용자(role=1) 승인 시도 → 조용히 무시(레거시 결함 보존)
  5. 존재하지 않는 ID 처리 → 예외 없이 조용히 무시
  6. 이미 승인된 건 재승인 시도 → 상태·수정시각 불변
- 두 산출물 문서 모두 `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS`로 기록 - 리팩토링 전후 동작 100% 보존이 특성화 테스트로 검증됨.
- `notice`, `schedule`, `user` 도메인은 아직 특성화 테스트가 없음(그대로 남은 위험 구간).

## 4. 주요 리팩토링 내용

### (1) 매직넘버 → enum (6장)
- `Approval.status/type/priority`(int) → `ApprovalStatus/ApprovalType/ApprovalPriority` enum, `User.role`(int) → `UserRole` enum, `processApproval`의 `action`(int) → `ApprovalAction` enum.
- DB 컬럼은 `@Convert` + `AttributeConverter`로 기존 정수(status 0/1/2/3/9, type 1~4, priority 1~3, role 1~3)를 그대로 저장(`@Enumerated` 미사용 - 불변 규칙 준수).
- `getStatus()/getType()/getPriority()/getRole()`은 기존과 동일하게 int 반환(계약 보존), enum이 필요한 내부 로직은 `getStatusEnum()` 등 별도 접근자로 분리.
- **동작 보존**: 정의되지 않은 코드(예: type=5)가 들어와도 예외 없이 `fromCode()`가 null을 반환 → 레거시의 "조용히 무시" 동작을 그대로 재현.

### (2) 약어 식별자 리네임 (6장)
- `d`→`approval`, `u`→`user`, `s`→`currentStatus`, `proc`→`requestedAction`, `tmp`→`label`, `a`→`amount` 등, 코드 곳곳의 한 글자/약어 변수명을 의미 있는 완전한 이름으로 교체.

### (3) processApproval 메서드 추출 (7장)
- 결재문서/사용자 조회 실패를 guard clause로 앞에 빼서 조기 반환.
- 상신/승인/반려/취소 4개 분기를 각각 `submit()`/`approve()`/`reject()`/`cancel()` private 메서드로 추출, 권한 판정은 `hasApprovalAuthority()`로 위임.
- 외부 시그니처 `processApproval(id, userId, action, reason)`과 각 분기의 조건·순서·부수효과(메일 발송, 감사 로그, 상태 전이)는 100% 동일하게 유지.

## 5. 남은 작업 (코드 주석·산출물 문서에 명시된 "이번 라운드 범위 밖")

- **God Class**: `ApprovalService`가 검증+영속화+메일+감사로그+포맷팅+권한판정을 여전히 혼자 담당.
- **Tight Coupling**: `new SmtpMailSender()`, `new FileAuditLogger()` 직접 생성 - 생성자 주입(DIP) 미적용.
- **Duplicated Code**: 메일 본문 생성, 감사 로그 기록 로직이 `submit/approve/reject/cancel`에 여전히 복붙되어 있음.
- **Feature Envy**: `amountGrade()`가 여전히 서비스에서 `Approval`의 금액 필드를 꺼내 등급을 계산.
- **Long Parameter List**: `create()` 파라미터 8개 그대로.
- **테스트 공백**: `notice`/`schedule`/`user` 도메인에는 특성화 테스트가 아직 없음.
- **패키지 재배치**: 현재 `approval/` 플랫 구조 → 정답본(2일차) 기준 `approval/domain·service·repository` 분리 예정.
- 위 항목들은 클린코드 과정 상 다음 단계(클래스 책임 분리·DIP 적용·2일차 리팩토링)에서 다룰 대상으로, 이번 라운드(6~7장)에서는 의도적으로 손대지 않았다.
