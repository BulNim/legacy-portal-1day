package com.ktds.portal.approval;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [리팩토링] 레거시 status/type/priority(모두 int 매직넘버) → 각각 ApprovalStatus/ApprovalType/ApprovalPriority
 * enum 으로 교체했다. DB 컬럼은 여전히 기존 정수(status 0/1/2/3/9, type 1~4, priority 1~3) 그대로 저장한다
 * (@Convert + AttributeConverter, @Enumerated 는 사용하지 않음 - 이유는 각 Converter 클래스 주석 참고).
 * getStatus()/getType()/getPriority() 는 기존과 동일하게 int 를 반환·유지한다 - 특성화 테스트와 JSON 응답
 * 형식(레거시와 동일한 정수 필드)을 그대로 보존하기 위함이다. enum 이 필요한 내부 로직(ApprovalService)은
 * getStatusEnum()/getTypeEnum()/getPriorityEnum() 을 사용한다.
 *
 * [동작 보존] 정의되지 않은 코드(예: type=5)가 setType(5) 로 들어와도 예외를 던지지 않는다. enum 매핑이 안 되면
 * 내부적으로 null 이 되고, get 조회 시엔 0(미정의)을 반환한다 - 레거시가 검증 없이 그대로 저장/통과시키던 것을
 * "조용히 무시"로 근사 보존한다(정의된 값 0/1/2/3/9, 1~4, 1~3 에 대해서는 결과가 레거시와 완전히 동일하다).
 *
 * [스멜] 캡슐화 부재(모든 필드에 public setter)는 이번 라운드 범위 밖 - 차후 리팩토링 대상.
 */
@Entity
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;

    @Convert(converter = ApprovalTypeConverter.class)
    private ApprovalType type;       // 1=지출 2=휴가 3=구매 4=기타

    @Convert(converter = ApprovalStatusConverter.class)
    private ApprovalStatus status;   // 0=임시저장 1=상신 2=승인 3=반려 9=취소

    @Convert(converter = ApprovalPriorityConverter.class)
    private ApprovalPriority priority;   // 1=낮음 2=보통 3=높음

    private Long drafterId;     // 기안자
    private Long approverId;    // 결재자
    private String rejectReason;
    private long amount;        // 지출/구매 금액
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    // [계약 보존] 기존 호출부·JSON 응답·특성화 테스트가 int 를 그대로 쓸 수 있도록 시그니처를 유지한다.
    public int getType() { return type == null ? 0 : type.getCode(); }
    public void setType(int type) { this.type = ApprovalType.fromCode(type); }
    public ApprovalType getTypeEnum() { return type; }

    public int getStatus() { return status == null ? 0 : status.getCode(); }
    public void setStatus(int status) { this.status = ApprovalStatus.fromCode(status); }
    public ApprovalStatus getStatusEnum() { return status; }

    public int getPriority() { return priority == null ? 0 : priority.getCode(); }
    public void setPriority(int priority) { this.priority = ApprovalPriority.fromCode(priority); }
    public ApprovalPriority getPriorityEnum() { return priority; }

    public Long getDrafterId() { return drafterId; }
    public void setDrafterId(Long drafterId) { this.drafterId = drafterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
