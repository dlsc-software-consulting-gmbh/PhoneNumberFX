package com.dlsc.phonenumberfx;

import com.google.i18n.phonenumbers.NumberParseException;
import javafx.util.StringConverter;

/**
 * Converts parsing error types to human-readable text.
 */
public class ErrorTypeConverter extends StringConverter<NumberParseException.ErrorType> {

    @Override
    public String toString(NumberParseException.ErrorType errorType) {
        if (errorType != null) {
            switch (errorType) {
                case INVALID_COUNTRY_CODE:
                    return "Invalid country code";
                case NOT_A_NUMBER:
                    return "Invalid: not a number";
                case TOO_SHORT_AFTER_IDD:
                case TOO_SHORT_NSN:
                    return "Invalid: too short / not enough digits";
                case TOO_LONG:
                    return "Invalid: too long / too many digits";
            }
        }
        return null;
    }

    @Override
    public NumberParseException.ErrorType fromString(String string) {
        return null;
    }
}
