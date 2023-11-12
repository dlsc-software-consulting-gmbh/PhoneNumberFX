module com.dlsc.phonenumberfx {
    requires javafx.base;
    requires javafx.graphics;
    requires transitive javafx.controls;

    requires libphonenumber;

    exports com.dlsc.phonenumberfx;
    exports com.dlsc.phonenumberfx.skins;
}