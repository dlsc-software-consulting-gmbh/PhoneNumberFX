package com.dlsc.phonenumberfx;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.controlsfx.control.textfield.CustomTextField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * A control for entering phone numbers. The control supports a list of {@link #getAvailableCountries() available countries}
 * and a list of {@link #getPreferredCountries() preferred countries}. To set a phone number the application has to invoke
 * the {@link #setValue(String)} method.
 *
 * @see #getE164PhoneNumber()
 * @see #getNationalPhoneNumber()
 * @see #getInternationalPhoneNumber()
 * @see #getPhoneNumber()
 */
public class PhoneNumberField extends CustomTextField {

    private static final Map<Country, Image> FLAG_IMAGES = new HashMap<>();

    static {
        for (Country country : Country.values()) {
            FLAG_IMAGES.put(country, new Image(Objects.requireNonNull(PhoneNumberField.class.getResource("country-flags/" + country.iso2Code().toLowerCase() + ".png")).toExternalForm()));
        }
    }

    private static final Comparator<Country> NAME_SORT_ASC = (c1, c2) -> {
        String c1Name = c1.countryName();
        String c2Name = c2.countryName();
        return c1Name.compareTo(c2Name);
    };

    /**
     * Pseudo class used to visualize the validity of the control.
     */
    public static final PseudoClass INVALID_PSEUDO_CLASS = PseudoClass.getPseudoClass("invalid");

    /**
     * Default style class for css styling.
     */
    public static final String DEFAULT_STYLE_CLASS = "phone-number-field";

    private final PhoneNumberUtil phoneNumberUtil;
    private final ComboBox<Country> comboBox;
    private final ObservableList<Country> countries = FXCollections.observableArrayList();

    /**
     * Constructs a new phone number field with no initial phone number.
     */
    public PhoneNumberField() {
        this(null);
    }

    /**
     * Constructs a new phone number field showing the specified initial phone number.
     *
     * @param value the "model" / the raw number ideally in form of a complete e164 number (e.g. "+12024561111" for the White House)
     */
    public PhoneNumberField(String value) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getAvailableCountries().setAll(Country.values());

        phoneNumberUtil = PhoneNumberUtil.getInstance();

        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.BACK_SPACE
                    && (getText() == null || getText().isEmpty())
                    && getSelectedCountry() != null
                    && isCountryCodeVisible()
                    && !getDisableCountryDropdown()) {

                // Clear the country if the user deletes the entire text
                clear();
                e.consume();
            }
        });

        Label countryCodePrefixLabel = new Label();
        countryCodePrefixLabel.getStyleClass().add("country-code-prefix-label");
        countryCodePrefixLabel.textProperty().bind(Bindings.createStringBinding(() -> getSelectedCountry() != null ? getSelectedCountry().countryCodePrefix() : null, selectedCountryProperty()));
        countryCodePrefixLabel.visibleProperty().bind(countryCodePrefixLabel.textProperty().isNotEmpty().and(countryCodeVisibleProperty()));
        countryCodePrefixLabel.managedProperty().bind(countryCodePrefixLabel.textProperty().isNotEmpty().and(countryCodeVisibleProperty()));
        countryCodePrefixLabel.setMaxHeight(Double.MAX_VALUE);
        countryCodePrefixLabel.setMinWidth(Region.USE_PREF_SIZE);

        comboBox = new ComboBox<>() {
            @Override
            protected Skin<?> createDefaultSkin() {
                return new ComboBoxListViewSkin<>(this) {

                    final Region globeRegion = new Region();
                    final StackPane globeButton = new StackPane(globeRegion);

                    {
                        globeRegion.getStyleClass().add("globe");
                        globeButton.getStyleClass().add("globe-button");
                        globeButton.setOnMouseClicked(evt -> getSkinnable().show());
                        getChildren().add(globeButton);
                    }

                    @Override
                    protected void layoutChildren(double x, double y, double w, double h) {
                        super.layoutChildren(x, y, w, h);

                        Node displayNode = getDisplayNode();
                        Bounds bounds = displayNode.getBoundsInParent();

                        // use the same bounds for the globe that were computed for the button cell
                        globeButton.resizeRelocate(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
                        globeButton.setVisible(getSkinnable().getValue() == null);
                        globeButton.setManaged(getSkinnable().getValue() == null);
                        globeButton.toFront();
                    }
                };
            }
        };

        comboBox.cellFactoryProperty().bind(countryCellFactoryProperty());
        comboBox.setItems(countries);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setMaxHeight(Double.MAX_VALUE);
        comboBox.setFocusTraversable(false);
        comboBox.disableProperty().bind(disableCountryDropdownProperty().or(editableProperty().not()));
        comboBox.valueProperty().bindBidirectional(selectedCountryProperty());
        comboBox.visibleProperty().bind(showCountryDropdownProperty());
        comboBox.managedProperty().bind(showCountryDropdownProperty());
        comboBox.buttonCellProperty().bind(Bindings.createObjectBinding(this::getButtonCell, buttonCellProperty(), valueProperty()));

        HBox leftBox = new HBox(comboBox, countryCodePrefixLabel);
        leftBox.getStyleClass().add("left-box");
        leftBox.setMaxWidth(Region.USE_PREF_SIZE);

        setLeft(leftBox);

        addEventFilter(KeyEvent.KEY_PRESSED, evt -> {
            if (evt.getCode().isLetterKey()) {
                String letter = evt.getCode().getChar();

                countries.stream()
                        .filter(c -> c.countryName().startsWith(letter))
                        .findFirst().ifPresent(this::showCountry);

            } else if (evt.getCode().equals(KeyCode.DOWN)) {
                showCountry(null);
            }
        });

        InvalidationListener updatePromptListener = it -> updatePromptTextWithExampleNumber();
        showExampleNumbersProperty().addListener(updatePromptListener);
        expectedPhoneNumberTypeProperty().addListener(updatePromptListener);
        selectedCountryProperty().addListener(updatePromptListener);
        countryCodeVisibleProperty().addListener(updatePromptListener);
        missingCountryPromptTextProperty().addListener(updatePromptListener);

        updatePromptTextWithExampleNumber();

        showCountryDropdownProperty().addListener(it -> requestLayout()); // important
        countryCodeVisibleProperty().addListener(it -> requestLayout()); // important

        valueProperty().addListener((obs, oldV, newV) -> {
            phoneNumber.set(null);
            nationalPhoneNumber.set(null);
            internationalPhoneNumber.set(null);
            e164PhoneNumber.set(null);
            valid.set(false);
            parsingErrorType.set(null);

            if (newV != null && !newV.isEmpty()) {
                String regionCode = getRegionCode(newV);

                try {
                    Phonenumber.PhoneNumber number = phoneNumberUtil.parse(newV, regionCode);
                    phoneNumber.set(number);
                    e164PhoneNumber.set(phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
                    nationalPhoneNumber.set(phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                    internationalPhoneNumber.set(phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL));
                    valid.set(phoneNumberUtil.isValidNumber(number) && (isExpectedTypeMatch(number) || !isValidityCheckIncludesTypeCheck()));
                    setTooltip(new Tooltip(getE164PhoneNumber()));
                } catch (NumberParseException e) {
                    parsingErrorType.set(e.getErrorType());
                }
            }
        });

        parsingErrorTypeProperty().addListener(it -> {
            ErrorType error = getParsingErrorType();
            if (error != null) {
                StringConverter<ErrorType> converter = getErrorTypeConverter();
                if (converter != null) {
                    setTooltip(new Tooltip(converter.toString(error)));
                } else {
                    setTooltip(new Tooltip(error.toString()));
                }
            }
        });

        validProperty().addListener((obs, oldV, newV) -> pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !newV));

        countryCellFactory.addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                setCountryCellFactory(oldValue);
                throw new IllegalArgumentException("country cell factory can not be null");
            }
        });

        InvalidationListener updateCallingCodes = it -> updateCountryList();

        getAvailableCountries().addListener(updateCallingCodes);
        getPreferredCountries().addListener(updateCallingCodes);
        countryCellFactoryProperty().addListener(updateCallingCodes);

        updateCountryList();

        validProperty().addListener(it -> updateValidPseudoState());
        updateValidPseudoState();

        final UnaryOperator<TextFormatter.Change> filter = c -> {
            String text = c.getText();

            if (text.equals(c.getControlText())) {
                // no change means all good
                return c;
            }

            if (!text.isEmpty()) {
                String controlNewText = c.getControlNewText();
                if (controlNewText.startsWith("+")) {
                    if (controlNewText.length() > 1) {
                        Country country = Country.ofCountryCodePrefix(controlNewText);
                        if (country != null) {
                            Platform.runLater(() -> {
                                setText("");
                                setSelectedCountry(country);
                            });
                        }
                    }

                    return c;
                }

                char ch = text.charAt(0);
                if (Character.isDigit(ch) || ch == '(' || ch == ')' || ch == '-' || ch == '.' || ch == ' ') {
                    return c;
                }

                return null;
            }

            return c;
        };

        StringConverter<String> converter = new StringConverter<>() {

            @Override
            public String toString(String value) {
                if (value != null && !value.trim().isEmpty()) {
                    value = value.trim();

                    if (isLiveFormatting()) {
                        return toStringLiveFormatting(value);
                    }

                    return toStringOnCommitFormatting(value);
                }

                return value;
            }

            private String toStringLiveFormatting(String value) {
                String regionCode = getRegionCode(value);
                AsYouTypeFormatter asYouTypeFormatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode);
                String result = "";
                for (int i = 0; i < value.length(); i++) {
                    result = asYouTypeFormatter.inputDigit(value.charAt(i));
                }

                return result.substring(getSelectedCountry().countryCodePrefix().length()).trim();
            }

            private String toStringOnCommitFormatting(String value) {
                String regionCode = getRegionCode(value);

                try {
                    Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(value, regionCode);

                    Country country = Country.ofPhoneNumber(phoneNumber);
                    setSelectedCountry(country);

                    if (!phoneNumberUtil.isValidNumber(phoneNumber)) {
                        return formatAsYouType(value, country);
                    }

                    // a valid number
                    if (isCountryCodeVisible()) {
                        // use the international formatting WITHOUT local prefix, e.g "0" in Germany
                        String prefix = country.countryCodePrefix();
                        return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL).substring(prefix.length()).trim();
                    } else {
                        // use the national formatting WITH local prefix, e.g "0" in Germany
                        return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
                    }
                } catch (NumberParseException e) {
                    // no-op
                }

                return value;
            }

            @Override
            public String fromString(String textFieldString) {
                if (getSelectedCountry() != null) {
                    try {
                        Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(textFieldString, getSelectedCountry().iso2Code());
                        return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
                    } catch (NumberParseException e) {
                        parsingErrorType.set(e.getErrorType());
                    }
                }

                return null;
            }
        };

        countryCodeVisibleProperty().addListener(it -> commitValue());

        TextFormatter<String> formatter = new TextFormatter<>(converter, null, filter);
        formatter.valueProperty().bindBidirectional(valueProperty());

        textProperty().addListener(it -> {
            if (isLiveFormatting()) {
                if (!updating) {
                    Platform.runLater(() -> {
                        updating = true;
                        try {
                            doCommitValue();
                        } finally {
                            updating = false;
                        }
                    });
                }
            }
        });

        setTextFormatter(formatter);

        if (value != null && !value.trim().isEmpty()) {
            setValue(value);
        }
    }

    private String formatAsYouType(String value, Country country) {
        // not a valid number, yet, so let's use the AsYouType formatter
        AsYouTypeFormatter asYouTypeFormatter = phoneNumberUtil.getAsYouTypeFormatter(country.iso2Code());
        String result = "";
        for (int i = 0; i < value.length(); i++) {
            result = asYouTypeFormatter.inputDigit(value.charAt(i));
        }
        return result.substring(country.countryCodePrefix().length()).trim();
    }

    private String getRegionCode(String value) {
        try {
            if (value.startsWith("+")) {
                Phonenumber.PhoneNumber number = PhoneNumberUtil.getInstance().parse(value, Country.ofDefaultLocale().iso2Code());
                Country country = Country.ofPhoneNumber(number);
                setSelectedCountry(country);
                return country.iso2Code();
            }
        } catch (NumberParseException ex) {
            // do nothing
        }

        Country country = getSelectedCountry();
        if (country != null) {
            return country.iso2Code();
        }

        return null;
    }

    private synchronized void doCommitValue() {
        commitValue();
    }

    private boolean updating = false;

    private final BooleanProperty validityCheckIncludesTypeCheck = new SimpleBooleanProperty(this, "validityCheckIncludesTypeCheck", false);

    public final boolean isValidityCheckIncludesTypeCheck() {
        return validityCheckIncludesTypeCheck.get();
    }

    /**
     * Determines whether the validity of the field requires a valid phone number type, e.g. when
     * the expected type is "mobile" then the field will only be valid if the number is not just a
     * valid number by itself, but it is definitely a "mobile" number.
     *
     * @return true if the type of the number is considered when doing the validity check
     */
    public final BooleanProperty validityCheckIncludesTypeCheckProperty() {
        return validityCheckIncludesTypeCheck;
    }

    public final void setValidityCheckIncludesTypeCheck(boolean validityCheckIncludesTypeCheck) {
        this.validityCheckIncludesTypeCheck.set(validityCheckIncludesTypeCheck);
    }

    private final BooleanProperty liveFormatting = new SimpleBooleanProperty(this, "liveFormatting", false);

    public final boolean isLiveFormatting() {
        return liveFormatting.get();
    }

    /**
     * A flag that determines whether the displayed text will be formatted while the user is
     * still entering it or only when the user "commits" the value by typing the ENTER key or by
     * moving the focus to another field.
     *
     * @return true if the field gets constantly formatted
     */
    public final BooleanProperty liveFormattingProperty() {
        return liveFormatting;
    }

    public final void setLiveFormatting(boolean liveFormatting) {
        this.liveFormatting.set(liveFormatting);
    }

    private void updateCountryList() {
        Set<Country> temp1 = new TreeSet<>(NAME_SORT_ASC);
        Set<Country> temp2 = new TreeSet<>(NAME_SORT_ASC);

        getAvailableCountries().forEach(code -> {
            if (!getPreferredCountries().contains(code)) {
                temp2.add(code);
            }
        });

        getPreferredCountries().forEach(code -> {
            if (getAvailableCountries().contains(code)) {
                temp1.add(code);
            }
        });

        List<Country> temp = new ArrayList<>();
        temp.addAll(temp1);
        temp.addAll(temp2);
        countries.setAll(temp);

        if (getSelectedCountry() != null && !temp.contains(getSelectedCountry())) {
            clear(); // Clear up the value in case the country code is not available anymore
        }
    }

    @Override
    public void clear() {
        super.clear();

        if (isShowCountryDropdown() || isCountryCodeVisible()) {
            setSelectedCountry(null);
        }
    }

    private void showCountry(Country country) {
        if (!comboBox.isShowing()) {
            comboBox.show();
        }

        ComboBoxListViewSkin<Country> comboBoxSkin = (ComboBoxListViewSkin<Country>) comboBox.getSkin();
        ListView<Country> listView = (ListView<Country>) comboBoxSkin.getPopupContent();

        listView.requestFocus();

        if (country != null) {
            listView.scrollTo(country);
            listView.getFocusModel().focus(listView.getItems().indexOf(country));
            listView.requestFocus();
        }
    }

    private final StringProperty missingCountryPromptText = new SimpleStringProperty(this, "missingCountryPromptText", "Select a country ...");

    public final String getMissingCountryPromptText() {
        return missingCountryPromptText.get();
    }

    /**
     * Specifies a prompt text that will be shown when no country has been selected, yet.
     */
    public final StringProperty missingCountryPromptTextProperty() {
        return missingCountryPromptText;
    }

    public final void setMissingCountryPromptText(String missingCountryPromptText) {
        this.missingCountryPromptText.set(missingCountryPromptText);
    }

    private void updatePromptTextWithExampleNumber() {
        if (!promptTextProperty().isBound()) {
            if (getSelectedCountry() == null) {
                setPromptText(getMissingCountryPromptText());
            } else {
                if (isShowExampleNumbers()) {
                    if (getSelectedCountry() == null) {
                        setPromptText(null);
                    } else {
                        Phonenumber.PhoneNumber sampleNumber;
                        if (getExpectedPhoneNumberType() == null) {
                            sampleNumber = phoneNumberUtil.getExampleNumber(getSelectedCountry().iso2Code());
                        } else {
                            sampleNumber = phoneNumberUtil.getExampleNumberForType(getSelectedCountry().iso2Code(), getExpectedPhoneNumberType());
                        }
                        if (sampleNumber != null) {
                            setPromptText(phoneNumberUtil.format(sampleNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                        } else {
                            setPromptText("");
                        }
                    }
                } else {
                    setPromptText(null);
                }
            }
        }
    }

    private void updateValidPseudoState() {
        pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !isValid());
    }

    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(PhoneNumberField.class.getResource("phone-number-field.css")).toExternalForm();
    }

    private final ObjectProperty<ListCell<Country>> buttonCell = new SimpleObjectProperty<>(this, "buttonCell", new ButtonCell());

    public final ListCell<Country> getButtonCell() {
        return buttonCell.get();
    }

    /**
     * A property that provided the button cell to be displayed by the ComboBox of the control.
     * By default the combo box displays the flag of the currently selected country.
     *
     * @return a list cell to be used as the button cell
     */
    public final ObjectProperty<ListCell<Country>> buttonCellProperty() {
        return buttonCell;
    }

    public final void setButtonCell(ListCell<Country> buttonCell) {
        this.buttonCell.set(buttonCell);
    }

    private class ButtonCell extends ListCell<Country> {

        public ButtonCell() {
            getStyleClass().add("graphics");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(Country country, boolean empty) {
            super.updateItem(country, empty);

            if (!empty && country != null) {
                setGraphic(getCountryGraphic(country));
            }
        }
    }

    // PARSING ERROR TYPE

    private final ReadOnlyObjectWrapper<ErrorType> parsingErrorType = new ReadOnlyObjectWrapper<>(this, "parsingErrorType");

    public final ErrorType getParsingErrorType() {
        return parsingErrorType.get();
    }

    /**
     * Returns the error type property of the phone number field. The error type
     * represents the type of error that occurred during the parsing of the phone
     * number.
     *
     * @return the error type property
     */
    public final ReadOnlyObjectProperty<ErrorType> parsingErrorTypeProperty() {
        return parsingErrorType.getReadOnlyProperty();
    }

    // SELECTED COUNTRY

    private final ObjectProperty<Country> selectedCountry = new SimpleObjectProperty<>(this, "selectedCountry", Country.ofDefaultLocale());

    /**
     * The selected country. Use this property if you want to define a default (pre-selected) country.
     * It can also be used in conjunction with {@link #disableCountryDropdownProperty()} to avoid
     * changing the country part.
     */
    public final ObjectProperty<Country> selectedCountryProperty() {
        return selectedCountry;
    }

    public final Country getSelectedCountry() {
        return selectedCountryProperty().get();
    }

    public final void setSelectedCountry(Country selectedCountry) {
        selectedCountryProperty().set(selectedCountry);
    }

    private final StringProperty value = new SimpleStringProperty(this, "value");

    public final String getValue() {
        return value.get();
    }

    /**
     * The value of the field is the information put into the field from the outside / from the
     * application. The value property is the model of the phone number field.
     *
     * @return the value of the field, which is the raw number passed into the field from the application
     */
    public final StringProperty valueProperty() {
        return value;
    }

    public final void setValue(String value) {
        this.value.set(value);
    }

    // INTERNATIONAL PHONE NUMBER

    private final ReadOnlyStringWrapper internationalPhoneNumber = new ReadOnlyStringWrapper(this, "internationalPhoneNumber");

    public final String getInternationalPhoneNumber() {
        return internationalPhoneNumber.get();
    }

    /**
     * The {@link #phoneNumberProperty() phone number} formatted as an international number including the country code.
     */
    public final ReadOnlyStringProperty internationalPhoneNumberProperty() {
        return internationalPhoneNumber.getReadOnlyProperty();
    }

    // NATIONAL PHONE NUMBER

    private final ReadOnlyStringWrapper nationalPhoneNumber = new ReadOnlyStringWrapper(this, "nationalPhoneNumber");

    /**
     * The {@link #phoneNumberProperty() phone number} formatted as a national number without the country code.
     */
    public final ReadOnlyStringProperty nationalPhoneNumberProperty() {
        return nationalPhoneNumber.getReadOnlyProperty();
    }

    public final String getNationalPhoneNumber() {
        return nationalPhoneNumber.get();
    }

    // E164 PHONE NUMBER

    private final ReadOnlyStringWrapper e164PhoneNumber = new ReadOnlyStringWrapper(this, "e164PhoneNumber");

    /**
     * The {@link #phoneNumberProperty() phone number} formatted as E164 standard format including the country code and national number.
     */
    public final ReadOnlyStringProperty e164PhoneNumberProperty() {
        return e164PhoneNumber.getReadOnlyProperty();
    }

    public final String getE164PhoneNumber() {
        return e164PhoneNumber.get();
    }

    // PHONE NUMBER AS AN OBJECT

    private final ReadOnlyObjectWrapper<Phonenumber.PhoneNumber> phoneNumber = new ReadOnlyObjectWrapper<>(this, "phoneNumber");

    /**
     * The phone number parsed out from the {@link #e164PhoneNumberProperty()} () e164 phone number}, this might be {@code null} if the
     * phone number is not valid.
     */
    public final ReadOnlyObjectProperty<Phonenumber.PhoneNumber> phoneNumberProperty() {
        return phoneNumber.getReadOnlyProperty();
    }

    public final Phonenumber.PhoneNumber getPhoneNumber() {
        return phoneNumber.get();
    }

    // AVAILABLE COUNTRIES

    private final ListProperty<Country> availableCountries = new SimpleListProperty<>(FXCollections.observableArrayList());

    /**
     * @return The list of available countries from which the user can select one and put it into the
     * {@link #selectedCountryProperty()}.
     */
    public final ListProperty<Country> availableCountriesProperty() {
        return availableCountries;
    }

    public final ObservableList<Country> getAvailableCountries() {
        return availableCountries.get();
    }

    public final void setAvailableCountries(ObservableList<Country> availableCountries) {
        this.availableCountries.set(availableCountries);
    }

    // PREFERRED COUNTRIES

    private final ListProperty<Country> preferredCountries = new SimpleListProperty<>(FXCollections.observableArrayList());

    /**
     * @return The list of preferred countries that are shown first in the list of available countries.  If a country
     * is added to this list that is not present in the {@link #getAvailableCountries()} then it will be ignored
     * and not shown.
     */
    public final ListProperty<Country> preferredCountriesProperty() {
        return preferredCountries;
    }

    public final ObservableList<Country> getPreferredCountries() {
        return preferredCountries;
    }

    public final void setPreferredCountries(ObservableList<Country> preferredCountries) {
        this.preferredCountries.set(preferredCountries);
    }

    // DISABLE COUNTRY DROPDOWN

    private final BooleanProperty disableCountryDropdown = new SimpleBooleanProperty(this, "disableCountryDropdown");

    /**
     * @return Flag to disable the country dropdown. This will allow to specify a default country and avoid changing it
     * in case it is wanted to be fixed.
     */
    public final BooleanProperty disableCountryDropdownProperty() {
        return disableCountryDropdown;
    }

    public final boolean getDisableCountryDropdown() {
        return disableCountryDropdownProperty().get();
    }

    public final void setDisableCountryDropdown(boolean disableCountryDropdown) {
        disableCountryDropdownProperty().set(disableCountryDropdown);
    }

    // COUNTRY CELL FACTORY

    private final ObjectProperty<Callback<ListView<Country>, ListCell<Country>>> countryCellFactory = new SimpleObjectProperty<>(this, "countryCellFactory", listView -> new CountryCell());

    /**
     * @return Factory that allows to replace the list cells used to graphically represent each country.
     */
    public final ObjectProperty<Callback<ListView<Country>, ListCell<Country>>> countryCellFactoryProperty() {
        return countryCellFactory;
    }

    public final Callback<ListView<Country>, ListCell<Country>> getCountryCellFactory() {
        return countryCellFactoryProperty().get();
    }

    public final void setCountryCellFactory(Callback<ListView<Country>, ListCell<Country>> countryCellFactory) {
        countryCellFactoryProperty().set(countryCellFactory);
    }

    // VALID

    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(this, "valid");

    /**
     * @return Read-only property that indicates whether the phone number is valid or not.
     */
    public final ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }

    public final boolean isValid() {
        return valid.get();
    }

    // EXPECTED PHONE NUMBER TYPE

    private final ObjectProperty<PhoneNumberUtil.PhoneNumberType> expectedPhoneNumberType = new SimpleObjectProperty<>(this, "expectedPhoneNumberType");

    /**
     * Property that indicates the expected phone number type.
     * This plays a role in the validation and also the showing of an example / prompt text of the
     * phone number format in the text field after selecting the country.
     */
    public final ObjectProperty<PhoneNumberUtil.PhoneNumberType> expectedPhoneNumberTypeProperty() {
        return expectedPhoneNumberType;
    }

    public final PhoneNumberUtil.PhoneNumberType getExpectedPhoneNumberType() {
        return expectedPhoneNumberTypeProperty().get();
    }

    public final void setExpectedPhoneNumberType(PhoneNumberUtil.PhoneNumberType expectedType) {
        expectedPhoneNumberTypeProperty().set(expectedType);
    }

    private boolean isExpectedTypeMatch(Phonenumber.PhoneNumber number) {
        return Optional.ofNullable(getExpectedPhoneNumberType())
                .map(t -> t == phoneNumberUtil.getNumberType(number))
                .orElse(true);
    }

    // COUNTRY CODE VISIBLE

    private final BooleanProperty countryCodeVisible = new SimpleBooleanProperty(this, "countryCodeVisible", true);

    /**
     * @return Determines if the country code stays visible in the field or if it gets removed as soon as
     * the field has determined which country it is.
     */
    public final BooleanProperty countryCodeVisibleProperty() {
        return countryCodeVisible;
    }

    public final boolean isCountryCodeVisible() {
        return countryCodeVisibleProperty().get();
    }

    public final void setCountryCodeVisible(boolean countryCodeVisible) {
        countryCodeVisibleProperty().set(countryCodeVisible);
    }

    // SHOW COUNTRY DROPDOWN

    private final BooleanProperty showCountryDropdown = new SimpleBooleanProperty(this, "showCountryDropdown", true);

    /**
     * @return The property indicating whether the country dropdown is visible or not.
     */
    public final BooleanProperty showCountryDropdownProperty() {
        return showCountryDropdown;
    }

    public final boolean isShowCountryDropdown() {
        return showCountryDropdown.get();
    }

    public final void setShowCountryDropdown(boolean showCountryDropdown) {
        this.showCountryDropdown.set(showCountryDropdown);
    }

    // SHOW EXAMPLE NUMBERS

    private final BooleanProperty showExampleNumbers = new SimpleBooleanProperty(this, "showExampleNumbers", true);

    /**
     * @return The property that allows to show/hide prompt text with phone number examples after selecting the country.
     */
    public final BooleanProperty showExampleNumbersProperty() {
        return showExampleNumbers;
    }

    public final boolean isShowExampleNumbers() {
        return showExampleNumbers.get();
    }

    public final void setShowExampleNumbers(boolean showExampleNumbers) {
        this.showExampleNumbers.set(showExampleNumbers);
    }

    /**
     * All countries supported by the control.
     */
    public enum Country {
        AFGHANISTAN(93, "AF"),
        ALAND_ISLANDS(358, "AX", 18),
        ALBANIA(355, "AL"),
        ALGERIA(213, "DZ"),
        AMERICAN_SAMOA(1, "AS", 684),
        ANDORRA(376, "AD"),
        ANGOLA(244, "AO"),
        ANGUILLA(1, "AI", 264),
        ANTIGUA_AND_BARBUDA(1, "AG", 268),
        ARGENTINA(54, "AR"),
        ARMENIA(374, "AM"),
        ARUBA(297, "AW"),
        AUSTRALIA(61, "AU"),
        AUSTRALIA_ANTARCTIC_TERRITORIES(672, "AQ", 1),
        AUSTRIA(43, "AT"),
        AZERBAIJAN(994, "AZ"),
        BAHAMAS(1, "BS", 242),
        BAHRAIN(973, "BH"),
        BANGLADESH(880, "BD"),
        BARBADOS(1, "BB", 246),
        BELARUS(375, "BY"),
        BELGIUM(32, "BE"),
        BELIZE(501, "BZ"),
        BENIN(229, "BJ"),
        BERMUDA(1, "BM", 441),
        BHUTAN(975, "BT"),
        BOLIVIA(591, "BO"),
        BONAIRE(599, "BQ", 7),
        BOSNIA_AND_HERZEGOVINA(387, "BA"),
        BOTSWANA(267, "BW"),
        BRAZIL(55, "BR"),
        BRITISH_INDIAN_OCEAN_TERRITORY(246, "IO"),
        BRITISH_VIRGIN_ISLANDS(1, "VG", 284),
        BRUNEI(673, "BN"),
        BULGARIA(359, "BG"),
        BURKINA_FASO(226, "BF"),
        BURUNDI(257, "BI"),
        CAMBODIA(855, "KH"),
        CAMEROON(237, "CM"),
        CANADA(1, "CA", 204, 226, 236, 249, 250, 263, 289, 306, 343, 365, 367, 368, 403, 416, 418, 431, 437, 438, 450, 468,
                474, 506, 514, 519, 548, 579, 581, 584, 587, 604, 613, 639, 647, 673, 683, 705, 709, 742, 753, 778, 780, 782,
                807, 819, 825, 867, 873, 902, 905),
        CAPE_VERDE(238, "CV"),
        CAYMAN_ISLANDS(1, "KY", 345),
        CENTRAL_AFRICAN_REPUBLIC(236, "CF"),
        CHAD(235, "TD"),
        CHILE(56, "CL"),
        CHINA(86, "CN"),
        CHRISTMAS_ISLAND(61, "CX", 89164),
        COCOS_ISLANDS(61, "CC", 89162),
        COLOMBIA(57, "CO"),
        COMOROS(269, "KM"),
        CONGO(242, "CG"),
        COOK_ISLANDS(682, "CK"),
        COSTA_RICA(506, "CR"),
        CROATIA(385, "HR"),
        CUBA(53, "CU"),
        CURACAO(599, "CW", 9),
        CYPRUS(357, "CY"),
        CZECH_REPUBLIC(420, "CZ"),
        DEMOCRATIC_REPUBLIC_OF_THE_CONGO(243, "CD"),
        DENMARK(45, "DK"),
        DJIBOUTI(253, "DJ"),
        DOMINICA(1, "DM", 767),
        DOMINICAN_REPUBLIC(1, "DO", 809, 829, 849),
        EAST_TIMOR(670, "TL"),
        ECUADOR(593, "EC"),
        EGYPT(20, "EG"),
        EL_SALVADOR(503, "SV"),
        EQUATORIAL_GUINEA(240, "GQ"),
        ERITREA(291, "ER"),
        ESTONIA(372, "EE"),
        ETHIOPIA(251, "ET"),
        FALKLAND_ISLANDS(500, "FK"),
        FAROE_ISLANDS(298, "FO"),
        FIJI(679, "FJ"),
        FINLAND(358, "FI"),
        FRANCE(33, "FR"),
        FRENCH_GUIANA(594, "GF"),
        FRENCH_POLYNESIA(689, "PF"),
        GABON(241, "GA"),
        GAMBIA(220, "GM"),
        GEORGIA(995, "GE"),
        GERMANY(49, "DE"),
        GHANA(233, "GH"),
        GIBRALTAR(350, "GI"),
        GREECE(30, "GR"),
        GREENLAND(299, "GL"),
        GRENADA(1, "GD", 473),
        GUADELOUPE(590, "GP"),
        GUAM(1, "GU", 671),
        GUATEMALA(502, "GT"),
        GUERNSEY(44, "GG", 1481, 7781, 7839, 7911),
        GUINEA(224, "GN"),
        GUINEA_BISSAU(245, "GW"),
        GUYANA(592, "GY"),
        HAITI(509, "HT"),
        HONDURAS(504, "HN"),
        HONG_KONG(852, "HK"),
        HUNGARY(36, "HU"),
        ICELAND(354, "IS"),
        INDIA(91, "IN"),
        INDONESIA(62, "ID"),
        IRAN(98, "IR"),
        IRAQ(964, "IQ"),
        IRELAND(353, "IE"),
        ISLE_OF_MAN(44, "IM", 1624, 7524, 7624, 7924),
        ISRAEL(972, "IL"),
        ITALY(39, "IT"),
        IVORY_COAST(225, "CI"),
        JAMAICA(1, "JM", 658, 876),
        JAN_MAYEN(47, "SJ", 79),
        JAPAN(81, "JP"),
        JERSEY(44, "JE", 1534),
        JORDAN(962, "JO"),
        KAZAKHSTAN(7, "KZ", 6, 7),
        KENYA(254, "KE"),
        KIRIBATI(686, "KI"),
        KOREA_NORTH(850, "KP"),
        KOREA_SOUTH(82, "KR"),
        KOSOVO(383, "XK"),
        KUWAIT(965, "KW"),
        KYRGYZSTAN(996, "KG"),
        LAOS(856, "LA"),
        LATVIA(371, "LV"),
        LEBANON(961, "LB"),
        LESOTHO(266, "LS"),
        LIBERIA(231, "LR"),
        LIBYA(218, "LY"),
        LIECHTENSTEIN(423, "LI"),
        LITHUANIA(370, "LT"),
        LUXEMBOURG(352, "LU"),
        MACAU(853, "MO"),
        MACEDONIA(389, "MK"),
        MADAGASCAR(261, "MG"),
        MALAWI(265, "MW"),
        MALAYSIA(60, "MY"),
        MALDIVES(960, "MV"),
        MALI(223, "ML"),
        MALTA(356, "MT"),
        MARSHALL_ISLANDS(692, "MH"),
        MARTINIQUE(596, "MQ"),
        MAURITANIA(222, "MR"),
        MAURITIUS(230, "MU"),
        MAYOTTE(262, "YT", 269, 639),
        MEXICO(52, "MX"),
        MICRONESIA(691, "FM"),
        MOLDOVA(373, "MD"),
        MONACO(377, "MC"),
        MONGOLIA(976, "MN"),
        MONTENEGRO(382, "ME"),
        MONTSERRAT(1, "MS", 664),
        MOROCCO(212, "MA"),
        MOZAMBIQUE(258, "MZ"),
        MYANMAR(95, "MM"),
        NAMIBIA(264, "NA"),
        NAURU(674, "NR"),
        NEPAL(977, "NP"),
        NETHERLANDS(31, "NL"),
        NEW_CALEDONIA(687, "NC"),
        NEW_ZEALAND(64, "NZ"),
        NICARAGUA(505, "NI"),
        NIGER(227, "NE"),
        NIGERIA(234, "NG"),
        NIUE(683, "NU"),
        NORFOLK_ISLAND(672, "NF", 3),
        NORTHERN_MARIANA_ISLANDS(1, "MP", 670),
        NORWAY(47, "NO"),
        OMAN(968, "OM"),
        PAKISTAN(92, "PK"),
        PALAU(680, "PW"),
        PALESTINE(970, "PS"),
        PANAMA(507, "PA"),
        PAPUA_NEW_GUINEA(675, "PG"),
        PARAGUAY(595, "PY"),
        PERU(51, "PE"),
        PHILIPPINES(63, "PH"),
        POLAND(48, "PL"),
        PORTUGAL(351, "PT"),
        PUERTO_RICO(1, "PR", 787, 930),
        QATAR(974, "QA"),
        REUNION(262, "RE"),
        ROMANIA(40, "RO"),
        RUSSIA(7, "RU"),
        RWANDA(250, "RW"),
        SAINT_HELENA(290, "SH"),
        SAINT_KITTS_AND_NEVIS(1, "KN", 869),
        SAINT_LUCIA(1, "LC", 758),
        SAINT_PIERRE_AND_MIQUELON(508, "PM"),
        SAINT_VINCENT_AND_THE_GRENADINES(1, "VC", 784),
        SAMOA(685, "WS"),
        SAN_MARINO(378, "SM"),
        SAO_TOME_AND_PRINCIPE(239, "ST"),
        SAUDI_ARABIA(966, "SA"),
        SENEGAL(221, "SN"),
        SERBIA(381, "RS"),
        SEYCHELLES(248, "SC"),
        SIERRA_LEONE(232, "SL"),
        SINGAPORE(65, "SG"),
        SLOVAKIA(421, "SK"),
        SLOVENIA(386, "SI"),
        SOLOMON_ISLANDS(677, "SB"),
        SOMALIA(252, "SO"),
        SOUTH_AFRICA(27, "ZA"),
        SOUTH_SUDAN(211, "SS"),
        SPAIN(34, "ES"),
        SRI_LANKA(94, "LK"),
        SUDAN(249, "SD"),
        SURINAME(597, "SR"),
        SVALBARD_AND_JAN_MAYEN(47, "SJ"),
        SWAZILAND(268, "SZ"),
        SWEDEN(46, "SE"),
        SWITZERLAND(41, "CH"),
        SYRIA(963, "SY"),
        TAIWAN(886, "TW"),
        TAJIKISTAN(992, "TJ"),
        TANZANIA(255, "TZ"),
        THAILAND(66, "TH"),
        TOGO(228, "TG"),
        TOKELAU(690, "TK"),
        TONGA(676, "TO"),
        TRINIDAD_AND_TOBAGO(1, "TT", 868),
        TUNISIA(216, "TN"),
        TURKEY(90, "TR"),
        TURKMENISTAN(993, "TM"),
        TURKS_AND_CAICOS_ISLANDS(1, "TC", 649),
        TUVALU(688, "TV"),
        UGANDA(256, "UG"),
        UKRAINE(380, "UA"),
        UNITED_ARAB_EMIRATES(971, "AE"),
        UNITED_KINGDOM(44, "GB"),
        UNITED_STATES(1, "US"),
        URUGUAY(598, "UY"),
        UZBEKISTAN(998, "UZ"),
        VANUATU(678, "VU"),
        VATICAN_CITY(379, "VA"),
        VENEZUELA(58, "VE"),
        VIETNAM(84, "VN"),
        VIRGIN_ISLANDS(1, "VI", 340),
        WALLIS_AND_FUTUNA(681, "WF"),
        WESTERN_SAHARA(212, "EH"),
        YEMEN(967, "YE"),
        ZAMBIA(260, "ZM"),
        ZANZIBAR(255, "TZ"),
        ZIMBABWE(263, "ZW");

        private final int countryCode;
        private final String iso2Code;
        private final int[] areaCodes;

        Country(int countryCode, String iso2Code, int... areaCodes) {
            this.countryCode = countryCode;
            this.iso2Code = iso2Code;
            this.areaCodes = Optional.ofNullable(areaCodes).orElse(new int[0]);
        }

        public static Country ofDefaultLocale() {
            return ofLocale(Locale.getDefault());
        }

        public static Country ofLocale(Locale locale) {
            return ofISO2(locale.getCountry());
        }

        public static Country ofISO2(String country) {
            for (Country c : values()) {
                if (c.iso2Code.equals(country)) {
                    return c;
                }
            }
            return null;
        }

        public static Country ofCountryCodePrefix(String prefix) {
            try {
                int code = Integer.parseInt(prefix.substring(1)); // skip the "+"

                // first try to find the country with no additional area codes, e.g. US = +1
                for (Country c : values()) {
                    if (c.countryCode == code && c.areaCodes.length == 0) {
                        return c;
                    }
                }

                // now find a country WITH area codes
                for (Country c : values()) {
                    if (c.countryCode == code) {
                        return c;
                    }
                }
            } catch (NumberFormatException ex) {
                // no-op
            }

            return null;
        }

        public static Country ofPhoneNumber(Phonenumber.PhoneNumber phoneNumber) {
            int code = phoneNumber.getCountryCode();

            String numberText = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            numberText = numberText.substring(("+" + code).length());

            int geoLength = PhoneNumberUtil.getInstance().getLengthOfNationalDestinationCode(phoneNumber);
            if (numberText.length() >= geoLength) {
                try {
                    int nationalDestinationCode = Integer.parseInt(numberText.substring(0, geoLength));

                    for (Country c : values()) {
                        if (c.countryCode == code) {
                            if (containsArea(c, nationalDestinationCode)) {
                                return c;
                            }
                        }
                    }

                    for (Country c : values()) {
                        if (c.countryCode == code && c.areaCodes.length == 0) {
                            return c;
                        }
                    }
                } catch (NumberFormatException ex) {
                    // fallback strategy is to use the country code only and ignore national destination code
                    return ofCountryCodePrefix("+" + code);
                }
            }

            // fallback strategy is to use the country code only
            return ofCountryCodePrefix("+" + code);
        }

        private static boolean containsArea(Country c, int geoCode) {
            for (int areaCode : c.areaCodes) {
                if (areaCode == geoCode) {
                    return true;
                }
            }
            return false;
        }

        public int countryCode() {
            return countryCode;
        }

        public int[] areaCodes() {
            return areaCodes;
        }

        public String iso2Code() {
            return iso2Code;
        }

        /**
         * @return The phone number prefix including only the country code with (+) sign.
         */
        public String countryCodePrefix() {
            return "+" + countryCode();
        }

        /**
         * The first area code if there is any in the country.
         *
         * @return the first code in the country
         */
        public Integer defaultAreaCode() {
            return areaCodes().length > 0 ? areaCodes()[0] : null;
        }

        /**
         * The phone number prefix including the country and default area code if any.
         *
         * @return The phone number prefix.
         */
        public String phonePrefix() {
            return countryCodePrefix() + Optional.ofNullable(defaultAreaCode()).map(Object::toString).orElse("");
        }

        private String countryName() {
            return new Locale("en", iso2Code).getDisplayCountry();
        }

    }

    private final ObjectProperty<StringConverter<ErrorType>> errorTypeConverter = new SimpleObjectProperty<>(this, "errorTypeConverter", new ErrorTypeConverter());

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

    public class CountryCell extends ListCell<Country> {

        private CountryCell() {
            getStyleClass().add("country-cell");

            setOnMousePressed(evt -> setValue(""));
            setOnTouchPressed(evt -> setValue(""));
        }

        @Override
        protected void updateItem(Country country, boolean empty) {
            super.updateItem(country, empty);

            int index = -1;

            if (country != null && !empty) {
                setText(country.countryName());
                setGraphic(getCountryGraphic(country));
                index = getPreferredCountries().indexOf(country);
            } else {
                setText(null);
                setGraphic(null);
            }

            if (index >= 0) {
                getStyleClass().add("preferred");
                if (index == getPreferredCountries().size() - 1) {
                    getStyleClass().add("last");
                } else {
                    getStyleClass().remove("last");
                }
            } else {
                getStyleClass().remove("preferred");
                getStyleClass().remove("last");
            }
        }
    }

    /**
     * Subclasses of this skin can easily override this method to simply return different
     * flags / globe.
     *
     * @param country the country code
     * @return a node representing the country (normally the country's flag)
     */
    protected Node getCountryGraphic(Country country) {
        Objects.requireNonNull(country, "country can not be null");
        ImageView imageView = new ImageView();
        imageView.setFitHeight(20);
        imageView.setFitWidth(20);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("flag-image-view");
        Optional.ofNullable(FLAG_IMAGES.get(country)).ifPresent(imageView::setImage);

        StackPane wrapper = new StackPane(imageView);
        wrapper.getStyleClass().add("flag-wrapper");
        wrapper.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        wrapper.setPadding(new Insets(1));
        wrapper.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));

        return wrapper;
    }
}
