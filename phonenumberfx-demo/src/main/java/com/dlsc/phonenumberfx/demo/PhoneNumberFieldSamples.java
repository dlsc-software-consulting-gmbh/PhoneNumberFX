package com.dlsc.phonenumberfx.demo;

import com.dlsc.phonenumberfx.PhoneNumberField;
import com.dlsc.phonenumberfx.PhoneNumberField.Country;
import com.dlsc.phonenumberfx.PhoneNumberLabel;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Function;

public final class PhoneNumberFieldSamples {

    private static final Function<Object, String> COUNTRY_CODE_CONVERTER = c -> {
        if (c == null) {
            return null;
        }
        Country code = (Country) c;
        return "(" + code.phonePrefix() + ")" + code;
    };

    private PhoneNumberFieldSamples() {
        super();
    }

    public static Node buildDefaultEmptySample() {
        PhoneNumberField field = new PhoneNumberField();

        String title = "Default Settings";
        String description = "A control without any changes to its properties.";

        return buildSample(title, description, field);
    }

    public static Node buildDefaultPrefilledSample() {
        PhoneNumberField field = new PhoneNumberField();
        field.setText("+573003767182");

        String title = "Initial Value";
        String description = "A control with default settings and a value set through code.";

        return buildSample(title, description, field);
    }

    public static Node buildCustomAvailableCountriesSample() {
        PhoneNumberField field = new PhoneNumberField();
        field.getAvailableCountries().setAll(
            Country.COLOMBIA,
            Country.GERMANY,
            Country.UNITED_STATES,
            Country.UNITED_KINGDOM,
            Country.SWITZERLAND);

        String title = "Available Countries (Customized)";
        String description = "A control with modified list of available countries.";

        return buildSample(title, description, field);
    }

    public static Node buildPreferredCountriesSample() {
        PhoneNumberField field = new PhoneNumberField();

        field.getPreferredCountries().setAll(
            Country.SWITZERLAND,
            Country.GERMANY,
            Country.UNITED_KINGDOM);

        String title = "Preferred Countries";
        String description = "Preferred countries all shown at the top of the list always.";

        return buildSample(title, description, field);
    }

    public static Node buildDisabledCountrySelectorSample() {
        PhoneNumberField field = new PhoneNumberField();
        field.setSelectedCountry(Country.GERMANY);
        field.setDisableCountryDropdown(true);
        field.setExpectedPhoneNumberType(PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER);

        String title = "Disabled Country Selector";
        String description = "Disables the country selector button so it forces the control to keep always the same country.";

        return buildSample(title, description, field);
    }

    public static Node buildExpectedPhoneNumberTypeSample() {
        PhoneNumberField field = new PhoneNumberField();
        field.setExpectedPhoneNumberType(PhoneNumberUtil.PhoneNumberType.MOBILE);
        field.setSelectedCountry(Country.COLOMBIA);

        String title = "Fixed Phone Number Type (MOBILE)";
        String description = "Establish an expected phone number type, performs validations against the type and shows an example of the phone number.";

        return buildSample(title, description, field);
    }

    public static Node buildSample(String title, String description, PhoneNumberField field) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.4em;");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);

        VBox leftBox = new VBox(20);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.getChildren().addAll(titleLabel, descriptionLabel, field);
        leftBox.setPrefWidth(400);

        ColumnConstraints column1 = new ColumnConstraints();
        column1.setPercentWidth(35);

        ColumnConstraints column2 = new ColumnConstraints();
        column2.setPercentWidth(65);

        GridPane rightBox = new GridPane();
        rightBox.setHgap(10);
        rightBox.setVgap(10);
        rightBox.getColumnConstraints().addAll(column1, column2);
        rightBox.setPrefWidth(400);

        PhoneNumberLabel phoneNumberLabel = new PhoneNumberLabel();
        phoneNumberLabel.valueProperty().bind(field.e164PhoneNumberProperty());

        ComboBox<Country> countryBox = new ComboBox<>();
        countryBox.getItems().setAll(Country.values());
        countryBox.getSelectionModel().select(field.getSelectedCountry());
        countryBox.valueProperty().bindBidirectional(phoneNumberLabel.countryProperty());

        VBox labelBox = new VBox(10, phoneNumberLabel, countryBox);

        addField(rightBox, "Text", field.textProperty());
        addField(rightBox, "Value", field.valueProperty());
        addField(rightBox, "Country Code", field.selectedCountryProperty(), COUNTRY_CODE_CONVERTER);
        addField(rightBox, "E164 Format", field.e164PhoneNumberProperty());
        addField(rightBox, "National Format", field.nationalPhoneNumberProperty());
        addField(rightBox, "International Format", field.internationalPhoneNumberProperty());
        addField(rightBox, "PhoneNumberLabel", labelBox);
        addField(rightBox, "Error Type", field.parsingErrorTypeProperty());
        addField(rightBox, "Valid", field.validProperty());

        HBox hBox = new HBox(30);
        hBox.getChildren().addAll(leftBox, rightBox);
        hBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftBox, Priority.NEVER);
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        return hBox;
    }

    private static void addField(GridPane pane, String name, ObservableValue<?> value) {
        addField(pane, name, value, null);
    }

    private static void addField(GridPane pane, String name, ObservableValue<?> value, Function<Object, String> converter) {
        Label valueLbl = new Label();
        if (converter == null) {
            valueLbl.textProperty().bind(Bindings.convert(value));
        } else {
            valueLbl.textProperty().bind(Bindings.createStringBinding(() -> converter.apply(value.getValue()), value));
        }

        addField(pane, name, valueLbl);
        valueLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 1.2em; -fx-font-weight: bold; -fx-padding: 0 0 0 10;");
    }

    private static void addField(GridPane pane, String name, Node node) {
        if (node instanceof VBox) {
            node.setStyle("-fx-border-color: black; -fx-border-width: 1px; -fx-font-family: monospace; -fx-font-size: 1.2em; -fx-font-weight: bold; -fx-padding: 0 0 0 10;");
        } else {
            node.setStyle("-fx-font-family: monospace; -fx-font-size: 1.2em; -fx-font-weight: bold; -fx-padding: 0 0 0 10;");
        }
        int row = pane.getRowCount();
        pane.add(new Label(name + ":"), 0, row);
        pane.add(node, 1, row);
    }

}
