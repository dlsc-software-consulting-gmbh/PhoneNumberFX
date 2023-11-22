package com.dlsc.phonenumberfx.demo;

import com.dlsc.phonenumberfx.PhoneNumberField;
import com.dlsc.phonenumberfx.PhoneNumberField.Country;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Function;

public class SinglePhoneNumberFieldApp extends Application {

    private static final Function<Object, String> COUNTRY_CODE_CONVERTER = c -> {
        if (c == null) {
            return null;
        }
        Country code = (Country) c;
        return "(" + code.phonePrefix() + ")" + code;
    };

    @Override
    public void start(Stage stage) {
        VBox vBox = new VBox(20);
        vBox.setPadding(new Insets(20));
        vBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(
            buildDefaultEmptySample()
        );

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(vBox);

        stage.setTitle("PhoneNumberField");
        stage.setScene(new Scene(scrollPane, 900, 800));
        stage.sizeToScene();
        stage.centerOnScreen();
        stage.show();
    }

    private Node buildDefaultEmptySample() {
        PhoneNumberField field = new PhoneNumberField();
        field.setExpectedPhoneNumberType(PhoneNumberUtil.PhoneNumberType.MOBILE);
        field.setSelectedCountry(Country.COLOMBIA);

        String title = "Phone Number Field";
        String description = "General purpose phone number field.";

        return buildSample(title, description, field);
    }

    private Node buildSample(String title, String description, PhoneNumberField field) {
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

        addField(rightBox, "Country Code", field.selectedCountryProperty(), COUNTRY_CODE_CONVERTER);
        addField(rightBox, "Raw Number", field.rawPhoneNumberProperty());
        addField(rightBox, "E164 Format", field.e164PhoneNumberProperty());
        addField(rightBox, "National Format", field.nationalPhoneNumberProperty());

        HBox hBox = new HBox(30);
        hBox.getChildren().addAll(leftBox, rightBox);
        hBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftBox, Priority.NEVER);
        HBox.setHgrow(rightBox, Priority.ALWAYS);

        return hBox;
    }

    private void addField(GridPane pane, String name, ObservableValue<?> value) {
        addField(pane, name, value, null);
    }

    private void addField(GridPane pane, String name, ObservableValue<?> value, Function<Object, String> converter) {
        Label valueLbl = new Label();
        if (converter == null) {
            valueLbl.textProperty().bind(Bindings.convert(value));
        } else {
            valueLbl.textProperty().bind(Bindings.createStringBinding(() -> converter.apply(value.getValue()), value));
        }

        valueLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 1.2em; -fx-font-weight: bold; -fx-padding: 0 0 0 10;");

        int row = pane.getRowCount();
        pane.add(new Label(name + ":"), 0, row);
        pane.add(valueLbl, 1, row);
    }

    public static void main(String[] args) {
        launch();
    }

}
