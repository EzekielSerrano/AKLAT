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
import javafx.beans.property.SimpleStringProperty;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Kiosk History panel — full transaction log for the student. */
public class KioskHistoryFX {

    private final StudentKioskFX kiosk;
    private final Student student;
    private final DataStore db = DataStore.get();

    public KioskHistoryFX(StudentKioskFX kiosk, Student student) {
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

        HBox hdr = FXComponents.cardHeader("Transaction History", null);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a");

        List<Transaction> myTx = new ArrayList<>();
        for (Transaction tx : db.getTransactions()) {
            if (tx.getStudentId().equals(student.getStudentId())) myTx.add(tx);
        }

        TableView<Transaction> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Transaction, String> timeCol = new TableColumn<>("DATE & TIME");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestamp().format(fmt)));
        timeCol.setPrefWidth(180);

        TableColumn<Transaction, String> bookCol = new TableColumn<>("BOOK");
        bookCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBookTitle()));
        bookCol.setPrefWidth(260);

        TableColumn<Transaction, String> barcodeCol = new TableColumn<>("BARCODE");
        barcodeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBookBarcode()));
        barcodeCol.setPrefWidth(100);
        barcodeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else { setText(item); setFont(Font.font("Consolas", 12)); setTextFill(Color.web(FXComponents.T_MUTED)); }
            }
        });

        TableColumn<Transaction, String> typeCol = new TableColumn<>("TYPE");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTypeText()));
        typeCol.setPrefWidth(90);
        typeCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else setGraphic(FXComponents.txBadge(item));
            }
        });

        table.getColumns().addAll(timeCol, bookCol, barcodeCol, typeCol);
        table.getItems().addAll(myTx);

        table.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseEntered(e -> { if (!row.isEmpty()) row.setStyle("-fx-background-color:#FDF2F3;"); });
            row.setOnMouseExited(e -> row.setStyle(""));
            return row;
        });

        Label countLbl = new Label("Showing " + myTx.size() + " transaction(s)");
        countLbl.setFont(Font.font("Segoe UI", 11));
        countLbl.setTextFill(Color.web(FXComponents.T_MUTED));

        if (myTx.isEmpty()) {
            Label empty = new Label("No transaction history found.");
            empty.setFont(Font.font("Segoe UI", 13));
            empty.setTextFill(Color.web(FXComponents.T_MUTED));
            empty.setAlignment(Pos.CENTER);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setPadding(new Insets(40, 0, 40, 0));
            card.getChildren().addAll(hdr, empty);
        } else {
            card.getChildren().addAll(hdr, table, countLbl);
        }

        wrapper.getChildren().add(card);
        VBox.setVgrow(card, Priority.ALWAYS);
        return wrapper;
    }
}

