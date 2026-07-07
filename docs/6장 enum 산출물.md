# 6장 - 매직넘버와 식별자 리팩토링 산출물

`status/type/priority/role/action` 매직넘버를 enum으로 바꾸고, 약어 식별자(`d/u/proc/s`)를 의미 있는 이름으로 개선.
**DB 저장값·동작·외부 API 100% 보존** (순수 리팩토링).

---

## 신규 생성 (9개)

### enum (5개)
| 파일 | 역할 |
|---|---|
| `approval/ApprovalStatus.java` | 결재 상태 (임시저장/상신/승인/반려/취소) |
| `approval/ApprovalType.java` | 결재 유형 (지출/휴가/구매/기타) |
| `approval/ApprovalPriority.java` | 우선순위 |
| `approval/ApprovalAction.java` | 처리 구분 (상신/승인/반려/취소 = action 1/2/3/9) |
| `user/UserRole.java` | 사용자 권한 (사원/팀장/임원) |

### Converter (4개) - enum ↔ DB 정수
| 파일 | 매핑 |
|---|---|
| `approval/ApprovalStatusConverter.java` | ApprovalStatus ↔ 0·1·2·3·9 |
| `approval/ApprovalTypeConverter.java` | ApprovalType ↔ 1~4 |
| `approval/ApprovalPriorityConverter.java` | ApprovalPriority ↔ 정수 |
| `user/UserRoleConverter.java` | UserRole ↔ 1~3 (role>=2 승인권한) |

(ApprovalAction은 요청 파라미터라 DB 미저장 → Converter 없음)

## 수정 (3개)
| 파일 | 변경 |
|---|---|
| `approval/Approval.java` | status/type/priority 필드를 enum + `@Convert` |
| `user/User.java` | role 필드를 UserRole enum + `@Convert` |
| `approval/ApprovalService.java` | 매직넘버 비교 → enum 비교 · **약어 식별자 리네임** (d→approval, u→user, proc→action, s→status) |

---

## 보존한 것 (불변 규칙)

- **DB 저장값**: 여전히 정수 (0/1/2/3/9 등) - `@Convert` + `AttributeConverter`, `@Enumerated` 미사용
- **동작 보존 (핵심)**: `fromCode`가 정의 안 된 잘못된 코드(type=5 등)를 만나도 **예외를 던지지 않고 `orElse(null)`로 조용히 처리** - 레거시가 잘못된 값도 조용히 저장하던 동작 그대로. (모든 enum 4개 확인)
- 외부 API·요청/응답 그대로
- **특성화 테스트: 한 글자도 변경 없이 6개 green** (해시 동일) = 동작 안 바뀜 증거

---

## 테스트 결과

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

특성화 테스트 6개 전부 green. 5장에서 만든 안전망이 6장 리팩토링 내내 변경 없이 통과.

---

## 참고 - 정답본(portal-refactored)과의 차이 (Day1 vs Day2)

| 항목 | 여기(Day1 6장) | 정답본(Day2 완료) |
|---|---|---|
| enum 이름 | ApprovalStatus/Type/ApprovalPriority/UserRole/ApprovalAction | 동일 (Action 제외) |
| 패키지 | flat (`approval/`) | `approval/domain·service·repository` 재배치 |
| DIP·AmountGrade | 없음 | 있음 |

→ 패키지 분리·DIP·AmountGrade는 **2일차(클래스 책임 분리)** 작업이라 Day1 6장엔 정상적으로 없음.
