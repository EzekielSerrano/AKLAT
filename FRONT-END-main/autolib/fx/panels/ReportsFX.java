package autolib.fx.panels;

import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;

/** Reports panel — clickable report cards with hover lift animation. */
public class ReportsFX {

    private final LibrarianPortalFX portal;

    public ReportsFX(LibrarianPortalFX portal) { this.portal = portal; }

    public Node build() {
        VBox wrapper = new VBox(16);
        wrapper.setStyle("-fx-background-color:#F4F6FA;");
        wrapper.setPadding(new Insets(20));

        // 2×3 grid of report cards
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);

        String[][] reports = {
            {"📊", "Borrowing Summary",   "Daily / weekly / monthly overview"},
            {"⏰", "Overdue Report",       "Students with past-due books"},
            {"📚", "Inventory Report",     "Stock levels by section"},
            {"👤", "Student Activity",    "Borrow/return history per student"},
            {"⭐", "Popular Books",        "Most borrowed titles this month"},
            {"💾", "Export Data",          "Download CSV or PDF reports"}
        };

        for (int i = 0; i < reports.length; i++) {
            String[] r = reports[i];
            VBox card = buildReportCard(r[0], r[1], r[2]);
            grid.add(card, i % 3, i / 3);
            GridPane.setHgrow(card, Priority.ALWAYS);
        }

        // Fill columns equally
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }

        Label hint = new Label("Select a report type above to generate it.");
        hint.setFont(Font.font("Segoe UI", 13));
        hint.setTextFill(Color.web(FXComponents.T_MUTED));
        hint.setAlignment(Pos.CENTER);
        hint.setMaxWidth(Double.MAX_VALUE);

        wrapper.getChildren().addAll(grid, hint);
        return wrapper;
    }

    private VBox buildReportCard(String icon, String title, String sub) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(20));
        card.setCursor(javafx.scene.Cursor.HAND);

        Label iconLbl = new Label(icon);
        iconLbl.setFont(Font.font("Segoe UI Emoji", 26));
        iconLbl.setPadding(new Insets(0, 0, 4, 0));

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        titleLbl.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label subLbl = new Label(sub);
        subLbl.setFont(Font.font("Segoe UI", 11));
        subLbl.setTextFill(Color.web(FXComponents.T_MUTED));
        subLbl.setWrapText(true);

        Button genBtn = FXComponents.primaryButton("Generate");
        genBtn.setPadding(new Insets(7, 14, 7, 14));
        genBtn.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, title + " will be generated.", ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Report");
            a.showAndWait();
        });

        card.getChildren().addAll(iconLbl, titleLbl, subLbl, genBtn);
        FXComponents.addHoverLift(card);
        return card;
    }
}

