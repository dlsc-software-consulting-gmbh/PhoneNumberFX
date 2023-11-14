package com.dlsc.phonenumberfx.skins;

import com.dlsc.phonenumberfx.PhoneNumberField;
import com.dlsc.phonenumberfx.PhoneNumberField.Country;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.*;

public class PhoneNumberFieldSkin extends SkinBase<PhoneNumberField> {

    private static final Map<Country, Image> FLAG_IMAGES = new HashMap<>();

    static {
        for (Country country : Country.values()) {
            FLAG_IMAGES.put(country, new Image(Objects.requireNonNull(PhoneNumberField.class.getResource("country-flags/" + country.iso2Code().toLowerCase() + ".png")).toExternalForm()));
        }
    }

    private static final Comparator<Country> NAME_SORT_ASC = (c1, c2) -> {
        String c1Name = new Locale("en", c1.iso2Code()).getDisplayCountry();
        String c2Name = new Locale("en", c2.iso2Code()).getDisplayCountry();
        return c1Name.compareTo(c2Name);
    };

    private final ComboBox<Country> comboBox = new ComboBox<>();

    public PhoneNumberFieldSkin(PhoneNumberField field) {
        super(field);

        ObservableList<Country> countries = FXCollections.observableArrayList();
        Runnable callingCodesUpdater = () -> {
            Set<Country> temp1 = new TreeSet<>(NAME_SORT_ASC);
            Set<Country> temp2 = new TreeSet<>(NAME_SORT_ASC);

            field.getAvailableCountries().forEach(code -> {
                if (!field.getPreferredCountries().contains(code)) {
                    temp2.add(code);
                }
            });

            field.getPreferredCountries().forEach(code -> {
                if (field.getAvailableCountries().contains(code)) {
                    temp1.add(code);
                }
            });

            List<Country> temp = new ArrayList<>();
            temp.addAll(temp1);
            temp.addAll(temp2);
            countries.setAll(temp);

            if (field.getSelectedCountry() != null && !temp.contains(field.getSelectedCountry())) {
                field.setRawPhoneNumber(null); // Clear up the value in case the country code is not available anymore
            }
        };

        InvalidationListener listener = obs -> callingCodesUpdater.run();
        field.getAvailableCountries().addListener(listener);
        field.getPreferredCountries().addListener(listener);
        field.countryCellFactoryProperty().addListener(listener);
        callingCodesUpdater.run();

        PhoneNumberEditor editor = new PhoneNumberEditor(field.getEditor());

        field.setCountryCellFactory(listView -> new CountryCell());

        comboBox.setButtonCell(editor);
        comboBox.cellFactoryProperty().bind(field.countryCellFactoryProperty());
        comboBox.setItems(countries);
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.setMaxHeight(Double.MAX_VALUE);
        comboBox.setFocusTraversable(false);
        comboBox.valueProperty().bindBidirectional(field.selectedCountryProperty());

        // Manually handle mouse event either on the text field or the trigger button box
        field.addEventFilter(MouseEvent.MOUSE_RELEASED, evt -> {
            Bounds buttonBounds = editor.buttonBox.getBoundsInParent();
            if (buttonBounds.contains(evt.getX(), evt.getY())) {
                if (!editor.buttonBox.isDisabled()) {
                    editor.buttonBox.requestFocus();
                    if (!comboBox.isShowing()) {
                        comboBox.show();
                    }
                }
            } else {
                comboBox.hide();
                Bounds textFieldBounds = editor.textField.getBoundsInParent();
                if (textFieldBounds.contains(evt.getX(), evt.getY())) {
                    editor.textField.requestFocus();
                    if (editor.textField.getText() != null) {
                        editor.textField.positionCaret(editor.textField.getText().length());
                    } else {
                        editor.textField.positionCaret(0);
                    }
                }
            }
            evt.consume();
        });

        getChildren().addAll(comboBox);
    }

    private final class PhoneNumberEditor extends ListCell<Country> {

        final TextField textField;
        final HBox buttonBox = new HBox();

        public PhoneNumberEditor(TextField textField) {
            getStyleClass().add("editor");

            this.textField = textField;

            StackPane flagBox = new StackPane();
            flagBox.getStyleClass().add("flag-box");

            Runnable flagUpdater = () -> flagBox.getChildren().setAll(getCountryGraphic(getSkinnable().getSelectedCountry()));
            getSkinnable().selectedCountryProperty().addListener(obs -> flagUpdater.run());
            getSkinnable().countryCellFactoryProperty().addListener(obs -> flagUpdater.run());
            flagUpdater.run();

            Region arrow = new Region();
            arrow.getStyleClass().add("arrow");

            StackPane arrowButton = new StackPane();
            arrowButton.getStyleClass().add("arrow-button");
            arrowButton.getChildren().add(arrow);

            buttonBox.getStyleClass().add("button-box");
            buttonBox.getChildren().addAll(flagBox, arrowButton);
            buttonBox.managedProperty().bind(buttonBox.visibleProperty());
            buttonBox.disableProperty().bind(getSkinnable().disableCountryDropdownProperty());
        }

        @Override
        protected Skin<?> createDefaultSkin() {

            return new SkinBase<>(this) {
                {
                    getChildren().addAll(buttonBox, textField);
                }

                @Override
                protected void layoutChildren(double x, double y, double w, double h) {
                    final double buttonWidth = snapSizeX(buttonBox.prefWidth(-1));
                    buttonBox.resizeRelocate(x, y, buttonWidth, h);

                    final double textFieldX = snapPositionX(x + buttonWidth);
                    textField.resizeRelocate(textFieldX, y, w - buttonWidth, h);
                }
            };
        }
    }

    private class CountryCell extends ListCell<Country> {

        private CountryCell() {
            getStyleClass().add("country-cell");
        }

        @Override
        public String getUserAgentStylesheet() {
            return getSkinnable().getUserAgentStylesheet();
        }

        @Override
        protected void updateItem(Country country, boolean empty) {
            super.updateItem(country, empty);

            int index = -1;

            if (country != null && !empty) {
                setText(new Locale("en", country.iso2Code()).getDisplayCountry());
                setGraphic(getCountryGraphic(country));
                index = getSkinnable().getPreferredCountries().indexOf(country);
            } else {
                setText(null);
                setGraphic(null);
            }

            if (index >= 0) {
                getStyleClass().add("preferred");
                if (index == getSkinnable().getPreferredCountries().size() - 1) {
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
        if (country != null) {
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

        Region globeRegion = new Region();
        globeRegion.getStyleClass().add("globe");
        return globeRegion;
    }
}
