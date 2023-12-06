package com.dlsc.phonenumberfx.demo;

import com.dlsc.phonenumberfx.PhoneNumberField;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.DateTimeStringConverter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SinglePhoneNumberFieldApp extends Application {

    @Override
    public void start(Stage stage) {
        VBox vBox = new VBox(20);
        vBox.setPadding(new Insets(20));
        vBox.setAlignment(Pos.CENTER_LEFT);

        PhoneNumberField field = new PhoneNumberField();
        vBox.getChildren().addAll(
                PhoneNumberFieldSamples.buildSample("Phone Number Field", "A configurable field for entering international phone numbers.", field)
        );

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(evt -> field.clear());

        CheckBox showExampleBox = new CheckBox("Show example number for selected country");
        showExampleBox.selectedProperty().bindBidirectional(field.showExampleNumbersProperty());

        CheckBox countryCodeVisibleBox = new CheckBox("Show country code as part of number");
        countryCodeVisibleBox.selectedProperty().bindBidirectional(field.countryCodeVisibleProperty());

        CheckBox disableCountryCodeBox = new CheckBox("Disable country dropdown");
        disableCountryCodeBox.selectedProperty().bindBidirectional(field.disableCountryDropdownProperty());

        CheckBox showCountryCodeBox = new CheckBox("Show country dropdown");
        showCountryCodeBox.selectedProperty().bindBidirectional(field.showCountryDropdownProperty());

        CheckBox editableBox = new CheckBox("Editable");
        editableBox.selectedProperty().bindBidirectional(field.editableProperty());

        ComboBox<PhoneNumberUtil.PhoneNumberType> expectedTypeBox = new ComboBox<>();
        expectedTypeBox.getItems().setAll(PhoneNumberUtil.PhoneNumberType.values());
        expectedTypeBox.valueProperty().bindBidirectional(field.expectedPhoneNumberTypeProperty());

        ComboBox<PhoneNumberField.Country> countryBox = new ComboBox<>();
        countryBox.getItems().setAll(PhoneNumberField.Country.values());
        countryBox.valueProperty().bindBidirectional(field.selectedCountryProperty());

        Button loadSwissNumber = new Button("Load +41798002320");
        loadSwissNumber.setOnAction(evt -> {
            try {
                field.load("+410798002320");
            } catch (NumberParseException e) {
                throw new RuntimeException(e);
            }
        });

        Button loadUSNumber = new Button("Load +12143456789");
        loadUSNumber.setOnAction(evt -> {
            try {
                field.load("+12143456789");
            } catch (NumberParseException e) {
                throw new RuntimeException(e);
            }
        });

        vBox.getChildren().addAll(new Separator(), clearButton, countryBox, expectedTypeBox, showExampleBox, countryCodeVisibleBox, showCountryCodeBox, disableCountryCodeBox, editableBox, loadSwissNumber, loadUSNumber);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(vBox);

        stage.setTitle("PhoneNumberField");
        stage.setScene(new Scene(scrollPane));
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}
