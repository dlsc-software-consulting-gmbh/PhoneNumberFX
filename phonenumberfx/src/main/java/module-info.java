module com.dlsc.phonenumberfx {
    requires javafx.base;
    requires javafx.graphics;
    requires transitive javafx.controls;

    requires libphonenumber;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.bootstrapicons;

    exports com.dlsc.phonenumberfx;
    exports com.dlsc.phonenumberfx.skins;
}