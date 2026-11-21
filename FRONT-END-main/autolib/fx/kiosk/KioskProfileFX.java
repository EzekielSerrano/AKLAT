package autolib.fx.kiosk;

import autolib.fx.FXComponents;
import autolib.fx.StudentKioskFX;
import autolib.models.Student;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;

/** Kiosk Profile panel — student account info and borrowing rules. */
public class KioskProfileFX {

    private final StudentKioskFX kiosk;
    private final Student student;

    public KioskProfileFX(StudentKioskFX kiosk, Student student) {
        this.kiosk = kiosk;
        this.student = student;
    }

    public Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#F4F6FA;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color:#F4F6FA;");

        content.getChildren().addAll(
            buildProfileHeader(),
            buildDetailsCard(),
            buildRulesCard()
        );

        sp.setContent(content);
        return sp;
    }

    private HBox buildProfileHeader() {
        HBox card = new HBox(18);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);

        // Avatar
        String init = "";
        if (student.getName() != null) {
            String[] parts = student.getName().split("[,\\s]+");
            if (parts.length >= 2) init = parts[0].substring(0,1).toUpperCase() + parts[parts.length-1].substring(0,1).toUpperCase();
            else if (parts.length == 1) init = parts[0].substring(0,1).toUpperCase();
        }
        StackPane av = FXComponents.avatar(init, FXComponents.BLUE, 72);

        // Info
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameL = new Label(student.getName());
        nameL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        nameL.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label idL = new Label(student.getStudentId());
        idL.setFont(Font.font("Consolas", 14));
        idL.setTextFill(Color.web(FXComponents.T_MUTED));

        HBox badges = new HBox(8);
        badges.setPadding(new Insets(4, 0, 0, 0));
        badges.getChildren().addAll(
            FXComponents.badge(student.getDepartment(), "badge-blue"),
            FXComponents.badge(student.getYear(), "badge-gray"),
            FXComponents.badge(student.isNfcRegistered() ? "✓ NFC Registered" : "⏳ NFC Pending",
                student.isNfcRegistered() ? "badge-green" : "badge-amber")
        );

        info.getChildren().addAll(nameL, idL, badges);
        card.getChildren().addAll(av, info);
        FXComponents.addHoverLift(card);
        return card;
    }

    private VBox buildDetailsCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setMaxWidth(Double.MAX_VALUE);

        HBox hdr = FXComponents.cardHeader("Account Details", null);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(14);
        grid.setPadding(new Insets(14, 0, 0, 0));

        String[][] details = {
            {"Student ID",   student.getStudentId()},
            {"Department",   student.getDepartment()},
            {"Year Level",   student.getYear()},
            {"NFC Status",   student.getNfcStatusText()},
            {"Active Loans", String.valueOf(student.getActiveLoans())},
            {"Overdue Books",String.valueOf(student.getOverdueCount())}
        };
        for (int i = 0; i < details.length; i++) {
            VBox cell = new VBox(2);
            Label lbl = new Label(details[i][0]);
            lbl.setFont(Font.font("Segoe UI", 10));
            lbl.setTextFill(Color.web(FXComponents.T_MUTED));

            Label val = new Label(details[i][1]);
            val.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            val.setTextFill(
                (details[i][0].equals("Overdue Books") && Integer.parseInt(details[i][1]) > 0)
                    ? Color.web(FXComponents.RED) : Color.web(FXComponents.T_PRIMARY));

            cell.getChildren().addAll(lbl, val);
            grid.add(cell, i % 2, i / 2);
        }

        card.getChildren().addAll(hdr, grid);
        return card;
    }

    private VBox buildRulesCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setMaxWidth(Double.MAX_VALUE);

        HBox hdr = FXComponents.cardHeader("Borrowing Rules", null);
        VBox rules = new VBox(6);
        rules.setPadding(new Insets(14, 0, 0, 0));

        String[] rulesList = {
            "•  Maximum 5 books at a time",
            "•  Loan period: 7 days",
            "•  Renewal allowed once (additional 7 days)",
            "•  Overdue penalty: ₱5.00 per day",
            "•  Lost book: Full replacement cost + ₱50.00 processing fee"
        };
        for (String r : rulesList) {
            Label l = new Label(r);
            l.setFont(Font.font("Segoe UI", 13));
            l.setTextFill(Color.web(FXComponents.T_SECOND));
            rules.getChildren().add(l);
        }

        card.getChildren().addAll(hdr, rules);
        return card;
    }
}

