module com.dlsc.phonenumberfx {
    requires javafx.base;
    requires javafx.graphics;
    requires transitive javafx.controls;

    requires org.controlsfx.controls;
    requires libphonenumber;

    exports com.dlsc.phonenumberfx;
}