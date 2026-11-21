package autolib.fx.panels;

import autolib.DataStore;
import autolib.IsbnLookupService;
import autolib.IsbnLookupService.BookInfo;
import autolib.fx.CameraBarcodeScannerFX;
import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import autolib.models.Book;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Book Catalog panel — searchable, filterable table with animated rows.
 * Includes ISBN scanner / lookup via Open Library & Google Books APIs,
 * camera barcode scanner, live feedback banners, and robust input validation.
 */
public class BookCatalogFX {

    private final LibrarianPortalFX portal;
    private final DataStore db = DataStore.get();

    private TextField searchField;
    private ComboBox<String> sectionCombo;
    private ComboBox<String> statusCombo;
    private TableView<Book> table;
    private Label countLabel;

    // Feedback banner at top of the panel
    private HBox banner;
    private Label bannerLabel;

    public BookCatalogFX(LibrarianPortalFX portal) {
        this.portal = portal;
    }

    public Node build() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color:#F4F6FA;");
        wrapper.setPadding(new Insets(20));

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        VBox.setVgrow(card, Priority.ALWAYS);

        // ── Banner for feedback ─────────────────────────────────────────
        banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10, 14, 10, 14));
        banner.setVisible(false);
        banner.setManaged(false);

        bannerLabel = new Label();
        bannerLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        HBox.setHgrow(bannerLabel, Priority.ALWAYS);
        banner.getChildren().add(bannerLabel);

        // ── Toolbar ────────────────────────────────────────────────────
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("\uD83D\uDD0D  Search by title, author, barcode, ISBN\u2026");
        searchField.getStyleClass().add("input-field");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((o, ov, nv) -> refresh());

        sectionCombo = FXComponents.styledCombo(new String[]{"All Sections"});
        updateSectionCombo();
        sectionCombo.setOnAction(e -> refresh());

        statusCombo = FXComponents.styledCombo(new String[]{"All Status", "Available", "Borrowed", "All Out"});
        statusCombo.setOnAction(e -> refresh());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = FXComponents.primaryButton("+ Add Book");
        addBtn.setOnAction(e -> showAddBookDialog());

        toolbar.getChildren().addAll(searchField, sectionCombo, statusCombo, spacer, addBtn);

        // ── Table ──────────────────────────────────────────────────────
        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Book, String> barcodeCol = col("BARCODE", 90);
        barcodeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBarcode()));
        barcodeCol.setCellFactory(tc -> monoCell());

        TableColumn<Book, String> titleCol = col("TITLE", 200);
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));

        TableColumn<Book, String> authCol = col("AUTHOR", 130);
        authCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAuthor()));

        TableColumn<Book, String> secCol = col("SECTION", 110);
        secCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSection()));

        TableColumn<Book, String> availCol = col("AVAILABLE", 80);
        availCol.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getAvailableCopies() + "/" + d.getValue().getTotalCopies()));

        TableColumn<Book, String> statusCol = col("STATUS", 90);
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatusText()));
        statusCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); }
                else { setGraphic(FXComponents.bookStatusBadge(item)); }
            }
        });

        TableColumn<Book, Void> actionsCol = col("ACTIONS", 80);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            final Button del = new Button("\uD83D\uDDD1");
            {
                del.getStyleClass().add("btn-icon");
                del.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0 && idx < table.getItems().size()) {
                        Book b = table.getItems().get(idx);
                        db.removeBook(idx);
                        showBanner("Book \"" + b.getTitle() + "\" deleted.", "warn");
                        refresh();
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : del);
            }
        });

        table.getColumns().addAll(barcodeCol, titleCol, authCol, secCol, availCol, statusCol, actionsCol);

        // Animated row hover
        table.setRowFactory(tv -> {
            TableRow<Book> row = new TableRow<>();
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) row.setStyle("-fx-background-color:#FDF2F3;");
            });
            row.setOnMouseExited(e -> row.setStyle(""));
            return row;
        });

        countLabel = new Label();
        countLabel.setFont(Font.font("Segoe UI", 11));
        countLabel.setTextFill(Color.web(FXComponents.T_MUTED));

        refresh();

        card.getChildren().addAll(banner, toolbar, table, countLabel);
        wrapper.getChildren().add(card);
        VBox.setVgrow(card, Priority.ALWAYS);
        return wrapper;
    }

    private void updateSectionCombo() {
        if (sectionCombo == null) return;
        String cur = sectionCombo.getValue();
        List<String> sections = db.getSections();
        List<String> items = new ArrayList<>();
        items.add("All Sections");
        for (String s : sections) {
            if (s != null && !s.trim().isEmpty() && !items.contains(s.trim())) {
                items.add(s.trim());
            }
        }
        sectionCombo.getItems().setAll(items);
        if (cur != null && items.contains(cur)) {
            sectionCombo.setValue(cur);
        } else {
            sectionCombo.setValue("All Sections");
        }
    }

    private void refresh() {
        String q   = searchField  == null ? ""             : searchField.getText().trim();
        String sec = sectionCombo == null ? "All Sections" : sectionCombo.getValue();
        String st  = statusCombo  == null ? "All Status"   : statusCombo.getValue();
        List<Book> books = db.searchBooks(q, sec, st);
        table.getItems().setAll(books);
        countLabel.setText("Showing " + books.size() + " book(s)");
    }

    private void showBanner(String msg, String type) {
        String c = "success".equals(type) ? FXComponents.GREEN : ("error".equals(type) ? FXComponents.RED : FXComponents.AMBER);
        bannerLabel.setText(msg);
        bannerLabel.setTextFill(Color.web(c));
        banner.setStyle("-fx-background-color:" + c + "18; -fx-background-radius:8; -fx-border-color:" + c + "44; -fx-border-radius:8;");
        banner.setVisible(true);
        banner.setManaged(true);
        FXComponents.fadeInSlide(banner);
    }

    // ══════════════════════════════════════════════════════════════════
    //  ADD BOOK DIALOG (with ISBN lookup, camera scan & validation)
    // ══════════════════════════════════════════════════════════════════

    private void showAddBookDialog() {
        Dialog<Book> dlg = new Dialog<>();
        dlg.setTitle("Add Book — A.K.L.A.T.");
        dlg.setHeaderText(null);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // ── Main layout ───────────────────────────────────────────────
        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.setPrefWidth(540);

        // ── ISBN scanner card ─────────────────────────────────────────
        VBox isbnCard = new VBox(8);
        isbnCard.setStyle(
            "-fx-background-color:#F0F4FF;" +
            "-fx-border-color:#C5D3F5;" +
            "-fx-border-radius:8;" +
            "-fx-background-radius:8;" +
            "-fx-padding:12;"
        );

        Label isbnHeader = new Label("\uD83D\uDD0E  ISBN / Title Lookup  —  auto-fill book details from the internet");
        isbnHeader.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        isbnHeader.setTextFill(Color.web("#3A5BBE"));

        HBox isbnRow = new HBox(8);
        isbnRow.setAlignment(Pos.CENTER_LEFT);

        TextField isbnField = new TextField();
        isbnField.setPromptText("Enter ISBN-10 or ISBN-13 (e.g. 9780321560995)");
        isbnField.setFont(Font.font("Consolas", 13));
        isbnField.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:#C5D3F5;" +
            "-fx-border-radius:6;" +
            "-fx-background-radius:6;" +
            "-fx-padding:6 10 6 10;"
        );
        HBox.setHgrow(isbnField, Priority.ALWAYS);

        Button lookupBtn = new Button("\uD83D\uDD0D  Look Up");
        lookupBtn.setStyle(
            "-fx-background-color:#3A5BBE;" +
            "-fx-text-fill:white;" +
            "-fx-font-weight:bold;" +
            "-fx-font-size:12px;" +
            "-fx-background-radius:6;" +
            "-fx-cursor:hand;" +
            "-fx-padding:7 14 7 14;"
        );

        Button camBtn = new Button("\uD83D\uDCF7  Scan");
        camBtn.setStyle(
            "-fx-background-color:#8B1A2B;" +
            "-fx-text-fill:white;" +
            "-fx-font-weight:bold;" +
            "-fx-font-size:12px;" +
            "-fx-background-radius:6;" +
            "-fx-cursor:hand;" +
            "-fx-padding:7 14 7 14;"
        );
        camBtn.setTooltip(new Tooltip("Open camera to scan barcode"));

        isbnRow.getChildren().addAll(isbnField, lookupBtn, camBtn);

        // Status label
        Label statusLbl = new Label();
        statusLbl.setFont(Font.font("Segoe UI", 11));
        statusLbl.setTextFill(Color.web("#666"));
        statusLbl.setVisible(false);

        // Cover image (shown when available)
        ImageView coverImg = new ImageView();
        coverImg.setFitHeight(80);
        coverImg.setFitWidth(55);
        coverImg.setPreserveRatio(true);
        coverImg.setSmooth(true);
        coverImg.setVisible(false);

        // ── Title search (auto-suggest from the internet) ─────────────
        TextField titleSearchField = new TextField();
        titleSearchField.setPromptText("Search by book title (e.g. Clean Code)");
        titleSearchField.setFont(Font.font("Segoe UI", 13));
        titleSearchField.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:#C5D3F5;" +
            "-fx-border-radius:6;" +
            "-fx-background-radius:6;" +
            "-fx-padding:6 10 6 10;"
        );
        HBox.setHgrow(titleSearchField, Priority.ALWAYS);

        Button titleSearchBtn = new Button("\uD83D\uDD0D  Search Title");
        titleSearchBtn.setStyle(
            "-fx-background-color:#3A5BBE;" +
            "-fx-text-fill:white;" +
            "-fx-font-weight:bold;" +
            "-fx-font-size:12px;" +
            "-fx-background-radius:6;" +
            "-fx-cursor:hand;" +
            "-fx-padding:7 14 7 14;"
        );

        HBox titleRow = new HBox(8, titleSearchField, titleSearchBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        ListView<BookInfo> resultsList = new ListView<>();
        resultsList.setPrefHeight(170);
        resultsList.setMaxHeight(170);
        resultsList.setVisible(false);
        resultsList.setManaged(false);
        resultsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(BookInfo b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) { setText(null); return; }
                String meta = (b.author.isEmpty() ? "Unknown author" : b.author)
                        + (b.year.isEmpty() ? "" : "  \u2022  " + b.year)
                        + (b.isbn.isEmpty() ? "" : "  \u2022  ISBN " + b.isbn);
                setText(b.title + "\n" + meta);
                setWrapText(false);
            }
        });

        isbnCard.getChildren().addAll(isbnHeader, isbnRow, titleRow, resultsList, statusLbl);

        // ── Book details grid ─────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);

        TextField titleF   = styledInput("Title *");
        TextField authF    = styledInput("Author *");
        TextField pubF     = styledInput("Publisher (optional)");
        TextField yearF    = styledInput("Year (optional)");

        // Section combo with common library sections (editable so custom can be typed)
        ComboBox<String> secCombo = new ComboBox<>();
        secCombo.setEditable(true);
        secCombo.getItems().addAll(
            "General Circulation",
            "Science & Tech",
            "Engineering",
            "Information Technology",
            "Computer Science",
            "Humanities",
            "Social Sciences",
            "Business & Accountancy",
            "Filipiniana",
            "Reference"
        );
        secCombo.setValue("General Circulation");
        secCombo.setMaxWidth(Double.MAX_VALUE);
        secCombo.setStyle("-fx-background-color:white; -fx-border-color:#D8DCE6; -fx-border-radius:5; -fx-background-radius:5;");

        TextField cnF        = styledInput("Call Number / Classification (optional)");
        TextField isbnStoreF = styledInput("ISBN (optional)");
        TextField copiesF    = styledInput("Total Copies *");
        copiesF.setText("1"); // Sensible default so it's never empty

        Label lTitle   = gridLabel("Title *:");
        Label lAuth    = gridLabel("Author *:");
        Label lPub     = gridLabel("Publisher:");
        Label lYear    = gridLabel("Year:");
        Label lSec     = gridLabel("Section *:");
        Label lCN      = gridLabel("Call No.:");
        Label lIsbn    = gridLabel("ISBN:");
        Label lCopies  = gridLabel("Copies *:");

        grid.add(lTitle,  0, 0); grid.add(titleF,     1, 0);
        grid.add(lAuth,   0, 1); grid.add(authF,      1, 1);
        grid.add(lPub,    0, 2); grid.add(pubF,       1, 2);
        grid.add(lYear,   0, 3); grid.add(yearF,      1, 3);
        grid.add(lSec,    0, 4); grid.add(secCombo,   1, 4);
        grid.add(lCN,     0, 5); grid.add(cnF,        1, 5);
        grid.add(lIsbn,   0, 6); grid.add(isbnStoreF, 1, 6);
        grid.add(lCopies, 0, 7); grid.add(copiesF,    1, 7);

        ColumnConstraints c0 = new ColumnConstraints(90);
        ColumnConstraints c1 = new ColumnConstraints(300, 330, Double.MAX_VALUE);
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        // Validation error label
        Label errorLbl = new Label();
        errorLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        errorLbl.setTextFill(Color.web("#C0392B"));
        errorLbl.setVisible(false);

        // Cover + grid side-by-side
        HBox contentRow = new HBox(14);
        contentRow.setAlignment(Pos.TOP_LEFT);
        contentRow.getChildren().add(grid);

        root.getChildren().addAll(isbnCard, contentRow, errorLbl);
        dlg.getDialogPane().setContent(root);
        dlg.getDialogPane().setStyle("-fx-background-color:#FAFBFF;");

        // ── Camera Scan action ─────────────────────────────────────────
        camBtn.setOnAction(evt -> {
            Stage owner = (Stage) dlg.getDialogPane().getScene().getWindow();
            new CameraBarcodeScannerFX().show(owner, scannedCode -> {
                isbnField.setText(scannedCode);
                lookupBtn.fire();
            });
        });

        // ── ISBN Lookup action ─────────────────────────────────────────
        lookupBtn.setOnAction(evt -> {
            String isbn = isbnField.getText().trim().replaceAll("[^0-9X]", "");
            if (isbn.length() < 10) {
                statusLbl.setText("⚠ Please enter a valid ISBN-10 or ISBN-13.");
                statusLbl.setTextFill(Color.web("#C0392B"));
                statusLbl.setVisible(true);
                return;
            }

            lookupBtn.setDisable(true);
            lookupBtn.setText("Fetching\u2026");
            statusLbl.setText("\uD83C\uDF10  Searching Open Library & Google Books\u2026");
            statusLbl.setTextFill(Color.web("#3A5BBE"));
            statusLbl.setVisible(true);
            coverImg.setVisible(false);

            FadeTransition pulse = new FadeTransition(Duration.millis(600), isbnCard);
            pulse.setFromValue(1.0); pulse.setToValue(0.6);
            pulse.setCycleCount(Animation.INDEFINITE); pulse.setAutoReverse(true);
            pulse.play();

            IsbnLookupService.lookup(isbn).thenAccept(info -> Platform.runLater(() -> {
                pulse.stop();
                isbnCard.setOpacity(1.0);
                lookupBtn.setDisable(false);
                lookupBtn.setText("\uD83D\uDD0D  Look Up");

                if (info.found) {
                    if (!info.title.isEmpty())     titleF.setText(info.title);
                    if (!info.author.isEmpty())    authF.setText(info.author);
                    if (!info.publisher.isEmpty()) pubF.setText(info.publisher);
                    if (!info.year.isEmpty())      yearF.setText(info.year);
                    if (!info.dewey.isEmpty())     cnF.setText(info.dewey);
                    isbnStoreF.setText(isbn);

                    // Section inference
                    if (!info.dewey.isEmpty()) {
                        String sect = deweyToSection(info.dewey);
                        if (!sect.isEmpty()) secCombo.setValue(sect);
                    }

                    statusLbl.setText("\u2705  Found on " + info.source
                            + (info.pages.isEmpty() ? "" : "  \u2022  " + info.pages + " pages"));
                    statusLbl.setTextFill(Color.web("#27AE60"));

                    loadCover(info.coverUrl, coverImg, contentRow);
                } else {
                    statusLbl.setText("\u274C  Not found online. Please enter book details manually.");
                    statusLbl.setTextFill(Color.web("#C0392B"));
                }
            }));
        });

        // ── Title search action ────────────────────────────────────────
        final int[] searchSeq = {0};
        Runnable doTitleSearch = () -> {
            String q = titleSearchField.getText().trim();
            if (q.length() < 2) {
                statusLbl.setText("\u26A0 Type at least 2 characters of the title.");
                statusLbl.setTextFill(Color.web("#C0392B"));
                statusLbl.setVisible(true);
                return;
            }
            final int mySeq = ++searchSeq[0];
            titleSearchBtn.setDisable(true);
            titleSearchBtn.setText("Searching\u2026");
            statusLbl.setText("\uD83C\uDF10  Searching titles on Open Library & Google Books\u2026");
            statusLbl.setTextFill(Color.web("#3A5BBE"));
            statusLbl.setVisible(true);

            IsbnLookupService.searchByTitle(q).thenAccept(list -> Platform.runLater(() -> {
                if (mySeq != searchSeq[0]) return; // stale result
                titleSearchBtn.setDisable(false);
                titleSearchBtn.setText("\uD83D\uDD0D  Search Title");
                resultsList.getItems().setAll(list);
                boolean any = !list.isEmpty();
                resultsList.setVisible(any);
                resultsList.setManaged(any);
                if (any) {
                    statusLbl.setText("Found " + list.size() + " match(es). Click one to auto-fill the form.");
                    statusLbl.setTextFill(Color.web("#27AE60"));
                } else {
                    statusLbl.setText("\u274C  No titles found. Try different words or enter details manually.");
                    statusLbl.setTextFill(Color.web("#C0392B"));
                }
                dlg.getDialogPane().getScene().getWindow().sizeToScene();
            }));
        };
        titleSearchBtn.setOnAction(evt -> doTitleSearch.run());
        titleSearchField.setOnAction(evt -> doTitleSearch.run());

        resultsList.setOnMouseClicked(evt -> {
            BookInfo pick = resultsList.getSelectionModel().getSelectedItem();
            if (pick == null) return;
            titleF.setText(pick.title);
            authF.setText(pick.author);
            pubF.setText(pick.publisher);
            yearF.setText(pick.year);
            if (!pick.isbn.isEmpty()) {
                isbnStoreF.setText(pick.isbn);
                isbnField.setText(pick.isbn);
            }
            loadCover(pick.coverUrl, coverImg, contentRow);
            resultsList.setVisible(false);
            resultsList.setManaged(false);
            statusLbl.setText("\u2705  Filled from " + pick.source + ". Review the details, then Save.");
            statusLbl.setTextFill(Color.web("#27AE60"));
            statusLbl.setVisible(true);
            dlg.getDialogPane().getScene().getWindow().sizeToScene();
        });

        isbnField.setOnAction(evt -> lookupBtn.fire());

        // ── Validation filter on OK button ─────────────────────────────
        Button okButton = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        if (okButton != null) {
            okButton.setStyle(
                "-fx-background-color:#8B1A2B;" +
                "-fx-text-fill:white;" +
                "-fx-font-weight:bold;" +
                "-fx-background-radius:6;"
            );
            okButton.setText("Save Book");

            // Event filter: consumes action event so dialog STAYS OPEN if validation fails!
            okButton.addEventFilter(ActionEvent.ACTION, event -> {
                String title = titleF.getText().trim();
                String author = authF.getText().trim();
                String sec = secCombo.getValue() != null ? secCombo.getValue().trim() : "";
                String copiesText = copiesF.getText().trim();

                if (title.isEmpty()) {
                    event.consume();
                    errorLbl.setText("⚠ Please enter the book Title.");
                    errorLbl.setVisible(true);
                    titleF.requestFocus();
                    return;
                }
                if (author.isEmpty()) {
                    event.consume();
                    errorLbl.setText("⚠ Please enter the Author name.");
                    errorLbl.setVisible(true);
                    authF.requestFocus();
                    return;
                }
                if (sec.isEmpty()) {
                    secCombo.setValue("General Circulation");
                }
                int copies = 0;
                try {
                    copies = Integer.parseInt(copiesText);
                    if (copies <= 0) throw new NumberFormatException();
                } catch (Exception ex) {
                    event.consume();
                    errorLbl.setText("⚠ Total Copies must be a positive integer (e.g. 1).");
                    errorLbl.setVisible(true);
                    copiesF.requestFocus();
                    return;
                }
                errorLbl.setVisible(false);
            });
        }

        // Result converter: only called when validation passes
        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String title  = titleF.getText().trim();
                    String author = authF.getText().trim();
                    String sec    = secCombo.getValue() != null ? secCombo.getValue().trim() : "General Circulation";
                    String cn     = cnF.getText().trim();
                    String isbnV  = isbnStoreF.getText().trim();
                    int copies    = Integer.parseInt(copiesF.getText().trim());

                    String barcode = db.nextBarcode();
                    Book b = new Book(barcode, title, author, cn, sec, copies, copies);
                    b.setIsbn(isbnV);
                    b.setDeweyDecimal(cn);
                    return b;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(book -> {
            if (book != null) {
                boolean ok = db.addBook(book);
                if (ok) {
                    // Reset filters so the new book is immediately visible in the table!
                    if (searchField != null) searchField.clear();
                    if (sectionCombo != null) sectionCombo.setValue("All Sections");
                    if (statusCombo != null) statusCombo.setValue("All Status");
                    updateSectionCombo();
                    refresh();

                    // Scroll to and highlight the newly added book
                    table.getSelectionModel().select(book);
                    table.scrollTo(book);

                    showBanner("✓ Book \"" + book.getTitle() + "\" added successfully (" + book.getBarcode() + ")!", "success");
                } else {
                    showBanner("✕ Error saving book to database. Please check database connection.", "error");
                }
            }
        });
    }

    private void loadCover(String url, ImageView coverImg, HBox contentRow) {
        if (url == null || url.isEmpty()) return;
        Task<Image> imgTask = new Task<>() {
            @Override protected Image call() {
                return new Image(url, 80, 110, true, true, false);
            }
        };
        imgTask.setOnSucceeded(e -> {
            Image img = imgTask.getValue();
            if (img != null && !img.isError()) {
                coverImg.setImage(img);
                if (!contentRow.getChildren().contains(coverImg)) {
                    contentRow.getChildren().add(0, coverImg);
                }
                coverImg.setVisible(true);
            }
        });
        new Thread(imgTask, "isbn-cover-loader").start();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String deweyToSection(String dewey) {
        try {
            int d = (int) Double.parseDouble(dewey.split("[^0-9.]")[0]);
            if (d < 100)  return "General Works";
            if (d < 200)  return "Philosophy & Psychology";
            if (d < 300)  return "Religion";
            if (d < 400)  return "Social Sciences";
            if (d < 500)  return "Language";
            if (d < 600)  return "Science & Math";
            if (d < 700)  return "Science & Tech";
            if (d < 800)  return "Arts";
            if (d < 900)  return "Literature";
            return "History & Geography";
        } catch (Exception e) { return "General Circulation"; }
    }

    private TextField styledInput(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setFont(Font.font("Segoe UI", 13));
        tf.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:#D8DCE6;" +
            "-fx-border-radius:5;" +
            "-fx-background-radius:5;" +
            "-fx-padding:6 10 6 10;"
        );
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private Label gridLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        l.setTextFill(Color.web("#555"));
        l.setAlignment(Pos.CENTER_RIGHT);
        l.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHalignment(l, javafx.geometry.HPos.RIGHT);
        return l;
    }

    @SuppressWarnings("unchecked")
    private <S, T> TableColumn<S, T> col(String title, int prefWidth) {
        TableColumn<S, T> c = new TableColumn<>(title);
        c.setPrefWidth(prefWidth);
        return c;
    }

    private TableCell<Book, String> monoCell() {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); }
                else {
                    setText(item);
                    setFont(Font.font("Consolas", 12));
                    setTextFill(Color.web(FXComponents.T_MUTED));
                }
            }
        };
    }
}
