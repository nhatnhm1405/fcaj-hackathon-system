package com.seal.hackathon.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates FPT student IDs in the form {@code XXxxxxxx}.
 */
public final class FptStudentIdValidator {

    public static final String INVALID_MESSAGE =
            "FPT student ID must start with HE, SE, DE, QE, or CE followed by "
                    + "6 digits from 000000 to 229999.";

    private static final int MAX_NUMERIC_PART = 229_999;
    private static final Pattern FORMAT = Pattern.compile("^(HE|SE|DE|QE|CE)([0-9]{6})$");

    private FptStudentIdValidator() {
    }

    public static boolean isValid(String studentId) {
        if (studentId == null) {
            return false;
        }

        Matcher matcher = FORMAT.matcher(studentId);
        return matcher.matches()
                && Integer.parseInt(matcher.group(2)) <= MAX_NUMERIC_PART;
    }
}
