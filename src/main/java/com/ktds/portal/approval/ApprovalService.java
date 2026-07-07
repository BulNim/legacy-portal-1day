package com.ktds.portal.approval;

import com.ktds.portal.common.FileAuditLogger;
import com.ktds.portal.common.SmtpMailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRole;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================== [리팩토링 6장] 이번 라운드 변경 사항 ==============================
 * (1) 매직넘버 → enum   : status/type/priority(Approval), role(User), action(처리 구분)을 각각
 *                         ApprovalStatus/ApprovalType/ApprovalPriority/UserRole/ApprovalAction 으로 교체.
 *                         DB 저장값은 @Convert + AttributeConverter 로 기존 정수 그대로 유지(@Enumerated 미사용).
 * (2) 약어 이름 → 완전한 이름 : d→approval, u→user, s→currentStatus, proc→requestedAction, tmp→label, a→amount.
 * (3) 동작 보존         : 정의되지 않은 코드(예: type=5, action=7)가 와도 IllegalArgumentException 등 예외를
 *                         던지지 않는다. enum 매핑이 null 이 되어 어떤 분기 조건에도 해당하지 않으므로
 *                         레거시의 "조용히 무시" 동작이 그대로 재현된다.
 *                         → ApprovalServiceCharacterizationTest 는 한 글자도 수정하지 않고 그대로 green.
 * ================================================================================================
 *
 * ==================== 이번 라운드에서 다루지 않은 나머지 스멜(차후 리팩토링 대상) ====================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 여전히 혼자 다 한다.
 *  2. Long Method          : processApproval() 은 여전히 하나의 메서드 안에서 4개 액션을 분기한다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 여전히 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 여전히 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 여전히 직접 상태/금액 규칙을 계산한다.
 *  8. Long Parameter List  : create() 파라미터 8개, 그대로.
 * 11. No Tests             : Approval 은 특성화 테스트가 생겼지만 Notice/Schedule 은 여전히 없다.
 * =================================================================================
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다. (이번 라운드 범위 밖)
    private final SmtpMailSender mail = new SmtpMailSender();
    private final FileAuditLogger audit = new FileAuditLogger();

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    // [스멜8] 파라미터 8개(이번 라운드 범위 밖). [계약 보존] 시그니처는 그대로 유지한다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.setTitle(title);
        approval.setContent(content);
        // [동작 보존] 정의되지 않은 type 코드가 와도 예외 없이 null 로 저장된다(레거시는 검증 없이 그대로 저장했다).
        approval.setType(type);
        // [매직넘버 → enum] urgent 면 HIGH, 아니면 요청받은 priority 코드를 그대로 변환한다.
        approval.setPriority(urgent ? ApprovalPriority.HIGH.getCode() : priority);
        approval.setStatus(ApprovalStatus.DRAFT.getCode());
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다(이번 라운드 범위 밖).
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + now + "] APPROVAL CREATE id=" + approval.getId()
                + " by=" + drafterId + " type=" + approval.getType();
        audit.write(line);
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     * [스멜2] 이 메서드 하나가 모든 일을 한다(이번 라운드 범위 밖). [계약 보존] 시그니처는 그대로 유지한다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소 (ApprovalAction 참고)
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        Approval approval = repo.findById(id).orElse(null);
        if (approval == null) {
            // [스멜] 예외 대신 조용히 리턴 — 호출자는 실패를 알 수 없다(레거시 결함, 보존 대상).
            return;
        }
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        ApprovalStatus currentStatus = approval.getStatusEnum();
        // [동작 보존] 정의되지 않은 action 코드는 null 이 되어 아래 어떤 분기에도 해당하지 않는다(조용히 무시).
        ApprovalAction requestedAction = ApprovalAction.fromCode(action);

        if (requestedAction == ApprovalAction.SUBMIT) {
            // 상신: 임시저장 상태일 때만 가능
            if (currentStatus == ApprovalStatus.DRAFT) {
                // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다(이번 라운드 범위 밖).
                if (approval.getTypeEnum() == ApprovalType.EXPENSE && approval.getAmount() >= 1000000) {
                    approval.setPriority(ApprovalPriority.HIGH.getCode());
                }
                approval.setStatus(ApprovalStatus.SUBMITTED.getCode());
                approval.setUpdatedAt(LocalDateTime.now());
                repo.save(approval);
                // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙(이번 라운드 범위 밖).
                User approver = userRepo.findById(approval.getApproverId()).orElse(null);
                if (approver != null) {
                    String body = "안녕하세요 " + approver.getName() + "님,\n"
                            + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                            + "\n기안자ID: " + approval.getDrafterId();
                    mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
                }
                writeAudit("APPROVAL SUBMIT", approval.getId(), userId);
            }
        } else if (requestedAction == ApprovalAction.APPROVE) {
            // 승인: 상신 상태 + 본인이 결재자 + 결재 권한(팀장 이상)일 때만
            if (currentStatus == ApprovalStatus.SUBMITTED) {
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (hasApprovalAuthority(user)) {
                        approval.setStatus(ApprovalStatus.APPROVED.getCode());
                        approval.setUpdatedAt(LocalDateTime.now());
                        repo.save(approval);
                        // [스멜4] 또 복붙된 메일 발송(이번 라운드 범위 밖)
                        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
                            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
                        }
                        writeAudit("APPROVAL APPROVE", approval.getId(), userId);
                    }
                }
            }
        } else if (requestedAction == ApprovalAction.REJECT) {
            // 반려: 상신 상태 + 본인이 결재자 + 결재 권한(팀장 이상)일 때만
            if (currentStatus == ApprovalStatus.SUBMITTED) {
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (hasApprovalAuthority(user)) {
                        approval.setStatus(ApprovalStatus.REJECTED.getCode());
                        approval.setRejectReason(reason);
                        approval.setUpdatedAt(LocalDateTime.now());
                        repo.save(approval);
                        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                                    + "\n사유: " + reason;
                            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), body);
                        }
                        writeAudit("APPROVAL REJECT", approval.getId(), userId);
                    }
                }
            }
        } else if (requestedAction == ApprovalAction.CANCEL) {
            // 취소: 기안자 본인 + 아직 승인 전(임시저장 또는 상신)
            if (currentStatus == ApprovalStatus.DRAFT || currentStatus == ApprovalStatus.SUBMITTED) {
                if (approval.getDrafterId() != null && approval.getDrafterId().equals(userId)) {
                    approval.setStatus(ApprovalStatus.CANCELED.getCode());
                    approval.setUpdatedAt(LocalDateTime.now());
                    repo.save(approval);
                    writeAudit("APPROVAL CANCEL", approval.getId(), userId);
                }
            }
        }
    }

    // [매직넘버 → 도메인 메서드] 레거시의 "role >= 2" 판정을 UserRole.hasApprovalAuthority() 로 위임한다.
    // [동작 보존] role 이 정의되지 않아 null 이면 권한 없음으로 조용히 처리한다(예외 없음).
    private boolean hasApprovalAuthority(User user) {
        UserRole role = user.getRoleEnum();
        return role != null && role.hasApprovalAuthority();
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거, 이번 라운드 범위 밖).
    private void writeAudit(String act, Long id, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        audit.write("[" + now + "] " + act + " id=" + id + " by=" + userId);
    }

    // [스멜1] 화면 표시용 문자열까지 서비스가 만든다(이번 라운드 범위 밖). 라벨 자체는 ApprovalStatus enum 으로 이동했다.
    public String statusLabel(Approval approval) {
        ApprovalStatus status = approval.getStatusEnum();
        // [동작 보존] 정의되지 않은 상태 코드는 레거시와 동일하게 "알수없음"을 반환한다.
        return status == null ? "알수없음" : status.getLabel();
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산한다(이번 라운드 범위 밖).
    public String amountGrade(Approval approval) {
        long amount = approval.getAmount();
        if (amount >= 10000000) return "S";   // 1000만원=S
        else if (amount >= 1000000) return "A";   // 100만원=A
        else if (amount >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
