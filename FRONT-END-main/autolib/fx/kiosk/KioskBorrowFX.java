package autolib.fx.kiosk;

import autolib.DataStore;
import autolib.fx.CameraBarcodeScannerFX;
import autolib.fx.FXComponents;
import autolib.fx.StudentKioskFX;
import autolib.models.Book;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Kiosk "Borrow a Book" panel, redesigned as two sections:
 *
 *   LEFT   Student profile card (name, program, ID, loan / due / overdue counters)
 *   RIGHT  Camera barcode scanner + manual ISBN / Book ID entry + live title search,
 *          then a book detail card with a one-click Borrow button.
 *
 * Public API is unchanged: new KioskBorrowFX(kiosk, student).build()
 *
 * Camera hookup: the viewport is a UI placeholder. Feed real scans (webcam + ZXing, or a USB
 * scanner) into {@link #onBarcodeScanned(String)}. While SIMULATE_CAMERA is true, pressing
 * "Scan Barcode" auto-detects a sample book after ~2 seconds so the flow can be demoed.
 */
public class KioskBorrowFX {

    /** Demo switch: set to false once a real camera feeds onBarcodeScanned(). */
    private static final boolean SIMULATE_CAMERA = false;

    private static final double VIEWPORT_H = 190;
    private static final int    RESULT_ROW_H = 54;
    private static final int    MAX_VISIBLE_RESULTS = 5;

    private final StudentKioskFX kiosk;
    private final Student student;
    private final DataStore db = DataStore.get();

    private KioskProfileCardFX profileCard;

    // Scanner UI
    private StackPane viewport;
    private Rectangle scanLine;
    private Label idleIcon, hintLabel, liveTag, statusPill;
    private Button scanBtn;
    private final List<Polyline> corners = new ArrayList<>();
    private Timeline scanAnim;
    private PauseTransition simDelay;
    private boolean scanning;

    // Manual entry / search
    private TextField isbnField, searchField;
    private ListView<Book> resultsList;
    private boolean suppressSearch;

    // Feedback + detail
    private HBox banner;
    private Label bannerLabel;
    private VBox detailBox;

    public KioskBorrowFX(StudentKioskFX kiosk, Student student) {
        this.kiosk = kiosk;
        this.student = student;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layout
    // ─────────────────────────────────────────────────────────────────────
    public Node build() {
        profileCard = new KioskProfileCardFX(student);
        Region left = profileCard.build();
        left.setPrefWidth(290);
        left.setMinWidth(270);
        left.setMaxWidth(290);

        Node right = buildScannerSection();
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox row = new HBox(16, left, right);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(20));
        row.setStyle("-fx-background-color:#F4F6FA;");

        ScrollPane sp = new ScrollPane(row);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#F4F6FA; -fx-background:#F4F6FA;");
        return sp;
    }

    private VBox buildScannerSection() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));
        card.setMaxWidth(Double.MAX_VALUE);

        statusPill = new Label();
        setPill(false);
        HBox hdr = FXComponents.cardHeader("Scan or Search a Book", statusPill);

        bannerLabel = new Label();
        bannerLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        bannerLabel.setWrapText(true);
        HBox.setHgrow(bannerLabel, Priority.ALWAYS);
        banner = new HBox(bannerLabel);
        banner.setVisible(false);
        banner.setManaged(false);

        scanBtn = FXComponents.primaryButton("📷  Scan Barcode with Camera");
        scanBtn.setMaxWidth(Double.MAX_VALUE);
        scanBtn.setPrefHeight(42);
        scanBtn.setOnAction(e -> { if (scanning) stopScan(); else startScan(); });

        // ISBN / Book ID
        isbnField = FXComponents.inputField("Enter ISBN or Book ID, then press Enter");
        isbnField.setPrefHeight(40);
        HBox.setHgrow(isbnField, Priority.ALWAYS);
        Button findBtn = FXComponents.ghostButton("Find");
        findBtn.setPrefHeight(40);
        findBtn.setPrefWidth(80);
        isbnField.setOnAction(e -> findByCode(isbnField.getText()));
        findBtn.setOnAction(e -> findByCode(isbnField.getText()));
        HBox isbnRow = new HBox(8, isbnField, findBtn);
        isbnRow.setAlignment(Pos.CENTER);

        // Title search + live results
        searchField = FXComponents.inputField("🔍  Type a book title or author…");
        searchField.setPrefHeight(40);
        searchField.textProperty().addListener((o, ov, nv) -> { if (!suppressSearch) updateResults(nv); });
        searchField.setOnAction(e -> {
            if (!resultsList.getItems().isEmpty()) pick(resultsList.getItems().get(0));
        });

        resultsList = new ListView<>();
        resultsList.setFixedCellSize(RESULT_ROW_H);
        resultsList.setVisible(false);
        resultsList.setManaged(false);
        resultsList.setStyle("-fx-background-color:white; -fx-background-radius:10;"
            + "-fx-border-color:#E4E8F0; -fx-border-radius:10;");
        Label noMatch = new Label("No matching books");
        noMatch.setTextFill(Color.web(FXComponents.T_MUTED));
        resultsList.setPlaceholder(noMatch);
        resultsList.setCellFactory(lv -> new ListCell<Book>() {
            @Override protected void updateItem(Book b, boolean empty) {
                super.updateItem(b, empty);
                setText(null);
                if (empty || b == null) { setGraphic(null); setStyle("-fx-background-color:transparent;"); return; }
                setStyle("-fx-background-color:transparent; -fx-padding:6 12 6 12;");
                setGraphic(resultRow(b));
            }
        });
        resultsList.setOnMouseClicked(e -> {
            Book b = resultsList.getSelectionModel().getSelectedItem();
            if (b != null) pick(b);
        });
        resultsList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Book b = resultsList.getSelectionModel().getSelectedItem();
                if (b != null) pick(b);
            }
        });

        detailBox = new VBox(12);
        detailBox.setVisible(false);
        detailBox.setManaged(false);
        detailBox.setStyle("-fx-background-color:#FAFBFD; -fx-background-radius:14; -fx-border-color:#E4E8F0;"
            + "-fx-border-radius:14; -fx-padding:16;");

        card.getChildren().addAll(
            hdr,
            banner,
            buildViewport(),
            scanBtn,
            divider("OR ENTER MANUALLY"),
            fieldLabel("ISBN / BOOK ID"),
            isbnRow,
            fieldLabel("SEARCH BY TITLE"),
            searchField,
            resultsList,
            detailBox
        );

        // Never leave the camera "running" or a timer pending after the panel is swapped out
        card.sceneProperty().addListener((o, was, now) -> { if (now == null) stopScan(); });
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scanner viewport (simulated camera)
    // ─────────────────────────────────────────────────────────────────────
    private StackPane buildViewport() {
        viewport = new StackPane();
        viewport.setMinHeight(VIEWPORT_H);
        viewport.setPrefHeight(VIEWPORT_H);
        viewport.setMaxHeight(VIEWPORT_H);
        viewport.setMaxWidth(Double.MAX_VALUE);
        viewport.setStyle("-fx-background-color: linear-gradient(to bottom, #0B0F1A, #151C30); -fx-background-radius:14;");

        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);

        // Faint barcode texture so the empty viewport doesn't look dead
        int[] widths = {3,1,2,4,1,3,2,1,4,2,3,1,2,3,1,4,2,1,3,2,4,1,2,3,1,2,4,1,3,2,1,3,2,4,1,2};
        HBox bars = new HBox(3);
        bars.setAlignment(Pos.CENTER);
        bars.setOpacity(0.09);
        bars.setMouseTransparent(true);
        for (int w : widths) {
            Rectangle r = new Rectangle(w, 76);
            r.setFill(Color.WHITE);
            bars.getChildren().add(r);
        }

        // Reticle
        StackPane frame = new StackPane();
        frame.setPrefSize(300, 120);
        frame.setMinSize(300, 120);
        frame.setMaxSize(300, 120);
        frame.getChildren().addAll(
            corner(Pos.TOP_LEFT), corner(Pos.TOP_RIGHT), corner(Pos.BOTTOM_LEFT), corner(Pos.BOTTOM_RIGHT));

        // Sweeping laser line
        scanLine = new Rectangle(276, 2);
        scanLine.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web(FXComponents.RED, 0)),
            new Stop(0.5, Color.web(FXComponents.RED)),
            new Stop(1, Color.web(FXComponents.RED, 0))));
        scanLine.setEffect(new DropShadow(10, Color.web(FXComponents.RED, 0.9)));
        scanLine.setVisible(false);
        scanAnim = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(scanLine.translateYProperty(), -50)),
            new KeyFrame(Duration.millis(1300),
                new KeyValue(scanLine.translateYProperty(), 50, Interpolator.EASE_BOTH)));
        scanAnim.setAutoReverse(true);
        scanAnim.setCycleCount(Animation.INDEFINITE);

        idleIcon = new Label("📷");
        idleIcon.setFont(Font.font("Segoe UI Emoji", 30));
        idleIcon.setOpacity(0.45);

        hintLabel = new Label("Camera idle — press the button below to scan");
        hintLabel.setFont(Font.font("Segoe UI", 11));
        hintLabel.setTextFill(Color.web("#8C96AA"));
        StackPane.setAlignment(hintLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hintLabel, new Insets(0, 0, 10, 0));

        liveTag = new Label("● LIVE");
        liveTag.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        liveTag.setTextFill(Color.web("#FF6B76"));
        liveTag.setVisible(false);
        StackPane.setAlignment(liveTag, Pos.TOP_LEFT);
        StackPane.setMargin(liveTag, new Insets(10, 0, 0, 14));

        viewport.getChildren().addAll(bars, frame, idleIcon, scanLine, hintLabel, liveTag);
        return viewport;
    }

    private StackPane corner(Pos pos) {
        Polyline pl = new Polyline(0, 26, 0, 0, 26, 0);
        pl.setFill(Color.TRANSPARENT);
        pl.setStroke(Color.web(FXComponents.RED));
        pl.setStrokeWidth(3.5);
        pl.setStrokeLineCap(StrokeLineCap.ROUND);
        pl.setStrokeLineJoin(StrokeLineJoin.ROUND);
        corners.add(pl);

        StackPane box = new StackPane(pl);
        box.setMinSize(30, 30);
        box.setPrefSize(30, 30);
        box.setMaxSize(30, 30);
        box.setScaleX(pos.getHpos() == HPos.RIGHT ? -1 : 1);
        box.setScaleY(pos.getVpos() == VPos.BOTTOM ? -1 : 1);
        StackPane.setAlignment(box, pos);
        return box;
    }

    private void startScan() {
        scanning = true;
        clearBanner();
        setScanButton(true);
        scanLine.setVisible(true);
        scanAnim.play();
        idleIcon.setVisible(false);
        liveTag.setVisible(true);
        hintLabel.setText("Align the barcode inside the frame");
        setPill(true);

        if (SIMULATE_CAMERA) {
            if (simDelay != null) simDelay.stop();
            simDelay = new PauseTransition(Duration.seconds(2.2));
            simDelay.setOnFinished(e -> simulateDetection());
            simDelay.play();
        } else {
            // Real camera via ZXing + webcam-capture
            javafx.stage.Stage owner = (javafx.stage.Stage)
                scanBtn.getScene().getWindow();
            new CameraBarcodeScannerFX().show(owner, scannedCode -> {
                stopScan();
                onBarcodeScanned(scannedCode);
            });
            // The dialog is modal — stopScan() is called in onResult callback above
            // But if user cancels, we still need to reset the scan button state
            stopScan();
        }
    }

    private void stopScan() {
        scanning = false;
        if (scanAnim != null) scanAnim.stop();
        if (simDelay != null) simDelay.stop();
        if (scanLine != null) scanLine.setVisible(false);
        if (idleIcon != null) idleIcon.setVisible(true);
        if (liveTag != null) liveTag.setVisible(false);
        if (hintLabel != null) hintLabel.setText("Camera idle — press the button below to scan");
        if (statusPill != null) setPill(false);
        if (scanBtn != null) setScanButton(false);
    }

    /** Demo only: pretends the camera read the barcode of the first borrowable book. */
    private void simulateDetection() {
        for (Book b : db.getBooks()) {
            if (b.getAvailableCopies() > 0 && !alreadyHas(b)) {
                onBarcodeScanned(b.getBarcode());
                return;
            }
        }
        stopScan();
        setBanner("Simulated scan found no borrowable books.", "warn");
    }

    /**
     * Entry point for real scans. Safe to call from any thread
     * (camera/ZXing worker, or a USB scanner's keyboard-wedge handler).
     */
    public void onBarcodeScanned(String code) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> onBarcodeScanned(code)); return; }
        stopScan();
        String n = norm(code);
        for (Book b : db.getBooks()) {
            if (norm(b.getBarcode()).equals(n) || (b.getIsbn() != null && norm(b.getIsbn()).equals(n))) {
                flashReticle(FXComponents.GREEN);
                pick(b);
                return;
            }
        }
        flashReticle(FXComponents.RED);
        FXComponents.shake(viewport);
        setBanner("Scanned code \"" + code + "\" isn't in the catalog. Try the manual search below.", "error");
    }

    private void flashReticle(String hex) {
        for (Polyline p : corners) p.setStroke(Color.web(hex));
        PauseTransition t = new PauseTransition(Duration.millis(900));
        t.setOnFinished(e -> { for (Polyline p : corners) p.setStroke(Color.web(FXComponents.RED)); });
        t.play();
    }

    private void setScanButton(boolean on) {
        scanBtn.setText(on ? "■  Stop Scanning" : "📷  Scan Barcode with Camera");
        scanBtn.getStyleClass().removeAll("btn-primary", "btn-ghost");
        scanBtn.getStyleClass().add(on ? "btn-ghost" : "btn-primary");
    }

    private void setPill(boolean live) {
        String c = live ? FXComponents.RED : FXComponents.GREEN;
        statusPill.setText(live ? "● Scanning…" : "● Camera ready");
        statusPill.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        statusPill.setTextFill(Color.web(c));
        statusPill.setStyle("-fx-background-color:" + c + "18; -fx-background-radius:20; -fx-padding:3 10 3 10;");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Manual entry + live search
    // ─────────────────────────────────────────────────────────────────────
    private void findByCode(String raw) {
        String code = norm(raw);
        if (code.isEmpty()) {
            FXComponents.flagInvalid(isbnField);
            setBanner("Enter an ISBN or Book ID first.", "warn");
            return;
        }
        for (Book b : db.getBooks()) {
            if (norm(b.getBarcode()).equals(code) || (b.getIsbn() != null && norm(b.getIsbn()).equals(code))) {
                isbnField.clear();
                pick(b);
                return;
            }
        }
        FXComponents.flagInvalid(isbnField);
        setBanner("No book found for \"" + raw.trim() + "\". Try the title search below.", "error");
    }

    private void updateResults(String text) {
        String q = text == null ? "" : text.trim().toLowerCase();
        if (q.isEmpty()) { hideResults(); return; }

        List<Book> hits = new ArrayList<>();
        for (Book b : db.getBooks()) {
            if (has(b.getTitle(), q) || has(b.getAuthor(), q) || has(b.getBarcode(), q)) {
                hits.add(b);
                if (hits.size() >= 20) break;
            }
        }
        resultsList.getItems().setAll(hits);
        int rows = Math.max(1, Math.min(hits.size(), MAX_VISIBLE_RESULTS));
        double h = rows * RESULT_ROW_H + 4;
        resultsList.setPrefHeight(h);
        resultsList.setMinHeight(h);
        resultsList.setMaxHeight(h);
        resultsList.setVisible(true);
        resultsList.setManaged(true);
    }

    private void hideResults() {
        resultsList.getItems().clear();
        resultsList.setVisible(false);
        resultsList.setManaged(false);
    }

    private Node resultRow(Book b) {
        Label title = new Label(b.getTitle());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12.5));
        title.setTextFill(Color.web(FXComponents.T_PRIMARY));
        Label sub = new Label(b.getAuthor() + "  ·  " + b.getBarcode());
        sub.setFont(Font.font("Segoe UI", 10.5));
        sub.setTextFill(Color.web(FXComponents.T_MUTED));
        VBox text = new VBox(2, title, sub);
        HBox.setHgrow(text, Priority.ALWAYS);

        boolean avail = b.getAvailableCopies() > 0;
        Label tag = pill(avail ? b.getAvailableCopies() + " available" : "All out",
            avail ? FXComponents.GREEN : FXComponents.RED);

        HBox row = new HBox(10, text, tag);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Selecting a book (from scan, ISBN, or the list) always ends up here. */
    private void pick(Book b) {
        clearBanner();
        suppressSearch = true;
        searchField.setText(b.getTitle());
        suppressSearch = false;
        hideResults();
        showDetail(b);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Book detail + borrow
    // ─────────────────────────────────────────────────────────────────────
    private void showDetail(Book b) {
        detailBox.getChildren().clear();

        Label tag = new Label("SELECTED BOOK");
        tag.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        tag.setTextFill(Color.web(FXComponents.T_MUTED));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button clear = FXComponents.ghostButton("✕ Clear");
        clear.setPadding(new Insets(3, 10, 3, 10));
        clear.setOnAction(e -> clearSelection());
        HBox tagRow = new HBox(tag, sp, clear);
        tagRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(b.getTitle());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web(FXComponents.T_PRIMARY));
        title.setWrapText(true);

        Label author = new Label("by " + b.getAuthor());
        author.setFont(Font.font("Segoe UI", 12.5));
        author.setTextFill(Color.web(FXComponents.T_SECOND));

        boolean avail = b.getAvailableCopies() > 0;
        HBox facts = new HBox(10,
            fact("CALL NO. / ID", callNumber(b), FXComponents.T_PRIMARY),
            fact("SECTION", b.getSection(), FXComponents.T_PRIMARY),
            fact("AVAILABILITY", b.getAvailableCopies() + " / " + b.getTotalCopies() + " copies",
                avail ? "#15803D" : FXComponents.RED));

        String reason = blockReason(b);
        Button borrow = FXComponents.primaryButton(reason == null ? "✓  Borrow Book" : reason);
        borrow.setDisable(reason != null);
        borrow.setMaxWidth(Double.MAX_VALUE);
        borrow.setPrefHeight(44);
        borrow.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        borrow.setOnAction(e -> doBorrow(b));

        detailBox.getChildren().addAll(tagRow, title, author, facts, borrow);
        detailBox.setVisible(true);
        detailBox.setManaged(true);
        FXComponents.fadeInSlide(detailBox);
    }

    private VBox fact(String caption, String value, String valueColor) {
        Label c = new Label(caption);
        c.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        c.setTextFill(Color.web(FXComponents.T_MUTED));
        Label v = new Label(value == null ? "—" : value);
        v.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12.5));
        v.setTextFill(Color.web(valueColor));
        v.setWrapText(true);
        VBox box = new VBox(2, c, v);
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setStyle("-fx-background-color:white; -fx-background-radius:10; -fx-border-color:#E4E8F0;"
            + "-fx-border-radius:10; -fx-padding:8 12 8 12;");
        return box;
    }

    private void clearSelection() {
        detailBox.getChildren().clear();
        detailBox.setVisible(false);
        detailBox.setManaged(false);
        suppressSearch = true;
        searchField.clear();
        suppressSearch = false;
        hideResults();
        clearBanner();
    }

    /** Returns why this book can't be borrowed right now, or null if it can. */
    private String blockReason(Book b) {
        if (b.getAvailableCopies() <= 0)             return "No copies available";
        if (student.getActiveLoans() >= KioskProfileCardFX.MAX_LOANS)
                                                      return "Loan limit reached (" + KioskProfileCardFX.MAX_LOANS + "/" + KioskProfileCardFX.MAX_LOANS + ")";
        if (alreadyHas(b))                            return "You already have this book";
        return null;
    }

    private boolean alreadyHas(Book b) {
        for (Transaction tx : KioskProfileCardFX.activeLoans(student)) {
            if (tx.getBookBarcode().equalsIgnoreCase(b.getBarcode())) return true;
        }
        return false;
    }

    private void doBorrow(Book b) {
        String reason = blockReason(b);
        if (reason != null) { setBanner(reason + ".", "warn"); showDetail(b); return; }

        b.setAvailableCopies(b.getAvailableCopies() - 1);
        student.setActiveLoans(student.getActiveLoans() + 1);
        db.addTransaction(new Transaction(student.getName(), student.getStudentId(),
            b.getTitle(), b.getBarcode(), Transaction.Type.BORROWED, LocalDateTime.now()));

        // Persist changes to PostgreSQL
        db.updateBookCopies(b);
        db.updateStudentLoans(student);

        String due = LocalDate.now().plusDays(KioskProfileCardFX.LOAN_DAYS)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        setBanner("Borrowed \"" + b.getTitle() + "\". Please return it by " + due + ".", "success");

        profileCard.refresh();
        kiosk.refreshDash();
        kiosk.refreshReturn();
        isbnField.clear();
        showDetail(b);   // re-render: now shows "You already have this book" and new copy count
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feedback banner + small UI helpers
    // ─────────────────────────────────────────────────────────────────────
    private void setBanner(String msg, String type) {
        String c, glyph;
        switch (type) {
            case "success": c = FXComponents.GREEN; glyph = "✓"; break;
            case "error":   c = FXComponents.RED;   glyph = "✕"; break;
            case "warn":    c = FXComponents.AMBER; glyph = "!"; break;
            default:        c = FXComponents.BLUE;  glyph = "i"; break;
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

    private Label pill(String text, String colorHex) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(colorHex));
        l.setStyle("-fx-background-color:" + colorHex + "18; -fx-background-radius:20; -fx-padding:3 10 3 10;");
        return l;
    }

    private Label fieldLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(FXComponents.T_MUTED));
        return l;
    }

    private HBox divider(String text) {
        Region a = new Region();
        Region b = new Region();
        for (Region r : new Region[]{a, b}) {
            r.setPrefHeight(1);
            r.setMaxHeight(1);
            r.setStyle("-fx-background-color:" + FXComponents.BORDER + ";");
            HBox.setHgrow(r, Priority.ALWAYS);
        }
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web(FXComponents.T_MUTED));
        HBox row = new HBox(10, a, l, b);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(4, 0, 2, 0));
        return row;
    }

    /**
     * The Book model exposes no separate call-number field, so the barcode doubles as the
     * call number / ID. If Book gains getCallNumber() or getIsbn(), return that here.
     */
    private String callNumber(Book b) { return b.getBarcode(); }

    private static boolean has(String s, String q) { return s != null && s.toLowerCase().contains(q); }

    /** Lower-cases and strips spaces/hyphens so "978-0-13" and "9780 13" both match. */
    private static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("[\\s-]", ""); }
}
