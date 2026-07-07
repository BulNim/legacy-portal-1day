package com.ktds.portal.approval;

import java.util.Arrays;

/**
 * [리팩토링] 레거시 status(int 0/1/2/3/9, 의미가 주석으로만 존재) → 여기서 enum으로 (매직넘버 제거)
 * DB 저장값은 기존 정수 그대로 유지한다 - ApprovalStatusConverter(@Convert) 참고, @Enumerated 는 쓰지 않는다.
 */
public enum ApprovalStatus {
    DRAFT(0, "임시저장"),
    SUBMITTED(1, "상신"),
    APPROVED(2, "승인"),
    REJECTED(3, "반려"),
    CANCELED(9, "취소");

    private final int code;
    private final String label;

    ApprovalStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    // [동작 보존] 정의되지 않은 코드(예: 5)가 와도 예외를 던지지 않고 null 을 반환한다.
    // 레거시는 검증 없이 숫자를 그대로 저장/통과시켰다 - 그 결과를 "조용히 무시"로 근사 보존한다.
    public static ApprovalStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElse(null);
    }
}
