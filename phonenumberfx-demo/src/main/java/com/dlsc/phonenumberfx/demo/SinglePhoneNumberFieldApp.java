package com.dlsc.phonenumberfx.demo;

import com.dlsc.phonenumberfx.PhoneNumberField;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.converter.DateTimeStringConverter;
import org.scenicview.ScenicView;

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

        CheckBox countryCodeVisibleBox = new CheckBox("Show country code");
        countryCodeVisibleBox.selectedProperty().bindBidirectional(field.countryCodeVisibleProperty());

        CheckBox disableCountryCodeBox = new CheckBox("Disable country dropdown");
        disableCountryCodeBox.selectedProperty().bindBidirectional(field.disableCountryDropdownProperty());

        CheckBox showCountryCodeBox = new CheckBox("Show country dropdown");
        showCountryCodeBox.selectedProperty().bindBidirectional(field.showCountryDropdownProperty());

        CheckBox editableBox = new CheckBox("Editable");
        editableBox.selectedProperty().bindBidirectional(field.editableProperty());

        CheckBox liveFormatting = new CheckBox("Live Formatting");
        liveFormatting.selectedProperty().bindBidirectional(field.liveFormattingProperty());

        ComboBox<PhoneNumberUtil.PhoneNumberType> expectedTypeBox = new ComboBox<>();
        expectedTypeBox.getItems().setAll(PhoneNumberUtil.PhoneNumberType.values());
        expectedTypeBox.valueProperty().bindBidirectional(field.expectedPhoneNumberTypeProperty());

        ComboBox<PhoneNumberField.Country> countryBox = new ComboBox<>();
        countryBox.getItems().setAll(PhoneNumberField.Country.values());
        countryBox.valueProperty().bindBidirectional(field.selectedCountryProperty());

        Button loadSwissNumber = new Button("Swiss number: +41798002320");
        loadSwissNumber.setOnAction(evt -> field.setValue("+410798002320"));

        Button loadUSNumber1 = new Button("Canadian number: +15871234567");
        loadUSNumber1.setOnAction(evt -> field.setValue("+15871234567"));

        Button loadUSNumber2 = new Button("White house: +12024561111");
        loadUSNumber2.setOnAction(evt -> field.setValue("+12024561111"));

        Button loadBadly = new Button("Bad input: 2024561111");
        loadBadly.setOnAction(evt -> field.setValue("2024561111"));

        HBox loaderBox = new HBox(10, loadSwissNumber, loadUSNumber1, loadUSNumber2, loadBadly);
        vBox.getChildren().addAll(new Separator(), clearButton, countryBox, expectedTypeBox, liveFormatting, showExampleBox, countryCodeVisibleBox, showCountryCodeBox, disableCountryCodeBox, editableBox, loaderBox);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(vBox);

        Scene scene = new Scene(scrollPane);

        stage.setTitle("PhoneNumberField");
        stage.setScene(scene);
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}
