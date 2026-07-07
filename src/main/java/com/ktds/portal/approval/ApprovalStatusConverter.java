package com.ktds.portal.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 컬럼(0/1/2/3/9) 매핑.
 * @Enumerated(STRING/ORDINAL) 을 쓰지 않는 이유: STRING 은 컬럼값이 문자열로 바뀌어 레거시 데이터와 어긋나고,
 * ORDINAL 은 enum 선언 순서(0,1,2,3,4)를 저장해 레거시 값(9=취소)과 어긋난다. 반드시 code 기반 변환만 사용한다.
 */
@Converter
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer dbData) {
        // [동작 보존] DB 에 정의되지 않은 값이 있어도 예외를 던지지 않고 null 로 매핑한다.
        return dbData == null ? null : ApprovalStatus.fromCode(dbData);
    }
}
