package com.ktds.portal.user;

import jakarta.persistence.*;

/**
 * 사용자 엔티티.
 *
 * [리팩토링] 레거시 role(int 1=사원·2=팀장·3=임원, 의미가 주석으로만 존재) → UserRole enum 으로 교체했다.
 * DB 컬럼은 여전히 기존 정수 그대로 저장한다(@Convert + UserRoleConverter, @Enumerated 는 사용하지 않음).
 * 생성자와 getRole()/setRole(int) 는 기존과 동일하게 int 를 그대로 주고받는다 - 다른 도메인(NoticeService 등)
 * 이 여전히 "role >= 2" 형태로 정수 비교를 하고 있어 계약을 바꾸지 않기 위함이다. enum 이 필요한 내부 로직
 * (ApprovalService 의 권한 판정)은 getRoleEnum() 을 사용한다.
 *
 * [동작 보존] 정의되지 않은 role 값(예: 5)이 들어와도 예외를 던지지 않는다. enum 매핑이 안 되면 내부적으로
 * null 이 되고, getRole() 조회 시엔 0(미정의)을 반환한다 - 레거시가 검증 없이 그대로 저장/통과시키던 것을
 * "조용히 무시"로 근사 보존한다(정의된 값 1/2/3 에 대해서는 결과가 레거시와 완전히 동일하다).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @Convert(converter = UserRoleConverter.class)
    private UserRole role;      // 1=사원 2=팀장 3=임원

    private String dept;

    public User() {}

    public User(String name, String email, int role, String dept) {
        this.name = name;
        this.email = email;
        this.role = UserRole.fromCode(role);
        this.dept = dept;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // [계약 보존] 기존 호출부·JSON 응답이 int 를 그대로 쓸 수 있도록 시그니처를 유지한다.
    public int getRole() { return role == null ? 0 : role.getCode(); }
    public void setRole(int role) { this.role = UserRole.fromCode(role); }
    public UserRole getRoleEnum() { return role; }

    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
}
