package autolib.fx.panels;

import autolib.DataStore;
import autolib.fx.CameraBarcodeScannerFX;
import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import autolib.fx.kiosk.KioskProfileCardFX;
import autolib.models.Book;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Librarian "Borrow & Return" desk  works like a grocery-store cashier.
 *
 *   1. The student taps / scans their ID at the librarian's desk  (student loads on the left).
 *   2. The student hands over the book(s). The librarian scans each barcode (USB scanner types
 *      into the focused field, or use the camera)  every book is added to the current transaction.
 *      A book the student already holds is treated as a RETURN, anything else as a BORROW.
 *   3. "Complete Transaction" writes everything to the database in one go
 *      (books.available_copies, students.active_loans, transactions).
 *
 * The student kiosk is now view-only; the mobile app will read the same tables.
 */
public class CirculationFX {

    private static final double FINE_PER_DAY = 5.00;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final LibrarianPortalFX portal;
    private final DataStore db = DataStore.get();

    // Desk state
    private Student student;
    private final List<Line> cart = new ArrayList<>();

    // UI
    private VBox leftHolder;
    private VBox cartList;
    private TextField scanField;
    private Label stepPill, scanCaption, cartCount, summaryLabel;
    private Button cameraBtn, searchTitleBtn, enterBtn, completeBtn, cancelBtn;
    private HBox banner;
    private Label bannerLabel;

    /** One scanned item in the current transaction. */
    private static class Line {
        Book book;
        boolean isReturn;
        Transaction loan;   // the active loan being returned (return lines only)
        LocalDate due;      // borrow: new due date   |  return: original due date
        int daysLate;       // return lines only
    }

    public CirculationFX(LibrarianPortalFX portal) {
        this.portal = portal;
    }

    // 
    // Layout
    // 
    public Node build() {
        leftHolder = new VBox();
        leftHolder.setPrefWidth(290);
        leftHolder.setMinWidth(270);
        leftHolder.setMaxWidth(290);

        Node right = buildDeskCard();
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox row = new HBox(16, leftHolder, right);
        row.setAlignment(Pos.TOP_LEFT);
        row.setFillHeight(false);
        row.setPadding(new Insets(20));
        row.setStyle("-fx-background-color:#F4F6FA;");
        // Clicking anywhere on the desk puts the cursor back in the scan box,
        // so the barcode scanner always "types" into the right place.
        row.setOnMouseClicked(e -> focusScan());

        renderLeft();
        renderState();

        ScrollPane sp = new ScrollPane(row);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#F4F6FA; -fx-background:#F4F6FA;");
        Platform.runLater(this::focusScan);
        return sp;
    }

