package com.ktds.portal.approval;

import java.util.Arrays;

/**
 * [리팩토링] 레거시 priority(int 1~3, 의미가 주석으로만 존재) → 여기서 enum으로 (매직넘버 제거)
 * DB 저장값은 기존 정수 그대로 유지한다 - ApprovalPriorityConverter(@Convert) 참고, @Enumerated 는 쓰지 않는다.
 */
public enum ApprovalPriority {
    LOW(1),
    NORMAL(2),
    HIGH(3);

    private final int code;

    ApprovalPriority(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    // [동작 보존] 정의되지 않은 코드(예: 5)가 와도 예외를 던지지 않고 null 을 반환한다(레거시는 검증 없이 그대로 저장했다).
    public static ApprovalPriority fromCode(int code) {
        return Arrays.stream(values())
                .filter(priority -> priority.code == code)
                .findFirst()
                .orElse(null);
    }
}
