package com.ktds.portal.approval;

import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * [특성화 테스트] 리팩토링 전 안전망.
 * ApprovalService.processApproval(id, userId, action, reason) 의 "지금 레거시가 실제로 동작하는 방식"을
 * 관찰 가능한 값(Approval 엔티티 상태)으로 그대로 고정한다. 옳고 그름은 판단하지 않는다.
 * 리팩토링 후에도 이 테스트는 한 글자도 수정 없이 그대로 green 이어야 한다.
 *
 * action 코드: 1=상신, 2=승인, 3=반려, 9=취소
 * status 코드: 0=임시저장, 1=상신, 2=승인, 3=반려, 9=취소
 * role 코드: 1=사원, 2=팀장, 3=임원 (레거시 승인/반려 조건: role >= 2)
 */
@DataJpaTest
@Import(ApprovalService.class)
class ApprovalServiceCharacterizationTest {

    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private ApprovalRepository approvalRepository;
    @Autowired
    private UserRepository userRepository;

    private Long 기안자ID;
    private Long 팀장결재자ID;   // role=2 → 레거시 조건상 승인/반려 권한 있음
    private Long 사원결재자ID;   // role=1 → 결재자로 지정돼도 권한 없음(role>=2 미충족)

    @BeforeEach
    void 사용자_준비() {
        기안자ID = userRepository.save(new User("김기안", "drafter@ktds.com", 1, "개발팀")).getId();
        팀장결재자ID = userRepository.save(new User("이팀장", "leader@ktds.com", 2, "개발팀")).getId();
        사원결재자ID = userRepository.save(new User("박사원", "staff@ktds.com", 1, "개발팀")).getId();
    }

    private Long 임시저장_결재문서_생성(Long approverId) {
        Approval saved = approvalService.create("연차 신청", "내용", 2, 2, 기안자ID, approverId, 0L, false);
        return saved.getId();
    }

    @Test
    void 상신_상태에서_결재권한있는_사용자가_승인하면_승인상태로_바뀐다() {
        Long id = 임시저장_결재문서_생성(팀장결재자ID);
        approvalService.processApproval(id, 기안자ID, 1, null);       // 상신

        approvalService.processApproval(id, 팀장결재자ID, 2, null);   // 승인

        Approval result = approvalRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getRejectReason()).isNull();
    }

    @Test
    void 상신_상태에서_결재권한있는_사용자가_반려하면_반려상태로_바뀌고_사유가_저장된다() {
        Long id = 임시저장_결재문서_생성(팀장결재자ID);
        approvalService.processApproval(id, 기안자ID, 1, null);       // 상신

        approvalService.processApproval(id, 팀장결재자ID, 3, "예산 초과");   // 반려

        Approval result = approvalRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(3);
        assertThat(result.getRejectReason()).isEqualTo("예산 초과");
    }

    @Test
    void 기안자가_취소하면_취소상태로_바뀐다() {
        Long id = 임시저장_결재문서_생성(팀장결재자ID);
        approvalService.processApproval(id, 기안자ID, 1, null);       // 상신 (레거시: 취소는 s==0 또는 s==1 에서 허용)

        approvalService.processApproval(id, 기안자ID, 9, null);       // 취소

        Approval result = approvalRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(9);
    }

    @Test
    void 결재권한없는_사용자가_승인시도하면_조용히_무시되어_상신상태그대로다() {
        Long id = 임시저장_결재문서_생성(사원결재자ID);   // 결재자로 지정됐지만 role=1(사원)
        approvalService.processApproval(id, 기안자ID, 1, null);       // 상신

        approvalService.processApproval(id, 사원결재자ID, 2, null);   // 승인 시도 → role<2 라 조용히 무시(레거시 결함, 보존 대상)

        Approval result = approvalRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(1);   // 상신 상태 그대로 - 예외 없이 조용히 무시된다
    }

    @Test
    void 존재하지_않는_결재문서ID로_처리해도_예외없이_조용히_무시된다() {
        Long 없는ID = 999999L;

        assertDoesNotThrow(() -> approvalService.processApproval(없는ID, 기안자ID, 1, null));

        assertThat(approvalRepository.findById(없는ID)).isEmpty();
    }

    @Test
    void 이미_승인된_건을_다시_승인해도_승인상태와_처리시각이_그대로_유지된다() {
        Long id = 임시저장_결재문서_생성(팀장결재자ID);
        approvalService.processApproval(id, 기안자ID, 1, null);       // 상신
        approvalService.processApproval(id, 팀장결재자ID, 2, null);   // 1차 승인
        var 첫승인시각 = approvalRepository.findById(id).orElseThrow().getUpdatedAt();

        approvalService.processApproval(id, 팀장결재자ID, 2, null);   // 재승인 시도 → 이미 s==2 라 s==1 조건 불충족

        Approval result = approvalRepository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(2);                 // 승인 상태 그대로
        assertThat(result.getUpdatedAt()).isEqualTo(첫승인시각);      // 두 번째 save() 가 실행되지 않아 처리시각도 그대로
    }
}
