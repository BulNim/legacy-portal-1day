package com.ktds.portal.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] UserRole enum ↔ DB 정수 컬럼(1~3) 매핑. @Enumerated(STRING/ORDINAL) 대신 code 기반 변환만 사용한다
 * (STRING 은 문자열 저장으로 레거시 값과 어긋나고, ORDINAL 은 선언 순서를 저장해 값이 우연히 맞을 뿐 안전하지 않다).
 */
@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, Integer> {

    @Override
    public Integer convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public UserRole convertToEntityAttribute(Integer dbData) {
        // [동작 보존] DB 에 정의되지 않은 값이 있어도 예외를 던지지 않고 null 로 매핑한다.
        return dbData == null ? null : UserRole.fromCode(dbData);
    }
}
