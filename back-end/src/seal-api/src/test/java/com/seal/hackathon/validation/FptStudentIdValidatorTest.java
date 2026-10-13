package com.seal.hackathon.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FptStudentIdValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "HE000000",
            "SE000001",
            "DE100000",
            "QE220000",
            "CE229999"
    })
    void isValid_shouldAcceptSupportedPrefixesAndNumericRange(String studentId) {
        assertTrue(FptStudentIdValidator.isValid(studentId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SE230000",
            "SE999999",
            "se123456",
            "AE123456",
            "SE12345",
            "SE1234567",
            "SE12A456",
            " SE123456",
            "SE123456 "
    })
    void isValid_shouldRejectUnsupportedFormatOrOutOfRangeValue(String studentId) {
        assertFalse(FptStudentIdValidator.isValid(studentId));
    }
}
