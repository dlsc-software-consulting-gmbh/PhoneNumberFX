package com.dlsc.phonenumberfx;

import com.dlsc.phonenumberfx.PhoneNumberField.Country;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.Objects;

/**
 * A control for displaying a phone number in a formatted way based on a raw number string.
 *
 * @see #setValue(String)
 */
public class PhoneNumberLabel extends Label {

    /**
     * Pseudo class used to visualize the validity of the control.
     */
    public static final PseudoClass INVALID_PSEUDO_CLASS = PseudoClass.getPseudoClass("invalid");

    /**
     * Default style class for css styling.
     */
    public static final String DEFAULT_STYLE_CLASS = "phone-number-label";

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    private boolean updatingText;

    /**
     * Builds a new phone number field with the default settings. The available country
     * calling codes are defined on {@link Country}.
     */
    public PhoneNumberLabel() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getStylesheets().add(Objects.requireNonNull(PhoneNumberLabel.class.getResource("phone-number-label.css")).toExternalForm());

        validProperty().addListener((obs, oldV, newV) -> pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !newV));

        textProperty().addListener(it -> {
            if (!updatingText) {
                // somebody called setText(...) instead of setRawPhoneNumber(...)
                setValue(getText());
            }
        });

        InvalidationListener updateListener = it -> {
            String newRawPhoneNumber = getValue();

            updatingText = true;
            errorType.set(null);

            Country country = getCountry();
            if (country == null) {
                country = Country.ofDefaultLocale();
            }

            try {
                Phonenumber.PhoneNumber phoneNumber;

                e164PhoneNumber.set(null);
                nationalPhoneNumber.set(null);
                internationalPhoneNumber.set(null);

                phoneNumber = phoneNumberUtil.parse(newRawPhoneNumber, country.iso2Code());

                e164PhoneNumber.set(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
                nationalPhoneNumber.set(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                internationalPhoneNumber.set(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL));

                switch (getStrategy()) {
                    case NATIONAL_FOR_OWN_COUNTRY_ONLY:
                        if (phoneNumber.getCountryCode() == country.countryCode()) {
                            setText(getNationalPhoneNumber());
                        } else {
                            setText(getInternationalPhoneNumber());
                        }
                        break;
                    case ALWAYS_DISPLAY_INTERNATIONAL:
                        setText(getInternationalPhoneNumber());
                        break;
                    case ALWAYS_DISPLAY_NATIONAL:
                        setText(getNationalPhoneNumber());
                        break;
                }

                setTooltip(new Tooltip(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)));
                setValid(phoneNumberUtil.isValidNumber(phoneNumber));
            } catch (NumberParseException e) {
                ErrorType errorType = e.getErrorType();
                if (errorType != null) {
                    this.errorType.set(errorType);
                    setTooltip(new Tooltip(getErrorTypeConverter().toString(errorType)));
                } else {
                    setTooltip(new Tooltip(newRawPhoneNumber));
                }

                if (newRawPhoneNumber != null && newRawPhoneNumber.startsWith(country.countryCodePrefix())) {
                    newRawPhoneNumber = newRawPhoneNumber.substring(country.countryCodePrefix().length());
                }

                setText(newRawPhoneNumber);
                setValid(false);
            } finally {
                updatingText = false;
            }
        };

        countryProperty().addListener(updateListener);
        value.addListener(updateListener);

        validProperty().addListener(it -> updateValidPseudoState());
        updateValidPseudoState();
    }

    private void updateValidPseudoState() {
        pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !isValid());
    }

    // STRING CONVERTER

    private final ObjectProperty<StringConverter<ErrorType>> errorTypeConverter = new SimpleObjectProperty<>(this, "errorTypeConverter", new StringConverter<>() {

        @Override
        public String toString(ErrorType errorType) {
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
        public ErrorType fromString(String string) {
            return null;
        }
    });

    public StringConverter<ErrorType> getErrorTypeConverter() {
        return errorTypeConverter.get();
    }

    /**
     * Returns the property that represents the converter for the error type.
     * The converter is used to convert the error type to a string for display purposes.
     *
     * @return the property that represents the converter for the error type
     */
    public ObjectProperty<StringConverter<ErrorType>> errorTypeConverterProperty() {
        return errorTypeConverter;
    }

    public void setErrorTypeConverter(StringConverter<ErrorType> errorTypeConverter) {
        this.errorTypeConverter.set(errorTypeConverter);
    }

    // ERROR TYPE

    private final ReadOnlyObjectWrapper<ErrorType> errorType = new ReadOnlyObjectWrapper<>(this, "errorType");

    public final ErrorType getErrorType() {
        return errorType.get();
    }

    /**
     * Returns the error type property of the phone number field. The error type
     * represents the type of error that occurred during the parsing of the phone
     * number.
     *
     * @return the error type property
     */
    public final ReadOnlyObjectProperty<ErrorType> errorTypeProperty() {
        return errorType.getReadOnlyProperty();
    }

    /**
     * Enumeration representing different strategies for displaying phone numbers.
     *
     * @see #setStrategy(Strategy)
     */
    public enum Strategy {

        /**
         * This value causes the label to show the phone number in a format appropriate
         * for the country where the application is being used, while numbers from other
         * countries will contain the countries prefix.
         */
        NATIONAL_FOR_OWN_COUNTRY_ONLY,

        /**
         * This value causes the label to always show the phone number in international
         * format including the country code prefix.
         */
        ALWAYS_DISPLAY_INTERNATIONAL,

        /**
         * This value causes the label to always show the phone number in the national
         * format, without the country code prefix.
         */
        ALWAYS_DISPLAY_NATIONAL
    }

    // STRATEGY

    private final ObjectProperty<Strategy> strategy = new SimpleObjectProperty<>(this, "strategy", Strategy.NATIONAL_FOR_OWN_COUNTRY_ONLY);

    public final Strategy getStrategy() {
        return strategy.get();
    }

    /**
     * A property used to determine how the label will display the given phone number.
     */
    public final ObjectProperty<Strategy> strategyProperty() {
        return strategy;
    }

    public final void setStrategy(Strategy strategy) {
        this.strategy.set(strategy);
    }

    //  PHONE NUMBER

    private final StringProperty value = new SimpleStringProperty(this, "value");

    /**
     * The text corresponding exactly to what the user typed in, including the (+) sign at the
     * beginning. This value can be a valid E164 formatted number. The label will do its best to properly format the
     * given number.
     */
    public final StringProperty valueProperty() {
        return value;
    }

    public final String getValue() {
        return valueProperty().get();
    }

    public final void setValue(String value) {
        valueProperty().set(value);
    }

    // COUNTRY

    private final ObjectProperty<Country> country = new SimpleObjectProperty<>(this, "country", Country.ofISO2(Locale.getDefault().getCountry()));

    public final Country getCountry() {
        return country.get();
    }

    /**
     * The country where the field gets used. This property will be initially populated via
     * a lookup of the default locale.
     */
    public final ObjectProperty<Country> countryProperty() {
        return country;
    }

    public final void setCountry(Country country) {
        this.country.set(country);
    }

    // NATIONAL PHONE NUMBER

    private final ReadOnlyStringWrapper nationalPhoneNumber = new ReadOnlyStringWrapper(this, "nationalPhoneNumber");

    /**
     * A representation of the phone number in the national format of the specified country.
     */
    public final ReadOnlyStringProperty nationalPhoneNumberProperty() {
        return nationalPhoneNumber.getReadOnlyProperty();
    }

    public final String getNationalPhoneNumber() {
        return nationalPhoneNumber.get();
    }

    private void setNationalPhoneNumber(String nationalPhoneNumber) {
        this.nationalPhoneNumber.set(nationalPhoneNumber);
    }

    // NATIONAL PHONE NUMBER

    private final ReadOnlyStringWrapper internationalPhoneNumber = new ReadOnlyStringWrapper(this, "internationalPhoneNumber");

    /**
     * A representation of the phone number in the national format of the specified country.
     */
    public final ReadOnlyStringProperty internationalPhoneNumberProperty() {
        return internationalPhoneNumber.getReadOnlyProperty();
    }

    public final String getInternationalPhoneNumber() {
        return internationalPhoneNumber.get();
    }

    private void setInternationalPhoneNumber(String nationalPhoneNumber) {
        this.internationalPhoneNumber.set(nationalPhoneNumber);
    }

    // E164 PHONE NUMBER

    private final ReadOnlyStringWrapper e164PhoneNumber = new ReadOnlyStringWrapper(this, "e164PhoneNumber");

    /**
     * A representation of the phone number in the international standard format E164.
     */
    public final ReadOnlyStringProperty e164PhoneNumberProperty() {
        return e164PhoneNumber.getReadOnlyProperty();
    }

    public final String getE164PhoneNumber() {
        return e164PhoneNumber.get();
    }

    private void setE164PhoneNumber(String e164PhoneNumber) {
        this.e164PhoneNumber.set(e164PhoneNumber);
    }

    // VALIDITY FLAG

    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(this, "valid");

    /**
     * Read-only property that indicates whether the phone number is valid or not.
     */
    public final ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }

    public final boolean isValid() {
        return valid.get();
    }

    private void setValid(boolean valid) {
        this.valid.set(valid);
    }
}
