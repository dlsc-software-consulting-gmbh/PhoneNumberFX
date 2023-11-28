package com.dlsc.phonenumberfx;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * A control for entering phone numbers. By default, the phone numbers are expressed in international format,
 * including the country calling code and delivered by {@link #rawPhoneNumberProperty()}.
 * Another property returns the phone number in the international standard format E164.
 * The control supports a list of {@link #getAvailableCountries() available countries} and a list of
 * {@link #getPreferredCountries() preferred countries}.
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

    private final CountryResolver resolver;
    private final PhoneNumberFormatter formatter;
    private final PhoneNumberUtil phoneNumberUtil;

    /**
     * Builds a new phone number field with the default settings. The available country
     * calling codes are defined on {@link Country}.
     */
    public PhoneNumberField() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getAvailableCountries().setAll(Country.values());

        ObservableList<Country> countries = FXCollections.observableArrayList();

        ComboBox<Country> comboBox = new ComboBox<>() {
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

                        // use same bounds for globe that were computed for the button cell
                        this.globeButton.resizeRelocate(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
                        this.globeButton.setVisible(getSkinnable().getValue() == null);
                        this.globeButton.setManaged(getSkinnable().getValue() == null);
                        this.globeButton.toFront();
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

        ButtonCell buttonCell = new ButtonCell();
        comboBox.setButtonCell(buttonCell);

        setLeft(comboBox);

        addEventFilter(KeyEvent.KEY_PRESSED, evt -> {
            if (evt.getCode().isLetterKey()) {
                String letter = evt.getCode().getChar();

                ComboBoxListViewSkin<Country> comboBoxSkin = (ComboBoxListViewSkin<Country>) comboBox.getSkin();
                ListView<Country> listView = (ListView<Country>) comboBoxSkin.getPopupContent();

                Country country = countries
                    .stream()
                    .filter(c -> c.countryName().startsWith(letter))
                    .findFirst()
                    .orElse(null);

                if (country != null) {
                    if (!comboBox.isShowing()) {
                        comboBox.show();
                    }

                    listView.requestFocus();
                    listView.scrollTo(country);
                }
            }
        });

        phoneNumberUtil = PhoneNumberUtil.getInstance();
        resolver = new CountryResolver();
        formatter = new PhoneNumberFormatter();

        InvalidationListener updateSampleListener = it -> updatePromptTextWithExampleNumber();
        showExampleNumbersProperty().addListener(updateSampleListener);
        expectedPhoneNumberTypeProperty().addListener(updateSampleListener);
        selectedCountryProperty().addListener(updateSampleListener);

        showCountryDropdownProperty().addListener(it -> requestLayout()); // important

        phoneNumberProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                setE164PhoneNumber(null);
                setNationalPhoneNumber(null);
                setValid(true);
            } else {
                setE164PhoneNumber(phoneNumberUtil.format(newV, PhoneNumberUtil.PhoneNumberFormat.E164));
                setNationalPhoneNumber(phoneNumberUtil.format(newV, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
                setValid(phoneNumberUtil.isValidNumber(newV) && isExpectedTypeMatch(newV));
            }
        });

        ChangeListener<Object> updateFormattedPhoneNumberListener = (obs, oldV, newV) -> Platform.runLater(() -> formatter.setFormattedPhoneNumber(getRawPhoneNumber()));
        rawPhoneNumberProperty().addListener(updateFormattedPhoneNumberListener);
        countryCodeVisibleProperty().addListener(updateFormattedPhoneNumberListener);
        validProperty().addListener((obs, oldV, newV) -> pseudoClassStateChanged(INVALID_PSEUDO_CLASS, !newV));

        countryCellFactory.addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                setCountryCellFactory(oldValue);
                throw new IllegalArgumentException("country cell factory can not be null");
            }
        });

        Runnable callingCodesUpdater = () -> {
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
                setRawPhoneNumber(null); // Clear up the value in case the country code is not available anymore
            }
        };

        InvalidationListener listener = obs -> callingCodesUpdater.run();
        getAvailableCountries().addListener(listener);
        getPreferredCountries().addListener(listener);
        countryCellFactoryProperty().addListener(listener);
        callingCodesUpdater.run();

        validProperty().addListener(it -> updateValidPseudoState());
        updateValidPseudoState();

        getStylesheets().add(getUserAgentStylesheet());
    }

    private void updatePromptTextWithExampleNumber() {
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
                setPromptText(formatter.doFormat(phoneNumberUtil.format(sampleNumber, PhoneNumberUtil.PhoneNumberFormat.E164), getSelectedCountry()));
            }
        } else {
            if (!promptTextProperty().isBound()) {
                setPromptText(null);
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

    @Override
    public void clear() {
        super.clear();
        setRawPhoneNumber(null);
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
                ButtonCell.this.setGraphic(getCountryGraphic(country));
            }
        }
    }

    // VALUES

    private final StringProperty rawPhoneNumber = new SimpleStringProperty(this, "rawPhoneNumber") {
        private boolean selfUpdate;

        @Override
        public void set(String newRawPhoneNumber) {
            if (selfUpdate) {
                return;
            }

            try {
                selfUpdate = true;

                // Set the value first, so that the binding will be triggered
                super.set(newRawPhoneNumber);

                // Resolve all dependencies out of the raw phone number
                Country country = resolver.call(newRawPhoneNumber);

                if (country != null) {
                    setSelectedCountry(country);
                    formatter.setFormattedPhoneNumber(newRawPhoneNumber);

                    try {
                        Phonenumber.PhoneNumber number = phoneNumberUtil.parse(newRawPhoneNumber, country.iso2Code());
                        setPhoneNumber(number);
                    } catch (Exception e) {
                        setPhoneNumber(null);
                    }
                } else {
                    setSelectedCountry(null);
                    formatter.setFormattedPhoneNumber(null);
                    setPhoneNumber(null);
                }

            } finally {
                selfUpdate = false;
            }
        }
    };

    // RAW PHONE NUMBER

    /**
     * @return The raw phone number corresponding exactly to what the user typed in, including the (+) sign appended at the
     * beginning.  This value can be a valid E164 formatted number.
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

    // SELECTED COUNTRY

    private final ObjectProperty<Country> selectedCountry = new SimpleObjectProperty<>(this, "selectedCountry") {
        private boolean selfUpdate;

        @Override
        public void set(Country newCountry) {
            if (selfUpdate) {
                return;
            }

            try {
                selfUpdate = true;

                // Set the value first, so that the binding will be triggered
                super.set(newCountry);

                setRawPhoneNumber(newCountry == null ? null : newCountry.phonePrefix());

            } finally {
                selfUpdate = false;
            }
        }
    };

    /**
     * @return The selected country. Use this property if you want to define a default (pre-selected) country.
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

    // NATIONAL PHONE NUMBER

    private final ReadOnlyStringWrapper nationalPhoneNumber = new ReadOnlyStringWrapper(this, "nationalPhoneNumber");

    /**
     * @return The {@link #phoneNumberProperty() phone number} formatted as a national number without the country code.
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

    // E164 PHONE NUMBER

    private final ReadOnlyStringWrapper e164PhoneNumber = new ReadOnlyStringWrapper(this, "e164PhoneNumber");

    /**
     * @return The {@link #phoneNumberProperty() phone number} formatted as E164 standard format including the country code and national number.
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

    // PHONE NUMBER AS AN OBJECT

    private final ReadOnlyObjectWrapper<Phonenumber.PhoneNumber> phoneNumber = new ReadOnlyObjectWrapper<>(this, "phoneNumber");

    /**
     * @return The phone number parsed out from the {@link #rawPhoneNumberProperty() raw phone number}, this might be {@code null} if the
     * phone number is not a valid one.
     */
    public final ReadOnlyObjectProperty<Phonenumber.PhoneNumber> phoneNumberProperty() {
        return phoneNumber.getReadOnlyProperty();
    }

    public final Phonenumber.PhoneNumber getPhoneNumber() {
        return phoneNumber.get();
    }

    private void setPhoneNumber(Phonenumber.PhoneNumber phoneNumber) {
        this.phoneNumber.set(phoneNumber);
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

    private void setValid(boolean valid) {
        this.valid.set(valid);
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

    private final BooleanProperty countryCodeVisible = new SimpleBooleanProperty(this, "countryCodeVisible");

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
        CANADA(1, "CA"),
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

        public static Country ofISO2(String country) {
            for (Country c : values()) {
                if (c.iso2Code.equals(country)) {
                    return c;
                }
            }
            return null;
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
         * @return The phone number prefix.
         */
        public String phonePrefix() {
            return countryCodePrefix() + Optional.ofNullable(defaultAreaCode()).map(Object::toString).orElse("");
        }

        private String countryName() {
            return new Locale("en", iso2Code).getDisplayCountry();
        }

    }

    /**
     * For internal use only.
     */
    private final class PhoneNumberFormatter implements UnaryOperator<TextFormatter.Change> {

        private PhoneNumberFormatter() {
            PhoneNumberField.this.setTextFormatter(new TextFormatter<>(this));
            PhoneNumberField.this.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.BACK_SPACE
                        && (PhoneNumberField.this.getText() == null || PhoneNumberField.this.getText().isEmpty())
                        && getSelectedCountry() != null
                        && !getDisableCountryDropdown()) {

                    // Clear the country if the user deletes the entire text
                    setRawPhoneNumber(null);
                    e.consume();
                }
            });
        }

        private boolean selfUpdate;

        @Override
        public TextFormatter.Change apply(TextFormatter.Change change) {
            if (selfUpdate) {
                return change;
            }

            try {
                selfUpdate = true;
                Country country = getSelectedCountry();

                if (change.isAdded()) {
                    String text = change.getText();

                    if (country == null && text.startsWith("+")) {
                        text = text.substring(1);
                    }

                    if (!text.isEmpty() && !text.matches("[0-9]+")) {
                        return null;
                    }

                    if (country == null && !change.getControlNewText().startsWith("+")) {
                        change.setText("+" + change.getText());
                        change.setCaretPosition(change.getCaretPosition() + 1);
                        change.setAnchor(change.getAnchor() + 1);
                    }
                }

                if (change.isContentChange()) {
                    if (country == null) {
                        resolveCountry(change);
                    } else {
                        setRawPhoneNumber(undoFormat(change.getControlNewText(), country));
                    }
                }

            } finally {
                selfUpdate = false;
            }

            return change;
        }

        private void resolveCountry(TextFormatter.Change change) {
            Country country = resolver.call(change.getControlNewText());
            if (country != null) {
                setSelectedCountry(country);
                if (!isCountryCodeVisible()) {
                    Platform.runLater(() -> {
                        PhoneNumberField.this.setText(Optional.ofNullable(country.defaultAreaCode()).map(String::valueOf).orElse(""));
                        change.setText("");
                        change.setCaretPosition(0);
                        change.setAnchor(0);
                        change.setRange(0, 0);
                    });
                }
            }
        }

        private String doFormat(String newRawPhoneNumber, Country country) {
            if (newRawPhoneNumber == null || newRawPhoneNumber.isEmpty() || country == null) {
                return "";
            }

            AsYouTypeFormatter formatter = phoneNumberUtil.getAsYouTypeFormatter(country.iso2Code());
            String formattedNumber = "";

            for (char c : newRawPhoneNumber.toCharArray()) {
                formattedNumber = formatter.inputDigit(c);
            }

            if (isCountryCodeVisible()) {
                return formattedNumber;
            }

            return formattedNumber.substring(country.countryCodePrefix().length()).trim();
        }

        private String undoFormat(String formattedPhoneNumber, Country country) {
            StringBuilder rawPhoneNumber = new StringBuilder();

            if (formattedPhoneNumber != null && !formattedPhoneNumber.isEmpty()) {
                for (char c : formattedPhoneNumber.toCharArray()) {
                    if (Character.isDigit(c)) {
                        rawPhoneNumber.append(c);
                    }
                }
            }

            if (!isCountryCodeVisible()) {
                rawPhoneNumber.insert(0, country.countryCodePrefix());
            } else {
                rawPhoneNumber.insert(0, "+");
            }

            return rawPhoneNumber.toString();
        }

        private void setFormattedPhoneNumber(String newRawPhoneNumber) {
            if (selfUpdate) {
                return; // Ignore when I'm the one who initiated the update
            }

            try {
                selfUpdate = true;
                String formattedPhoneNumber = doFormat(newRawPhoneNumber, getSelectedCountry());
                PhoneNumberField.this.setText(formattedPhoneNumber);
                PhoneNumberField.this.positionCaret(formattedPhoneNumber.length());
            } finally {
                selfUpdate = false;
            }
        }

    }

    /**
     * For internal use only.
     */
    private final class CountryResolver implements Callback<String, Country> {

        @Override
        public Country call(String phoneNumber) {
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                return null;
            }

            if (phoneNumber.startsWith("+")) {
                phoneNumber = phoneNumber.substring(1);
                if (phoneNumber.isEmpty()) {
                    return null;
                }
            }

            TreeMap<Integer, List<Country>> scores = new TreeMap<>();

            for (Country country : getAvailableCountries()) {
                int score = calculateScore(country, phoneNumber);
                if (score > 0) {
                    scores.computeIfAbsent(score, s -> new ArrayList<>()).add(country);
                }
            }

            Map.Entry<Integer, List<Country>> highestScore = scores.lastEntry();
            if (highestScore == null) {
                return null;
            }

            return inferBestMatch(highestScore.getValue());
        }

        private int calculateScore(Country country, String phoneNumber) {
            String countryPrefix = String.valueOf(country.countryCode());

            if (country.areaCodes().length == 0) {
                if (phoneNumber.startsWith(countryPrefix)) {
                    return 1;
                }
            } else {
                for (int areaCode : country.areaCodes()) {
                    String areaCodePrefix = countryPrefix + areaCode;
                    if (phoneNumber.startsWith(areaCodePrefix)) {
                        return 2;
                    }
                }
            }

            return 0;
        }

        private Country inferBestMatch(List<Country> matchingCountries) {
            Country code = null;
            if (matchingCountries.size() > 1) {
                // pick the country that is preferred
                for (Country c : matchingCountries) {
                    if (getPreferredCountries().contains(c)) {
                        code = c;
                        break;
                    }
                }

                if (code == null) {
                    code = matchingCountries.get(matchingCountries.size() - 1);
                }
            } else {
                code = matchingCountries.get(0);
            }
            return code;
        }
    }

    private class CountryCell extends ListCell<Country> {

        private CountryCell() {
            getStyleClass().add("country-cell");

            getStylesheets().add(PhoneNumberField.this.getUserAgentStylesheet());
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

        return wrapper;
    }
}