    private VBox buildDeskCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));
        card.setMaxWidth(Double.MAX_VALUE);

        stepPill = new Label();
        HBox hdr = FXComponents.cardHeader("Borrow & Return Desk", stepPill);

        bannerLabel = new Label();
        bannerLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        bannerLabel.setWrapText(true);
        HBox.setHgrow(bannerLabel, Priority.ALWAYS);
        banner = new HBox(bannerLabel);
        banner.setVisible(false);
        banner.setManaged(false);

        scanCaption = caption("");

        scanField = FXComponents.inputField("");
        scanField.setPrefHeight(44);
        scanField.setFont(Font.font("Consolas", 14));
        HBox.setHgrow(scanField, Priority.ALWAYS);
        scanField.setOnAction(e -> submitScan());

        enterBtn = FXComponents.ghostButton("Enter");
        enterBtn.setPrefHeight(44);
        enterBtn.setPrefWidth(84);
        enterBtn.setOnAction(e -> submitScan());

        cameraBtn = FXComponents.ghostButton("Camera");
        cameraBtn.setPrefHeight(44);
        cameraBtn.setOnAction(e -> openCamera());

        searchTitleBtn = FXComponents.ghostButton("\uD83D\uDD0D Search Title");
        searchTitleBtn.setPrefHeight(44);
        searchTitleBtn.setOnAction(e -> openTitleSearchDialog(""));

        HBox scanRow = new HBox(8, scanField, enterBtn, cameraBtn, searchTitleBtn);
        scanRow.setAlignment(Pos.CENTER);

        cartCount = new Label();
        Region cartSp = new Region();
        HBox.setHgrow(cartSp, Priority.ALWAYS);
        HBox cartHdr = new HBox(caption("CURRENT TRANSACTION"), cartSp, cartCount);
        cartHdr.setAlignment(Pos.CENTER_LEFT);
        cartHdr.setPadding(new Insets(6, 0, 0, 0));

        cartList = new VBox(8);
        cartList.setMinHeight(150);

        summaryLabel = new Label();
        summaryLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        summaryLabel.setTextFill(Color.web(FXComponents.T_SECOND));
        summaryLabel.setWrapText(true);

        cancelBtn = FXComponents.ghostButton("Next Student");
        cancelBtn.setPrefHeight(44);
        cancelBtn.setOnAction(e -> resetDesk());

        completeBtn = FXComponents.primaryButton("Complete Transaction");
        completeBtn.setPrefHeight(44);
        completeBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        completeBtn.setOnAction(e -> completeTransaction());

        Region actSp = new Region();
        HBox.setHgrow(actSp, Priority.ALWAYS);
        HBox actions = new HBox(10, cancelBtn, actSp, completeBtn);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(6, 0, 0, 0));

        card.getChildren().addAll(hdr, banner, scanCaption, scanRow, cartHdr, cartList, summaryLabel, actions);
        return card;
    }

    // 
    // Rendering
    // 
    private void renderLeft() {
        if (student == null) {
            leftHolder.getChildren().setAll(buildIdleCard());
        } else {
            Region c = new KioskProfileCardFX(student).build();
            leftHolder.getChildren().setAll(c);
            FXComponents.fadeInSlide(c);
        }
    }

    private Region buildIdleCard() {
        StackPane pulse = FXComponents.nfcPulse(96);

        Label t = new Label("Waiting for student");
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        t.setTextFill(Color.WHITE);

        Label s = new Label("Ask the student to tap their ID on the NFC reader, "
            + "or scan / type their student ID in the box.");
        s.setFont(Font.font("Segoe UI", 11));
        s.setTextFill(Color.web("#AEB7CC"));
        s.setWrapText(true);
        s.setTextAlignment(TextAlignment.CENTER);
        s.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, pulse, t, s);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(34, 22, 34, 22));
        box.setMinHeight(320);
        box.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #1B2150, #12163A);"
            + "-fx-background-radius:20; -fx-border-color:rgba(255,255,255,0.12);"
            + "-fx-border-radius:20; -fx-border-width:1;");
        return box;
    }

    /** Refreshes prompts, cart rows, totals and button states from the current desk state. */
    private void renderState() {
        boolean hasStudent = student != null;

        if (hasStudent) {
            setPill(stepPill, "STEP 2  Scan or search the books", FXComponents.BLUE);
            scanCaption.setText("BOOK BARCODE / ISBN OR SEARCH TITLE");
            scanField.setPromptText("Scan barcode, type ISBN, or type title and press Enter");
        } else {
            setPill(stepPill, "STEP 1  Scan student ID", FXComponents.AMBER);
            scanCaption.setText("STUDENT ID  (NFC TAP OR SR-CODE)");
            scanField.setPromptText("Tap the NFC card or type the student ID, then press Enter");
        }
        cameraBtn.setDisable(!hasStudent);
        searchTitleBtn.setDisable(!hasStudent);

        cartList.getChildren().clear();
        if (cart.isEmpty()) {
            Label empty = new Label(hasStudent
                ? "No books scanned yet.\nScan a book to borrow it  or a book the student is returning."
                : "Scan a student ID to start a transaction.");
            empty.setFont(Font.font("Segoe UI", 12));
            empty.setTextFill(Color.web(FXComponents.T_MUTED));
            empty.setTextAlignment(TextAlignment.CENTER);
            empty.setAlignment(Pos.CENTER);
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setPadding(new Insets(34, 0, 34, 0));
            cartList.getChildren().add(empty);
        } else {
            for (Line l : cart) cartList.getChildren().add(lineRow(l));
        }

        int borrows = 0, returns = 0;
        for (Line l : cart) { if (l.isReturn) returns++; else borrows++; }
        setPill(cartCount, cart.size() + (cart.size() == 1 ? " item" : " items"), FXComponents.T_MUTED);

        StringBuilder sum = new StringBuilder();
        if (borrows > 0) sum.append(borrows).append(" to borrow");
        if (returns > 0) sum.append(sum.length() > 0 ? "   |   " : "").append(returns).append(" to return");
        int fee = totalFine();
        if (fee > 0) sum.append("   |   Late fee: \u20B1").append(String.format("%.2f", fee * FINE_PER_DAY));
        summaryLabel.setText(sum.toString());
        summaryLabel.setVisible(sum.length() > 0);
        summaryLabel.setManaged(sum.length() > 0);

        completeBtn.setDisable(cart.isEmpty());
        completeBtn.setText(cart.isEmpty() ? "Complete Transaction" : "Complete Transaction (" + cart.size() + ")");
        cancelBtn.setDisable(!hasStudent);
        cancelBtn.setText(cart.isEmpty() ? "Next Student" : "Cancel Transaction");
    }

    private HBox lineRow(Line l) {
        Label badge = FXComponents.txBadge(l.isReturn ? "Returned" : "Borrowed");
        badge.setText(l.isReturn ? "RETURN" : "BORROW");
        badge.setMinWidth(72);
        badge.setAlignment(Pos.CENTER);

        Label title = new Label(l.book.getTitle());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(Color.web(FXComponents.T_PRIMARY));
        title.setWrapText(true);

        Label sub = new Label(l.book.getBarcode() + "    " + nz(l.book.getAuthor()));
        sub.setFont(Font.font("Segoe UI", 10.5));
        sub.setTextFill(Color.web(FXComponents.T_MUTED));

        VBox info = new VBox(2, title, sub);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label status = new Label();
        status.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        if (l.isReturn) {
            if (l.daysLate > 0) {
                status.setText("Late " + l.daysLate + " day" + (l.daysLate == 1 ? "" : "s")
                    + "  \u20B1" + String.format("%.2f", l.daysLate * FINE_PER_DAY));
                status.setTextFill(Color.web(FXComponents.RED));
            } else {
                status.setText("On time");
                status.setTextFill(Color.web("#15803D"));
            }
        } else {
            status.setText("Due " + l.due.format(DATE_FMT));
            status.setTextFill(Color.web(FXComponents.T_SECOND));
        }

        Button remove = new Button("\u2715");
        remove.setStyle("-fx-background-color:transparent; -fx-text-fill:#949EB2; -fx-font-size:13; -fx-cursor:hand;");
        remove.setOnMouseEntered(e -> remove.setStyle("-fx-background-color:#FDF2F3; -fx-background-radius:8; -fx-text-fill:#CC2936; -fx-font-size:13; -fx-cursor:hand;"));
        remove.setOnMouseExited(e -> remove.setStyle("-fx-background-color:transparent; -fx-text-fill:#949EB2; -fx-font-size:13; -fx-cursor:hand;"));
        remove.setOnAction(e -> {
            cart.remove(l);
            clearBanner();
            renderState();
            focusScan();
        });

        HBox row = new HBox(12, badge, info, status, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle("-fx-background-color:#FAFBFD; -fx-background-radius:10; -fx-border-radius:10;"
            + "-fx-border-color:#E4E8F0; -fx-border-width:1; -fx-padding:10 12 10 14;");
        FXComponents.fadeInSlide(row);
        return row;
    }

    // 
    // Scanning
    // 
    private void submitScan() {
        String code = scanField.getText();
        scanField.clear();
        handleScan(code);
        focusScan();
    }

    private void openCamera() {
        if (student == null || cameraBtn.getScene() == null) return;
        Stage owner = (Stage) cameraBtn.getScene().getWindow();
        new CameraBarcodeScannerFX().show(owner, scanned -> Platform.runLater(() -> {
            handleScan(scanned);
            focusScan();
        }));
    }

    /** Single entry point for everything that gets scanned or typed at the desk. */
    private void handleScan(String raw) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            FXComponents.flagInvalid(scanField);
            return;
        }

        // Step 1  waiting for a student
        if (student == null) {
            Student s = db.findStudentByIdOrNfc(code);
            if (s == null) {
                FXComponents.shake(scanField);
                setBanner("No student found for \"" + code + "\". Check the ID and try again.", "error");
                return;
            }
            loadStudent(s);
            return;
        }

        // Step 2 – a book?
        Book b = db.findBookByCode(code);
        if (b != null) {
            addBook(b);
            return;
        }

        // Not a barcode/ISBN – check if it matches book titles in catalog
        List<Book> titleMatches = db.searchBooks(code, "All Sections", "All Status");
        if (!titleMatches.isEmpty()) {
            if (titleMatches.size() == 1) {
                addBook(titleMatches.get(0));
                return;
            } else {
                openTitleSearchDialog(code);
                return;
            }
        }

        // Not a book – maybe the next student tapped their card
        Student other = db.findStudentByIdOrNfc(code);
        if (other != null) {
            if (other.getStudentId().equalsIgnoreCase(student.getStudentId())) {
                setBanner(student.getName() + " is already loaded. Scan the book(s).", "info");
            } else if (!cart.isEmpty()) {
                setBanner("Finish or cancel the current transaction before switching to "
                    + other.getName() + ".", "warn");
            } else {
                loadStudent(other);
            }
            return;
        }

        FXComponents.shake(scanField);
        setBanner("\"" + code + "\" isn't a book in the catalog. Check the barcode or use Search Title.", "error");
    }

    private void openTitleSearchDialog(String initialQuery) {
        if (student == null) {
            setBanner("Please scan or enter student ID first before selecting books.", "warn");
            return;
        }

        Stage owner = null;
        if (scanField != null && scanField.getScene() != null && scanField.getScene().getWindow() instanceof Stage) {
            owner = (Stage) scanField.getScene().getWindow();
        }

        Dialog<Void> dlg = new Dialog<>();
        if (owner != null) dlg.initOwner(owner);
        dlg.setTitle("Search Book to Borrow or Return — A.K.L.A.T.");
        dlg.setHeaderText(null);

        VBox content = new VBox(12);
        content.setPrefWidth(640);
        content.setPrefHeight(500);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color:#F8F9FC;");

        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        TextField qField = FXComponents.inputField("Search book by title, author, or barcode…");
        qField.setPrefHeight(42);
        qField.setFont(Font.font("Segoe UI", 13));
        HBox.setHgrow(qField, Priority.ALWAYS);
        if (initialQuery != null && !initialQuery.trim().isEmpty()) {
            qField.setText(initialQuery.trim());
        }

        Button clearBtn = FXComponents.ghostButton("Clear");
        clearBtn.setPrefHeight(42);
        clearBtn.setOnAction(e -> { qField.clear(); qField.requestFocus(); });
        searchBar.getChildren().addAll(qField, clearBtn);

        VBox resultsBox = new VBox(10);
        ScrollPane sp = new ScrollPane(resultsBox);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:transparent; -fx-background:transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        Runnable refreshList = () -> {
            resultsBox.getChildren().clear();
            String q = qField.getText().trim().toLowerCase();

            // 1. ACTIVE LOANS (Return candidates)
            List<Transaction> activeLoans = KioskProfileCardFX.activeLoans(student);
            List<Transaction> matchingLoans = new ArrayList<>();
            for (Transaction tx : activeLoans) {
                if (q.isEmpty()
                        || (tx.getBookTitle() != null && tx.getBookTitle().toLowerCase().contains(q))
                        || (tx.getBookBarcode() != null && tx.getBookBarcode().toLowerCase().contains(q))) {
                    matchingLoans.add(tx);
                }
            }

            if (!matchingLoans.isEmpty()) {
                Label loanSection = new Label("STUDENT'S BORROWED BOOKS (CLICK TO RETURN)");
                loanSection.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
                loanSection.setTextFill(Color.web("#8B1A2B"));
                resultsBox.getChildren().add(loanSection);

                for (Transaction tx : matchingLoans) {
                    Book b = db.findBookByCode(tx.getBookBarcode());
                    if (b == null) {
                        b = new Book(tx.getBookBarcode(), tx.getBookTitle(), "", "", "", 1, 0);
                    }
                    final Book bookToReturn = b;

                    boolean alreadyInCart = false;
                    for (Line l : cart) {
                        if (l.book.getBarcode().equalsIgnoreCase(bookToReturn.getBarcode())) {
                            alreadyInCart = true;
                            break;
                        }
                    }

                    HBox item = new HBox(12);
                    item.setAlignment(Pos.CENTER_LEFT);
                    item.setPadding(new Insets(10, 14, 10, 14));
                    item.setStyle("-fx-background-color:#FFF5F5; -fx-border-color:#FCDAD7; -fx-border-radius:8; -fx-background-radius:8;");

                    Label retBadge = FXComponents.txBadge("Returned");
                    retBadge.setText("ACTIVE LOAN");

                    VBox bookInfo = new VBox(2);
                    Label tLbl = new Label(tx.getBookTitle());
                    tLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                    tLbl.setTextFill(Color.web(FXComponents.T_PRIMARY));

                    LocalDate due = tx.getTimestamp().toLocalDate().plusDays(KioskProfileCardFX.LOAN_DAYS);
                    long late = ChronoUnit.DAYS.between(due, LocalDate.now());
                    String dueText = (late > 0 ? "⚠ Late " + late + " day(s)" : "Due " + due.format(DATE_FMT));
                    Label sLbl = new Label("Barcode: " + tx.getBookBarcode() + "  •  " + dueText);
                    sLbl.setFont(Font.font("Segoe UI", 11));
                    sLbl.setTextFill(late > 0 ? Color.web(FXComponents.RED) : Color.web(FXComponents.T_MUTED));
                    bookInfo.getChildren().addAll(tLbl, sLbl);
                    HBox.setHgrow(bookInfo, Priority.ALWAYS);

                    Button actBtn;
                    if (alreadyInCart) {
                        actBtn = new Button("In Cart");
                        actBtn.setDisable(true);
                        actBtn.setStyle("-fx-background-color:#E2E8F0; -fx-text-fill:#718096; -fx-background-radius:6; -fx-padding:6 12;");
                    } else {
                        actBtn = FXComponents.primaryButton("↩ Return");
                        actBtn.setStyle("-fx-background-color:#C0392B; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:6; -fx-padding:6 14; -fx-cursor:hand;");
                        actBtn.setOnAction(ev -> {
                            addBook(bookToReturn);
                            dlg.setResult(null);
                            dlg.close();
                            focusScan();
                        });
                    }
                    item.getChildren().addAll(retBadge, bookInfo, actBtn);
                    resultsBox.getChildren().add(item);
                }
            }

            // 2. CATALOG BOOKS (Borrow candidates)
            List<Book> matches = db.searchBooks(q, "All Sections", "All Status");
            Label catSection = new Label("LIBRARY CATALOG (CLICK TO BORROW)");
            catSection.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            catSection.setTextFill(Color.web("#3A5BBE"));
            catSection.setPadding(new Insets(10, 0, 0, 0));
            resultsBox.getChildren().add(catSection);

            if (matches.isEmpty()) {
                Label noMatch = new Label("No matching books found for \"" + q + "\"");
                noMatch.setFont(Font.font("Segoe UI", 12));
                noMatch.setTextFill(Color.web(FXComponents.T_MUTED));
                noMatch.setPadding(new Insets(12, 0, 12, 0));
                resultsBox.getChildren().add(noMatch);
            } else {
                int limit = Math.min(matches.size(), 30);
                for (int i = 0; i < limit; i++) {
                    Book b = matches.get(i);
                    boolean alreadyInCart = false;
                    for (Line l : cart) {
                        if (l.book.getBarcode().equalsIgnoreCase(b.getBarcode())) {
                            alreadyInCart = true;
                            break;
                        }
                    }

                    boolean isHeldByStudent = false;
                    for (Transaction tx : activeLoans) {
                        if (tx.getBookBarcode().equalsIgnoreCase(b.getBarcode())) {
                            isHeldByStudent = true;
                            break;
                        }
                    }

                    HBox item = new HBox(12);
                    item.setAlignment(Pos.CENTER_LEFT);
                    item.setPadding(new Insets(10, 14, 10, 14));
                    item.setStyle("-fx-background-color:white; -fx-border-color:#E2E8F0; -fx-border-radius:8; -fx-background-radius:8;");

                    VBox bookInfo = new VBox(2);
                    Label tLbl = new Label(b.getTitle());
                    tLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                    tLbl.setTextFill(Color.web(FXComponents.T_PRIMARY));

                    Label sLbl = new Label("Author: " + (b.getAuthor() == null || b.getAuthor().isEmpty() ? "Unknown" : b.getAuthor())
                        + "  •  Section: " + b.getSection()
                        + "  •  Available: " + b.getAvailableCopies() + "/" + b.getTotalCopies());
                    sLbl.setFont(Font.font("Segoe UI", 11));
                    sLbl.setTextFill(Color.web(FXComponents.T_MUTED));
                    bookInfo.getChildren().addAll(tLbl, sLbl);
                    HBox.setHgrow(bookInfo, Priority.ALWAYS);

                    Button actBtn;
                    if (alreadyInCart) {
                        actBtn = new Button("In Cart");
                        actBtn.setDisable(true);
                        actBtn.setStyle("-fx-background-color:#E2E8F0; -fx-text-fill:#718096; -fx-background-radius:6; -fx-padding:6 12;");
                    } else if (isHeldByStudent) {
                        actBtn = FXComponents.ghostButton("↩ Return");
                        actBtn.setStyle("-fx-background-color:#FFF1F2; -fx-text-fill:#C0392B; -fx-border-color:#FECDD3; -fx-font-weight:bold; -fx-background-radius:6; -fx-padding:6 12; -fx-cursor:hand;");
                        actBtn.setOnAction(ev -> {
                            addBook(b);
                            dlg.setResult(null);
                            dlg.close();
                            focusScan();
                        });
                    } else if (b.getAvailableCopies() <= 0) {
                        actBtn = new Button("No Copies");
                        actBtn.setDisable(true);
                        actBtn.setStyle("-fx-background-color:#F1F5F9; -fx-text-fill:#94A3B8; -fx-background-radius:6; -fx-padding:6 12;");
                    } else {
                        actBtn = FXComponents.primaryButton("⊞ Borrow");
                        actBtn.setStyle("-fx-background-color:#3A5BBE; -fx-text-fill:white; -fx-font-weight:bold; -fx-background-radius:6; -fx-padding:6 14; -fx-cursor:hand;");
                        actBtn.setOnAction(ev -> {
                            addBook(b);
                            dlg.setResult(null);
                            dlg.close();
                            focusScan();
                        });
                    }
                    item.getChildren().addAll(bookInfo, actBtn);
                    resultsBox.getChildren().add(item);
                }
            }
        };

        qField.textProperty().addListener((obs, oldVal, newVal) -> refreshList.run());
        refreshList.run();

        Button dismissBtn = FXComponents.ghostButton("Close");
        dismissBtn.setOnAction(e -> { dlg.setResult(null); dlg.close(); focusScan(); });
        HBox botBar = new HBox(dismissBtn);
        botBar.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(searchBar, sp, botBar);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node closeBtn = dlg.getDialogPane().lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setVisible(false);
            closeBtn.setManaged(false);
        }

        Platform.runLater(qField::requestFocus);
        dlg.showAndWait();
        focusScan();
    }

    private void loadStudent(Student s) {
        student = s;
        cart.clear();
        renderLeft();
        renderState();
        if (s.getOverdueCount() > 0) {
            setBanner("Student verified: " + s.getName() + ". Note: " + s.getOverdueCount()
                + " overdue book" + (s.getOverdueCount() == 1 ? "" : "s") + ".", "warn");
        } else {
            setBanner("Student verified: " + s.getName() + ". Now scan the book(s).", "success");
        }
    }

    private void addBook(Book b) {
        for (Line l : cart) {
            if (l.book.getBarcode().equalsIgnoreCase(b.getBarcode())) {
                setBanner("\"" + b.getTitle() + "\" is already in this transaction.", "warn");
                return;
            }
        }

        // Does this student currently hold the book?  Then it's a return.
        Transaction loan = null;
        for (Transaction tx : KioskProfileCardFX.activeLoans(student)) {
            if (tx.getBookBarcode().equalsIgnoreCase(b.getBarcode())) { loan = tx; break; }
        }

        Line line = new Line();
        line.book = b;

        if (loan != null) {
            line.isReturn = true;
            line.loan = loan;
            line.due = loan.getTimestamp().toLocalDate().plusDays(KioskProfileCardFX.LOAN_DAYS);
            line.daysLate = (int) Math.max(0, ChronoUnit.DAYS.between(line.due, LocalDate.now()));
            cart.add(line);
            if (line.daysLate > 0)
                setBanner("Return added: \"" + b.getTitle() + "\"  " + line.daysLate + " day(s) late.", "warn");
            else
                setBanner("Return added: \"" + b.getTitle() + "\".", "success");
        } else {
            if (b.getAvailableCopies() <= 0) {
                FXComponents.shake(scanField);
                setBanner("No copies of \"" + b.getTitle() + "\" are available right now.", "error");
                return;
            }
            if (projectedLoans() + 1 > KioskProfileCardFX.MAX_LOANS) {
                FXComponents.shake(scanField);
                setBanner("Loan limit reached  a student can hold " + KioskProfileCardFX.MAX_LOANS
                    + " books at a time.", "warn");
                return;
            }
            line.isReturn = false;
            line.due = LocalDate.now().plusDays(KioskProfileCardFX.LOAN_DAYS);
            cart.add(line);
            setBanner("Borrow added: \"" + b.getTitle() + "\"  due " + line.due.format(DATE_FMT) + ".", "success");
        }
        renderState();
    }

    /** Loan count the student would have after this transaction is completed. */
    private int projectedLoans() {
        int n = student.getActiveLoans();
        for (Line l : cart) n += l.isReturn ? -1 : 1;
        return n;
    }

    private int totalFine() {
        int days = 0;
        for (Line l : cart) if (l.isReturn) days += l.daysLate;
        return days;
    }

    // 
    // Commit to database
    // 
    private void completeTransaction() {
        if (student == null || cart.isEmpty()) return;
        if (projectedLoans() > KioskProfileCardFX.MAX_LOANS) {
            setBanner("This would exceed the " + KioskProfileCardFX.MAX_LOANS + "-book loan limit. Remove an item.", "error");
            return;
        }

        // Returns first (they free up slots), then borrows
        List<Line> ordered = new ArrayList<>();
        for (Line l : cart) if (l.isReturn) ordered.add(l);
        for (Line l : cart) if (!l.isReturn) ordered.add(l);

        List<Line> saved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int borrowed = 0, returned = 0, lateDays = 0;

        for (Line l : ordered) {
            String err = l.isReturn
                ? db.checkinBook(student, l.book, l.loan.getType() == Transaction.Type.OVERDUE, l.daysLate)
                : db.checkoutBook(student, l.book);
            if (err == null) {
                saved.add(l);
                if (l.isReturn) { returned++; lateDays += l.daysLate; } else borrowed++;
            } else {
                errors.add(err);
            }
        }
        cart.removeAll(saved);

        // Re-read the student so the left card shows what is really in the database
        Student fresh = db.findStudentById(student.getStudentId());
        if (fresh != null) student = fresh;

        if (!saved.isEmpty()) portal.refreshAll();

        renderLeft();
        renderState();

        if (errors.isEmpty()) {
            StringBuilder msg = new StringBuilder("Transaction saved for " + student.getName() + ":");
            if (borrowed > 0) msg.append(" ").append(borrowed).append(" borrowed (due ")
                .append(LocalDate.now().plusDays(KioskProfileCardFX.LOAN_DAYS).format(DATE_FMT)).append(")");
            if (borrowed > 0 && returned > 0) msg.append(",");
            if (returned > 0) msg.append(" ").append(returned).append(" returned");
            msg.append(".");
            if (lateDays > 0) msg.append(" Late fee to collect: \u20B1")
                .append(String.format("%.2f", lateDays * FINE_PER_DAY)).append(".");
            setBanner(msg.toString(), lateDays > 0 ? "warn" : "success");
        } else {
            setBanner("Some items were not saved: " + String.join(" ", errors)
                + (saved.isEmpty() ? "" : "  (" + saved.size() + " saved.)"), "error");
        }
        focusScan();
    }

    private void resetDesk() {
        student = null;
        cart.clear();
        clearBanner();
        renderLeft();
        renderState();
        focusScan();
    }

    // 
    // Small UI helpers
    // 
    private void focusScan() {
        if (scanField != null) Platform.runLater(() -> scanField.requestFocus());
    }

    private void setBanner(String msg, String type) {
        String c, glyph;
        switch (type) {
            case "success": c = FXComponents.GREEN; glyph = "\u2713"; break;
            case "error":   c = FXComponents.RED;   glyph = "\u2715"; break;
            case "warn":    c = FXComponents.AMBER; glyph = "!";      break;
            default:        c = FXComponents.BLUE;  glyph = "i";      break;
        }
        bannerLabel.setText(glyph + "   " + msg);
        bannerLabel.setTextFill(Color.web(FXComponents.T_PRIMARY));
        banner.setStyle("-fx-background-color:" + c + "18; -fx-background-radius:10; -fx-border-color:" + c + "55;"
            + "-fx-border-radius:10; -fx-padding:10 14 10 14;");
        banner.setVisible(true);
        banner.setManaged(true);
        FXComponents.fadeInSlide(banner);
    }

    private void clearBanner() {
        banner.setVisible(false);
        banner.setManaged(false);
    }

    private void setPill(Label l, String text, String colorHex) {
        l.setText(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(colorHex));
        l.setStyle("-fx-background-color:" + colorHex + "18; -fx-background-radius:20; -fx-padding:3 10 3 10;");
    }

    private Label caption(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(FXComponents.T_MUTED));
        return l;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
