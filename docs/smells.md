# 코드 스멜 종합 목록

> 4-4(구조 파악) ~ 4-9(강결합 탐지) 진단을 종합한 코드 스멜 목록. 심각도별 상세 근거는 `docs/4-10. 도출 스멜 - 심각도 표.md` 참조.

| 순위 | 등급 | 스멜 | 위치/범위 | 근거 문서 | 리팩토링 방향 | 성격 |
|:---:|:---:|------|-----------|-----------|----------------|------|
| 1 | 최상 | God Class | `ApprovalService` (검증·영속화·메일·감사로그·권한판정·화면표시 6개 책임) | 4-4 | 책임별 협력 객체로 분해(도메인 로직은 Rich Domain으로 이동) | 개선 대상 |
| 2 | 최상 | Long Method + 깊은 중첩(5단) | `processApproval`(86줄, 상신/승인/반려/취소 4액션 한 메서드) | 4-7 | 액션별 메서드(submit/approve/reject/cancel)로 분해, guard clause로 중첩 완화 | 개선 대상 |
| 3 | 최상 | 매직넘버 - 권한 판정 복붙 | `role >= 2`("팀장 이상")가 Approval(승인/반려) · Notice(게시) 3곳에 흩어짐 | 4-8, 4-5 | `Role` enum + `role.canApprove()` 메서드로 일원화 (DB 저장값은 정수 유지) | 개선 대상 |
| 4 | 상 | 강결합(DIP 위반) - 협력 객체 직접 `new` | `SmtpMailSender`/`FileAuditLogger`를 3개 서비스가 생성자 주입 없이 직접 생성 (총 5건) | 4-9, 4-5 | 인터페이스(`MailSender`/`AuditLogger`) 추출 + 생성자 주입 | 개선 대상 |
| 5 | 상 | 중복 코드 - 감사 로그 조립 | 타임스탬프 포맷 문자열 `"yyyy-MM-dd HH:mm:ss"` + 로그 라인 조립이 3서비스 6곳에 복붙 | 4-5 | `common`에 `AuditService`(포맷터) 추출 | 개선 대상 |
| 6 | 상 | 매직넘버 - 상태값·행위값 축 겹침 | `Approval.status`(0/1/2/3/9)와 `process action`(1/2/3/9)이 같은 숫자를 다른 의미로 사용, `action`은 API로 그대로 노출 | 4-8 | `ApprovalStatus`/`ApprovalAction` 별도 enum + `@Convert`로 code 매핑 | 개선 대상 |
| 7 | 상 | 매직넘버 - 금액 임계값 분산 | `amount>=1000000`(우선순위 자동상향)과 `amountGrade`의 A등급 기준이 같은 값인데 서로 다른 곳에 중복 정의 | 4-8 | 도메인 상수 또는 `AmountGrade` enum으로 일원화 | 개선 대상 |
| 8 | 상 | Primitive Obsession | `Approval.status/type/priority`, `Notice.status/category`, `Schedule.status`, `User.role` 전부 원시 `int`, 의미는 주석에만 존재 | 4-4, 4-8 | 도메인별 enum + `AttributeConverter` (DB 저장값 0/1/2/3/9는 불변) | 개선 대상 |
| 9 | 중 | 중복 코드 - 메일 본문/제목 조립 | "안녕하세요 OOO님," 패턴이 Approval(상신/승인/반려) 3곳 + Notice(긴급공지) 1곳에 반복 | 4-5 | `MailMessageFactory`로 본문/제목 생성 추출 | 개선 대상 |
| 10 | 중 | 중복 코드 - null 체크 가드 + 조용한 무시 | `findById(...).orElse(null); if(==null) return;` 패턴이 3서비스 동일하게 반복 | 4-5 | 가드 중복은 `orElseThrow`로 통일(개선 대상). 조회 실패/권한 없음을 예외 없이 무시하는 **동작 자체**는 보존 | 개선(중복) / 보존(동작) |
| 11 | 중 | Long Method - `ScheduleService.create` | 39줄, 겹침검사(for+if)를 포함해 검증·저장·로그가 한 메서드에 혼재 | 4-7 | 겹침 검사 로직을 별도 메서드/도메인 규칙으로 추출 | 개선 대상 |
| 12 | 중 | 깊은 중첩 - `NoticeService.publish` | 25줄이지만 권한->상태->긴급->전직원 메일 for 루프까지 4단 중첩 | 4-7 | 긴급 메일 발송 루프를 별도 메서드로 추출 | 개선 대상 |
| 13 | 중 | Long Parameter List | `ApprovalService.create` 파라미터 8개 | 4-4, 4-7 | 요청 DTO(record)로 파라미터 묶기 | 개선 대상 |
| 14 | 중 | Anemic Domain Model | `Approval`/`Notice`/`Schedule`/`User` 전부 데이터만 있고 행위 없음 | 4-4 | 상태 전이·판정 로직을 Entity/enum으로 이동(Rich Domain) | 개선 대상 |
| 15 | 중 | DTO 부재 - Entity/Map 직접 노출 | 결재 요청은 `Map`으로 수신, 응답은 Entity를 그대로 반환 | 4-4 | request/response record DTO 분리(JSON 구조는 레거시와 동일하게 유지) | 개선 대상 |
| 16 | 중 | 입력 검증 부재 | `type`/`priority`가 범위 밖 값(예: `type=9`, `priority=5`)이어도 그대로 저장됨 | 4-8 | enum 매핑 시 유효 범위 검증 추가(단, 검증 강화가 기존 저장 동작을 바꾸지 않는지 특성화 테스트로 확인 필요) | 개선 대상 |
| 17 | 중 | 매직넘버 - Notice/Schedule 상태값 | `Notice.status`(0/1/9), `Schedule.status`(0/1/9) 각각 개별 enum 후보, "9=취소/내림" 관례가 문서화되어 있지 않음 | 4-8 | `NoticeStatus`/`ScheduleStatus` enum + `@Convert` | 개선 대상 |
| 18 | 하 | 의미 불명 변수명 | `ScheduleService`의 겹침 검사 for 루프 플래그가 `flag1` | 4-4, 4-7 | 조건식 추출 + 의미 있는 이름(`isOverlapping` 등)으로 교체 | 개선 대상 |
| 19 | 하 | 계층 분리 누락 | `UserController`가 Service 없이 `UserRepository`를 직접 호출 | 4-4 | `UserService` 계층 추가(다만 현재 조회 전용이라 파급력 낮음) | 개선 대상 |
| 20 | 하 | 매직넘버 - Notice.category | `category==2`(긴급) 분기 1곳뿐, 나머지(1/3)는 주석으로만 존재 | 4-8 | `NoticeCategory` enum(우선순위 낮음, 분기 확장 시 대비) | 개선 대상 |
