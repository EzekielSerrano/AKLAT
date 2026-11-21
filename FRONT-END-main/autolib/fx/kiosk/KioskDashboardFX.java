package autolib.fx.kiosk;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.fx.StudentKioskFX;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Kiosk dashboard — stat cards + borrowed books + quick actions. */
public class KioskDashboardFX {

    private final StudentKioskFX kiosk;
    private final Student student;
    private final DataStore db = DataStore.get();

    public KioskDashboardFX(StudentKioskFX kiosk, Student student) {
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

        // Stat row
        HBox statsRow = new HBox(12);
        statsRow.setMaxWidth(Double.MAX_VALUE);
        int remaining = 5 - student.getActiveLoans();
        VBox c1 = FXComponents.statCard("📚", student.getActiveLoans(), "Active Loans",
            student.getActiveLoans() > 0 ? "Books in your possession" : "No active loans", FXComponents.BLUE);
        VBox c2 = FXComponents.statCard("⏰", student.getOverdueCount(), "Overdue",
            student.getOverdueCount() > 0 ? "Return ASAP!" : "All good!", student.getOverdueCount() > 0 ? FXComponents.RED : FXComponents.GREEN);
        VBox c3 = FXComponents.statCard("⊞", remaining, "Remaining Slots",
            "Max 5 loans at a time", FXComponents.T_MUTED);
        VBox c4 = FXComponents.statCard("⭐", student.isNfcRegistered() ? 1 : 0, "NFC Status",
            student.isNfcRegistered() ? "Registered" : "Not registered", student.isNfcRegistered() ? FXComponents.GREEN : FXComponents.ORANGE);
        // Override NFC number display
        ((Label) c4.getChildren().get(1)).setText(student.isNfcRegistered() ? "Yes" : "No");

        for (VBox c : new VBox[]{c1, c2, c3, c4}) { HBox.setHgrow(c, Priority.ALWAYS); statsRow.getChildren().add(c); }

        // Mid row
        HBox midRow = new HBox(12);
        midRow.setMaxWidth(Double.MAX_VALUE);
        Node borrowedCard = buildBorrowedCard();
        Node activityCard = buildActivityCard();
        HBox.setHgrow(borrowedCard, Priority.ALWAYS);
        HBox.setHgrow(activityCard, Priority.ALWAYS);
        midRow.getChildren().addAll(borrowedCard, activityCard);

        // Quick actions
        VBox qCard = new VBox(10);
        qCard.getStyleClass().add("card");
        qCard.setPadding(new Insets(16, 18, 16, 18));

        HBox hdr = FXComponents.cardHeader("Quick Actions", null);

        HBox btns = new HBox(10);
        Button borrowBtn = FXComponents.primaryButton("⊞  Borrow a Book");
        Button returnBtn = FXComponents.ghostButton("↩  Return a Book");
        Button histBtn   = FXComponents.ghostButton("⇄  View History");
        borrowBtn.setOnAction(e -> kiosk.navigateTo("Borrow a Book"));
        returnBtn.setOnAction(e -> kiosk.navigateTo("Return a Book"));
        histBtn.setOnAction(e   -> kiosk.navigateTo("My History"));
        btns.getChildren().addAll(borrowBtn, returnBtn, histBtn);

        qCard.getChildren().addAll(hdr, btns);

        content.getChildren().addAll(statsRow, midRow, qCard);
        sp.setContent(content);
        return sp;
    }

    private Node buildBorrowedCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 12, 18));

        HBox hdr = FXComponents.cardHeader("My Borrowed Books", null);
        VBox list = new VBox(4);
        list.setPadding(new Insets(10, 0, 0, 0));

        boolean found = false;
        for (Transaction tx : db.getTransactions()) {
            if (tx.getStudentId().equals(student.getStudentId())
                    && (tx.getType() == Transaction.Type.BORROWED || tx.getType() == Transaction.Type.OVERDUE)) {
                list.getChildren().add(borrowedRow(tx));
                found = true;
            }
        }
        if (!found) {
            Label empty = new Label("No books currently borrowed.");
            empty.setFont(Font.font("Segoe UI", 13));
            empty.setTextFill(Color.web(FXComponents.T_MUTED));
            empty.setPadding(new Insets(20, 0, 20, 0));
            list.getChildren().add(empty);
        }
        card.getChildren().addAll(hdr, list);
        return card;
    }

    private HBox borrowedRow(Transaction tx) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        boolean overdue = tx.getType() == Transaction.Type.OVERDUE;
        row.setStyle("-fx-border-color:transparent transparent transparent " +
            (overdue ? FXComponents.RED : FXComponents.BLUE) + "; -fx-border-width:0 0 0 3; -fx-padding:8 0 8 10;");

        VBox info = new VBox(1);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label titleL = new Label(tx.getBookTitle());
        titleL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        titleL.setTextFill(Color.web(FXComponents.T_PRIMARY));
        Label barL = new Label(tx.getBookBarcode());
        barL.setFont(Font.font("Consolas", 11));
        barL.setTextFill(Color.web(FXComponents.T_MUTED));
        info.getChildren().addAll(titleL, barL);

        row.getChildren().addAll(info, FXComponents.txBadge(tx.getTypeText()));
        return row;
    }

    private Node buildActivityCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 12, 18));

        HBox hdr = FXComponents.cardHeader("Recent Activity", null);
        VBox list = new VBox(2);
        list.setPadding(new Insets(10, 0, 0, 0));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, h:mm a");
        boolean found = false;
        for (Transaction tx : db.getTransactions()) {
            if (tx.getStudentId().equals(student.getStudentId())) {
                HBox row = new HBox(0);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 0, 6, 0));
                row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:#FDF2F3; -fx-background-radius:6;"));
                row.setOnMouseExited(e  -> row.setStyle("-fx-background-color:transparent;"));

                VBox left = new VBox(1);
                HBox.setHgrow(left, Priority.ALWAYS);
                Label bookL = new Label(tx.getBookTitle());
                bookL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                bookL.setTextFill(Color.web(FXComponents.T_PRIMARY));
                Label timeL = new Label(tx.getTimestamp().format(fmt));
                timeL.setFont(Font.font("Segoe UI", 10));
                timeL.setTextFill(Color.web(FXComponents.T_MUTED));
                left.getChildren().addAll(bookL, timeL);
                row.getChildren().addAll(left, FXComponents.txBadge(tx.getTypeText()));
                list.getChildren().add(row);
                found = true;
            }
        }
        if (!found) {
            Label empty = new Label("No recent activity.");
            empty.setFont(Font.font("Segoe UI", 13));
            empty.setTextFill(Color.web(FXComponents.T_MUTED));
            list.getChildren().add(empty);
        }
        card.getChildren().addAll(hdr, list);
        return card;
    }
}

