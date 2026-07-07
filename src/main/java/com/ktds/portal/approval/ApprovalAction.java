package com.ktds.portal.approval;

import java.util.Arrays;

/**
 * [리팩토링] processApproval() 의 레거시 action 매직넘버(1=상신,2=승인,3=반려,9=취소) → 여기서 enum으로.
 * action 은 DB 에 저장되는 값이 아니라 processApproval() 호출 시 전달되는 "처리 구분" 파라미터라서
 * @Convert/AttributeConverter 대상이 아니다(영속화 대상 아님). processApproval(id, userId, action, reason)
 * 의 public 시그니처(action: int)는 계약 보존을 위해 그대로 두고, 메서드 내부에서만 이 enum 으로 변환해 분기한다.
 */
public enum ApprovalAction {
    SUBMIT(1),
    APPROVE(2),
    REJECT(3),
    CANCEL(9);

    private final int code;

    ApprovalAction(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    // [동작 보존] 정의되지 않은 action 코드가 와도 예외를 던지지 않고 null 을 반환한다.
    // 호출부에서는 null 이면 어떤 분기에도 해당하지 않아 조용히 무시된다(레거시의 if-else 무매칭과 동일).
    public static ApprovalAction fromCode(int code) {
        return Arrays.stream(values())
                .filter(action -> action.code == code)
                .findFirst()
                .orElse(null);
    }
}
