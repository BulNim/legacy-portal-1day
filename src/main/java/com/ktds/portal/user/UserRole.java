package com.ktds.portal.user;

import java.util.Arrays;

/**
 * [리팩토링] 레거시 role(int 1~3, 의미가 주석으로만 존재) → 여기서 enum으로 (매직넘버 제거)
 * DB 저장값은 기존 정수 그대로 유지한다 - UserRoleConverter(@Convert) 참고, @Enumerated 는 쓰지 않는다.
 * hasApprovalAuthority() 는 ApprovalService 곳곳에 흩어져 있던 "role >= 2" 매직넘버 비교를 도메인으로 모은 것이다.
 */
public enum UserRole {
    STAFF(1),
    TEAM_LEAD(2),
    EXECUTIVE(3);

    private final int code;

    UserRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    // [매직넘버 → 도메인 메서드] 레거시의 "role >= 2" (팀장 이상 결재 권한) 조건을 그대로 옮긴 것 - 판정 기준 자체는 바꾸지 않았다.
    public boolean hasApprovalAuthority() {
        return code >= 2;
    }

    // [동작 보존] 정의되지 않은 코드(예: 5)가 와도 예외를 던지지 않고 null 을 반환한다(레거시는 검증 없이 그대로 저장했다).
    public static UserRole fromCode(int code) {
        return Arrays.stream(values())
                .filter(role -> role.code == code)
                .findFirst()
                .orElse(null);
    }
}
