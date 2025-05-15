module com.dlsc.phonenumberfx {
    requires javafx.base;
    requires javafx.graphics;
    requires transitive javafx.controls;

    requires org.controlsfx.controls;
    requires com.google.i18n.phonenumbers.libphonenumber;

    exports com.dlsc.phonenumberfx;
}