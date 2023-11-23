package com.dlsc.phonenumberfx;

import com.dlsc.phonenumberfx.PhoneNumberField.Country;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.scene.control.*;

import java.util.Locale;
import java.util.Objects;

/**
 * A control for displaying a phone number in a formatted way based on a raw number string.
 * 
 * @see #setRawPhoneNumber(String)
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

    /**
     * Builds a new phone number field with the default settings. The available country
     * calling codes are defined on {@link Country}.
     */
    public PhoneNumberLabel() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getStylesheets().add(Objects.requireNonNull(PhoneNumberLabel.class.getResource("phone-number-label.css")).toExternalForm());

        validProperty().addListener((obs, oldV, newV) -> pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !newV));

        rawPhoneNumber.addListener((obs, oldRawPhoneNumber, newRawPhoneNumber) -> {
            try {
                if (getCountry() != null) {
                    Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(newRawPhoneNumber, getCountry().iso2Code());

                    e164PhoneNumber.set(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164));
                    nationalPhoneNumber.set(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));

                    switch (getStrategy()) {
                        case NATIONAL_FOR_DEFAULT_COUNTRY_ONLY:
                            if (phoneNumber.getCountryCode() == getCountry().countryCode()) {
                                setText(getNationalPhoneNumber());
                            } else {
                                setText(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL));
                            }
                            break;
                        case ALWAYS_DISPLAY_INTERNATIONAL:
                            setText(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL));
                            break;
                        case ALWAYS_DISPLAY_NATIONAL:
                            setText(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                            break;
                    }
                    setTooltip(new Tooltip(phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)));
                } else {
                    setText(getRawPhoneNumber());
                    setTooltip(new Tooltip(getRawPhoneNumber()));
                    e164PhoneNumber.set("");
                    nationalPhoneNumber.set("");
                }

                setValid(true);
            } catch (NumberParseException e) {
                if (newRawPhoneNumber != null && newRawPhoneNumber.startsWith(getCountry().countryCodePrefix())) {
                    newRawPhoneNumber = newRawPhoneNumber.substring(getCountry().countryCodePrefix().length());
                }
                e164PhoneNumber.set("");
                nationalPhoneNumber.set("");
                setText(newRawPhoneNumber);
                setValid(false);
            }
        });

        System.out.println("default country: " + getCountry());

        validProperty().addListener(it -> System.out.println("valid: " + isValid()));
    }

    public enum Strategy {
        NATIONAL_FOR_DEFAULT_COUNTRY_ONLY,
        ALWAYS_DISPLAY_INTERNATIONAL,
        ALWAYS_DISPLAY_NATIONAL,
    }

    private final ObjectProperty<Strategy> strategy = new SimpleObjectProperty<>(this, "strategy", Strategy.NATIONAL_FOR_DEFAULT_COUNTRY_ONLY);

    public final Strategy getStrategy() {
        return strategy.get();
    }

    public final ObjectProperty<Strategy> strategyProperty() {
        return strategy;
    }

    public final void setStrategy(Strategy strategy) {
        this.strategy.set(strategy);
    }

    private final StringProperty rawPhoneNumber = new SimpleStringProperty(this, "rawPhoneNumber");

    /**
     * @return The raw phone number corresponding exactly to what the user typed in, including the (+) sign appended at the
     * beginning. This value can be a valid E164 formatted number.
     */
    public final StringProperty rawPhoneNumberProperty() {
        return rawPhoneNumber;
    }

    public final String getRawPhoneNumber() {
        return rawPhoneNumberProperty().get();
    }

    public final void setRawPhoneNumber(String rawPhoneNumber) {
        rawPhoneNumberProperty().set(rawPhoneNumber);
    }

    private final ObjectProperty<Country> country = new SimpleObjectProperty<>(this, "country", Country.ofISO2(Locale.getDefault().getCountry()));

    public final Country getCountry() {
        return country.get();
    }

    public final ObjectProperty<Country> countryProperty() {
        return country;
    }

    public final void setCountry(Country country) {
        this.country.set(country);
    }

    private final ReadOnlyStringWrapper nationalPhoneNumber = new ReadOnlyStringWrapper(this, "nationalPhoneNumber");

    public final ReadOnlyStringProperty nationalPhoneNumberProperty() {
        return nationalPhoneNumber.getReadOnlyProperty();
    }

    public final String getNationalPhoneNumber() {
        return nationalPhoneNumber.get();
    }

    private void setNationalPhoneNumber(String nationalPhoneNumber) {
        this.nationalPhoneNumber.set(nationalPhoneNumber);
    }

    private final ReadOnlyStringWrapper e164PhoneNumber = new ReadOnlyStringWrapper(this, "e164PhoneNumber");

    public final ReadOnlyStringProperty e164PhoneNumberProperty() {
        return e164PhoneNumber.getReadOnlyProperty();
    }

    public final String getE164PhoneNumber() {
        return e164PhoneNumber.get();
    }

    private void setE164PhoneNumber(String e164PhoneNumber) {
        this.e164PhoneNumber.set(e164PhoneNumber);
    }

    // SETTINGS

    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(this, "valid") {
        @Override
        public void set(boolean newValid) {
            super.set(newValid);
            pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !newValid);
        }
    };

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
