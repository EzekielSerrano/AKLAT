package autolib.fx.panels;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import autolib.models.Transaction;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.beans.property.SimpleStringProperty;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Transactions panel. */
public class TransactionsFX {

    private final LibrarianPortalFX portal;
    private final DataStore db = DataStore.get();

    public TransactionsFX(LibrarianPortalFX portal) { this.portal = portal; }

    public Node build() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color:#F4F6FA;");
        wrapper.setPadding(new Insets(20));

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        VBox.setVgrow(card, Priority.ALWAYS);

        Label heading = new Label("All Transactions");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web(FXComponents.T_PRIMARY));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a");
        TableView<Transaction> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Transaction, String> timeCol = new TableColumn<>("DATE & TIME");
        timeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestamp().format(fmt)));
        timeCol.setPrefWidth(170);

        TableColumn<Transaction, String> studentCol = new TableColumn<>("STUDENT");
        studentCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStudentName()));
        studentCol.setPrefWidth(140);

        TableColumn<Transaction, String> idCol = new TableColumn<>("STUDENT ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStudentId()));
        idCol.setPrefWidth(100);
        idCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else { setText(item); setFont(Font.font("Consolas", 12)); setTextFill(Color.web(FXComponents.T_MUTED)); }
            }
        });

        TableColumn<Transaction, String> bookCol = new TableColumn<>("BOOK TITLE");
        bookCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBookTitle()));
        bookCol.setPrefWidth(220);

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

        table.getColumns().addAll(timeCol, studentCol, idCol, bookCol, typeCol);
        table.getItems().addAll(db.getTransactions());

        table.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseEntered(e -> { if (!row.isEmpty()) row.setStyle("-fx-background-color:#FDF2F3;"); });
            row.setOnMouseExited(e -> row.setStyle(""));
            return row;
        });

        Label countLbl = new Label("Showing " + db.getTransactions().size() + " transaction(s)");
        countLbl.setFont(Font.font("Segoe UI", 11));
        countLbl.setTextFill(Color.web(FXComponents.T_MUTED));

        card.getChildren().addAll(heading, table, countLbl);
        wrapper.getChildren().add(card);
        VBox.setVgrow(card, Priority.ALWAYS);
        return wrapper;
    }
}

