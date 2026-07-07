package com.ktds.portal.approval;

import java.util.Arrays;

/**
 * [리팩토링] 레거시 type(int 1~4, 의미가 주석으로만 존재) → 여기서 enum으로 (매직넘버 제거)
 * DB 저장값은 기존 정수 그대로 유지한다 - ApprovalTypeConverter(@Convert) 참고, @Enumerated 는 쓰지 않는다.
 */
public enum ApprovalType {
    EXPENSE(1),
    VACATION(2),
    PURCHASE(3),
    OTHER(4);

    private final int code;

    ApprovalType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    // [동작 보존] 정의되지 않은 코드(예: 5)가 와도 예외를 던지지 않고 null 을 반환한다(레거시는 검증 없이 그대로 저장했다).
    public static ApprovalType fromCode(int code) {
        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst()
                .orElse(null);
    }
}
