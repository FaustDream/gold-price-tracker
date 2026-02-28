module com.goldpricetracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires okhttp3;
    requires com.fasterxml.jackson.databind;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.slf4j;
    requires java.desktop;

    opens com.goldpricetracker to javafx.graphics, javafx.fxml;
    opens com.goldpricetracker.frontend to javafx.fxml;
    opens com.goldpricetracker.backend to com.fasterxml.jackson.databind;

    exports com.goldpricetracker;
    exports com.goldpricetracker.frontend;
    exports com.goldpricetracker.backend;
}
