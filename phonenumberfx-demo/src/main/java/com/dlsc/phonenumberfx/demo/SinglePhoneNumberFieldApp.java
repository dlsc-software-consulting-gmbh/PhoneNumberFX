package com.dlsc.phonenumberfx.demo;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SinglePhoneNumberFieldApp extends Application {

    @Override
    public void start(Stage stage) {
        VBox vBox = new VBox(20);
        vBox.setPadding(new Insets(20));
        vBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(
            PhoneNumberFieldSamples.buildCountryCodeVisibleSample()
        );

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
