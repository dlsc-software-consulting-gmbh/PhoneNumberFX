package com.dlsc.phonenumberfx.demo;

import com.dlsc.phonenumberfx.PhoneNumberField;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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

        CheckBox editableBox = new CheckBox("Editable");
        editableBox.selectedProperty().bindBidirectional(field.editableProperty());

        vBox.getChildren().addAll(new Separator(), clearButton, showExampleBox, countryCodeVisibleBox, disableCountryCodeBox, editableBox);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(vBox);

        stage.setTitle("PhoneNumberField");
        stage.setScene(new Scene(scrollPane, 900, 400));
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}
