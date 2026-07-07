package com.ktds.portal.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalType enum ↔ DB 정수 컬럼(1~4) 매핑. @Enumerated 미사용(사유는 ApprovalStatusConverter 참고).
 */
@Converter
public class ApprovalTypeConverter implements AttributeConverter<ApprovalType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ApprovalType convertToEntityAttribute(Integer dbData) {
        // [동작 보존] DB 에 정의되지 않은 값이 있어도 예외를 던지지 않고 null 로 매핑한다.
        return dbData == null ? null : ApprovalType.fromCode(dbData);
    }
}
