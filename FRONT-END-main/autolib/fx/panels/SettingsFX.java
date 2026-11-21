package autolib.fx.panels;

import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;

/** Settings panel — library info form + device status. */
public class SettingsFX {

    private final LibrarianPortalFX portal;

    public SettingsFX(LibrarianPortalFX portal) { this.portal = portal; }

    public Node build() {
        HBox wrapper = new HBox(14);
        wrapper.setStyle("-fx-background-color:#F4F6FA;");
        wrapper.setPadding(new Insets(20));

        // Left: Library info
        VBox leftCard = new VBox(0);
        leftCard.getStyleClass().add("card");
        leftCard.setPadding(new Insets(20, 22, 20, 22));
        HBox.setHgrow(leftCard, Priority.ALWAYS);

        Label t1 = new Label("Library Information");
        t1.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        t1.setTextFill(Color.web(FXComponents.T_PRIMARY));
        t1.setPadding(new Insets(0, 0, 16, 0));

        leftCard.getChildren().add(t1);
        leftCard.getChildren().add(settingRow("Library Name",    "Batangas State University Library"));
        leftCard.getChildren().add(settingRow("System Name",     "A.K.L.A.T. (Automated Key Library Analytics & Tracking)"));
        leftCard.getChildren().add(settingRow("System Version",  "A.K.L.A.T. v2.0.0 (JavaFX)"));
        leftCard.getChildren().add(settingRow("Admin Email",     "library@batstate-u.edu.ph"));
        leftCard.getChildren().add(settingRow("Loan Duration",   "7 days"));
        leftCard.getChildren().add(settingRow("Max Active Loans","5 books per student"));

        Button saveBtn = FXComponents.primaryButton("Save Changes");
        saveBtn.setPadding(new Insets(9, 20, 9, 20));
        VBox.setMargin(saveBtn, new Insets(20, 0, 0, 0));
        saveBtn.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully.", ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Saved");
            a.showAndWait();
        });
        leftCard.getChildren().add(saveBtn);

        // Right: Device status
        VBox rightCard = new VBox(0);
        rightCard.getStyleClass().add("card");
        rightCard.setPadding(new Insets(20, 22, 20, 22));
        HBox.setHgrow(rightCard, Priority.ALWAYS);

        Label t2 = new Label("Device Status");
        t2.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        t2.setTextFill(Color.web(FXComponents.T_PRIMARY));
        t2.setPadding(new Insets(0, 0, 16, 0));

        rightCard.getChildren().add(t2);
        rightCard.getChildren().add(deviceRow("NFC Reader",        "Online",     true));
        rightCard.getChildren().add(deviceRow("Barcode Scanner",   "Online",     true));
        rightCard.getChildren().add(deviceRow("Receipt Printer",   "Offline",    false));
        rightCard.getChildren().add(deviceRow("PostgreSQL Database","Connected",  true));

        wrapper.getChildren().addAll(leftCard, rightCard);
        return wrapper;
    }

    private VBox settingRow(String label, String value) {
        VBox col = new VBox(4);
        col.setPadding(new Insets(0, 0, 12, 0));
        col.setMaxWidth(Double.MAX_VALUE);

        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", 11));
        lbl.setTextFill(Color.web(FXComponents.T_MUTED));

        TextField val = FXComponents.inputField(value);
        val.setText(value);
        val.setMaxWidth(Double.MAX_VALUE);
        val.setPrefHeight(34);

        col.getChildren().addAll(lbl, val);
        return col;
    }

    private HBox deviceRow(String device, String status, boolean online) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 0, 10, 0));
        row.setMaxWidth(Double.MAX_VALUE);

        Label devLbl = new Label(device);
        devLbl.setFont(Font.font("Segoe UI", 13));
        devLbl.setTextFill(Color.web(FXComponents.T_PRIMARY));
        HBox.setHgrow(devLbl, Priority.ALWAYS);

        HBox statusBox = FXComponents.statusDot(status, online);

        row.getChildren().addAll(devLbl, statusBox);
        return row;
    }
}

