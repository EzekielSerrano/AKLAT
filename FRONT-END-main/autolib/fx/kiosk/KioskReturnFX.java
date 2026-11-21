package autolib.fx.kiosk;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.fx.StudentKioskFX;
import autolib.models.Book;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Kiosk Return panel — card per borrowed book, animated row. */
public class KioskReturnFX {

    private final StudentKioskFX kiosk;
    private final Student student;
    private final DataStore db = DataStore.get();
    private VBox listPanel;

    public KioskReturnFX(StudentKioskFX kiosk, Student student) {
        this.kiosk = kiosk;
        this.student = student;
    }

    public Node build() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color:#F4F6FA;");
        wrapper.setPadding(new Insets(20));

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox hdr = FXComponents.cardHeader("Books to Return", null);

        Label info = new Label("Select a book to return. Click the Return button to check it back in.");
        info.setFont(Font.font("Segoe UI", 11));
        info.setTextFill(Color.web(FXComponents.T_MUTED));
        info.setPadding(new Insets(0, 0, 10, 0));

        listPanel = new VBox(10);
        listPanel.setPadding(new Insets(10, 0, 0, 0));

        ScrollPane sp = new ScrollPane(listPanel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        refreshList();

        card.getChildren().addAll(hdr, info, sp);
        wrapper.getChildren().add(card);
        VBox.setVgrow(card, Priority.ALWAYS);
        return wrapper;
    }

    private void refreshList() {
        listPanel.getChildren().clear();

        // Use the smart "latest transaction per book" logic so returned books disappear
        List<Transaction> borrowed = KioskProfileCardFX.activeLoans(student);

        if (borrowed.isEmpty()) {
            Label empty = new Label("You don't have any books to return.");
            empty.setFont(Font.font("Segoe UI", 13));
            empty.setTextFill(Color.web(FXComponents.T_MUTED));
            empty.setAlignment(Pos.CENTER);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setPadding(new Insets(40, 0, 40, 0));
            listPanel.getChildren().add(empty);
        } else {
            for (Transaction tx : borrowed) listPanel.getChildren().add(returnRow(tx));
        }
    }

    private HBox returnRow(Transaction tx) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle("-fx-background-color:#FAFBFD; -fx-background-radius:10; -fx-border-radius:10; -fx-border-color:#E4E8F0; -fx-border-width:1; -fx-padding:14 16 14 16;");
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:#FDF2F3; -fx-background-radius:10; -fx-border-radius:10; -fx-border-color:#CC2936; -fx-border-width:1; -fx-padding:14 16 14 16;"));
        row.setOnMouseExited(e  -> row.setStyle("-fx-background-color:#FAFBFD; -fx-background-radius:10; -fx-border-radius:10; -fx-border-color:#E4E8F0; -fx-border-width:1; -fx-padding:14 16 14 16;"));
        FXComponents.addHoverLift(row);

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label titleL = new Label(tx.getBookTitle());
        titleL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        titleL.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label detailL = new Label(tx.getBookBarcode() + "  ·  Borrowed "
            + tx.getTimestamp().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        detailL.setFont(Font.font("Segoe UI", 10));
        detailL.setTextFill(Color.web(FXComponents.T_MUTED));

        info.getChildren().addAll(titleL, detailL);

        if (tx.getType() == Transaction.Type.OVERDUE) {
            Label ovdL = new Label("⚠ Overdue by " + tx.getDaysOverdue() + " day(s)");
            ovdL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            ovdL.setTextFill(Color.web(FXComponents.RED));
            info.getChildren().add(ovdL);
        }

        HBox right = new HBox(10);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().add(FXComponents.txBadge(tx.getTypeText()));

        Button retBtn = FXComponents.primaryButton("Return");
        retBtn.setPadding(new Insets(6, 16, 6, 16));
        retBtn.setOnAction(e -> doReturn(tx));
        right.getChildren().add(retBtn);

        row.getChildren().addAll(info, right);
        return row;
    }

    private void doReturn(Transaction tx) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Return \"" + tx.getBookTitle() + "\"?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                for (Book b : db.getBooks()) {
                    if (b.getBarcode().equals(tx.getBookBarcode())) {
                        b.setAvailableCopies(b.getAvailableCopies() + 1);
                        db.updateBookCopies(b);
                        break;
                    }
                }
                student.setActiveLoans(Math.max(0, student.getActiveLoans() - 1));
                if (tx.getType() == Transaction.Type.OVERDUE)
                    student.setOverdueCount(Math.max(0, student.getOverdueCount() - 1));
                db.updateStudentLoans(student);

                db.addTransaction(new Transaction(student.getName(), student.getStudentId(),
                    tx.getBookTitle(), tx.getBookBarcode(), Transaction.Type.RETURNED, LocalDateTime.now()));

                new Alert(Alert.AlertType.INFORMATION,
                    "\"" + tx.getBookTitle() + "\" has been returned. Thank you!", ButtonType.OK).showAndWait();
                kiosk.refreshDash();
                kiosk.refreshBorrow();
                refreshList();
            }
        });
    }
}

