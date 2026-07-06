# CLAUDE.md - legacy-portal

KT DS 「클린코드 & 리팩토링 실전」 과정의 관통 실습 프로젝트 (사내 업무 포털 - 결재/공지/일정).
이 파일은 **프로젝트의 사실(Facts)과 항상 지키는 코딩 규칙**만 담는다.
리팩토링 절차와 작업 방법은 `.claude/skills/refactor` Skill에서 관리한다.

## 빌드 / 실행 / 테스트
- 실행: `.\mvnw.cmd spring-boot:run`  (포트 8080)
- 테스트: `.\mvnw.cmd test`
- DB: MariaDB 있으면 사용, 없으면 자동 H2 인메모리
- Java 21 · Spring Boot 3.2 · JPA · JUnit 5

## 패키지 구조
- `approval`(결재) · `notice`(공지) · `schedule`(일정) · `user`(사용자) = 도메인(업무 폴더)
- 각 도메인 폴더 안 = Entity + Repository + Service (+ Controller)
- `common` = 공통 (메일·로그)

## 코딩 컨벤션 (항상 지킴)
- 의미 있는 코드값 = enum (매직넘버 금지)
- 협력 객체 = 직접 `new` 금지 → 생성자 주입
- Entity는 API에 직접 노출하지 않는다
- 이름 = 약어·한 글자 금지, 의미 있는 완전한 이름
- boolean = `is` / `has` 접두
