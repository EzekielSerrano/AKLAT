package autolib.fx.panels;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import autolib.models.Book;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Dashboard panel — stat cards with count-up animation,
 * borrowing-activity bar chart, recent transactions, most-borrowed,
 * overdue alerts.
 */
public class DashboardFX {

    private final LibrarianPortalFX portal;
    private final DataStore db = DataStore.get();

    public DashboardFX(LibrarianPortalFX portal) {
        this.portal = portal;
    }

    public Node build() {
        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color:#F4F6FA;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color:#F4F6FA;");

        // ── Stat cards ──────────────────────────────────────────────────
        HBox statsRow = new HBox(12);
        statsRow.setMaxWidth(Double.MAX_VALUE);

        // Live dynamic subtitles
        long nfcCount = db.getStudents().stream().filter(s -> s.isNfcRegistered()).count();
        long borrowedActive = db.getTransactions().stream()
            .filter(t -> t.getType() == Transaction.Type.BORROWED).count();
        int overdueNow = db.overdueBooks();
        int totalTitles = db.getBooks().size();

        VBox c1 = FXComponents.statCard("📚", db.totalBooks(),        "Total Books",          totalTitles + " titles in catalog",                                         FXComponents.GREEN);
        VBox c2 = FXComponents.statCard("📦", db.currentlyBorrowed(), "Currently Borrowed",   borrowedActive + " active loan" + (borrowedActive == 1 ? "" : "s"),        FXComponents.T_MUTED);
        VBox c3 = FXComponents.statCard("⏰", db.overdueBooks(),       "Overdue Books",        overdueNow == 0 ? "All returned on time ✔" : overdueNow + " need attention", FXComponents.RED);
        VBox c4 = FXComponents.statCard("👤", db.registeredStudents(), "Registered Students",  nfcCount + " with NFC card",                                                FXComponents.GREEN);

        for (VBox c : new VBox[]{c1, c2, c3, c4}) {
            HBox.setHgrow(c, Priority.ALWAYS);
            statsRow.getChildren().add(c);
        }

        // ── Middle row: chart + recent transactions ─────────────────────
        HBox midRow = new HBox(12);
        midRow.setMaxWidth(Double.MAX_VALUE);
        Node chartCard = buildChartCard();
        Node txCard    = buildRecentTxCard();
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        HBox.setHgrow(txCard, Priority.ALWAYS);
        midRow.getChildren().addAll(chartCard, txCard);
        midRow.setPrefHeight(260);
        midRow.setMaxHeight(260);

        // ── Bottom row: most borrowed + overdue ────────────────────────
        HBox bottomRow = new HBox(12);
        bottomRow.setMaxWidth(Double.MAX_VALUE);
        Node mbCard  = buildMostBorrowedCard();
        Node ovCard  = buildOverdueCard();
        HBox.setHgrow(mbCard, Priority.ALWAYS);
        HBox.setHgrow(ovCard, Priority.ALWAYS);
        bottomRow.getChildren().addAll(mbCard, ovCard);

        content.getChildren().addAll(statsRow, midRow, bottomRow);
        sp.setContent(content);
        return sp;
    }

    // ── Bar chart card ─────────────────────────────────────────────────────
    private Node buildChartCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));

        // Toggle
        Button wkBtn = UIToggleBtn("Week", true);
        Button moBtn = UIToggleBtn("Month", false);
        HBox toggle = new HBox(2, wkBtn, moBtn);
        toggle.setAlignment(Pos.CENTER_RIGHT);

        HBox hdr = FXComponents.cardHeader("Borrowing Activity", toggle);

        // Bar chart pane (canvas-drawn)
        BarChartPane chart = new BarChartPane(
            db.weeklyBorrowData(),
            new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"});
        VBox.setVgrow(chart, Priority.ALWAYS);

        wkBtn.setOnAction(e -> {
            chart.setData(db.weeklyBorrowData(), new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"});
            setActiveToggle(wkBtn, moBtn);
        });
        moBtn.setOnAction(e -> {
            chart.setData(db.monthlyBorrowData(), new String[]{"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"});
            setActiveToggle(moBtn, wkBtn);
        });

        card.getChildren().addAll(hdr, chart);
        return card;
    }

    // ── Recent transactions card ───────────────────────────────────────────
    private Node buildRecentTxCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 12, 18));

        Label viewAll = new Label("View all →");
        viewAll.setFont(Font.font("Segoe UI", 11));
        viewAll.setTextFill(Color.web(FXComponents.RED));
        viewAll.setStyle("-fx-cursor:hand;");

        HBox hdr = FXComponents.cardHeader("Recent Transactions", viewAll);

        VBox list = new VBox(4);
        String[] colors = {FXComponents.BLUE, FXComponents.GREEN, FXComponents.ORANGE, "#A855F7", "#14B8A6"};
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h:mm a");
        int ci = 0;
        List<Transaction> allTx = db.getTransactions();
        int maxItems = Math.min(5, allTx.size());
        for (int i = 0; i < maxItems; i++) {
            Transaction tx = allTx.get(i);
            list.getChildren().add(txRow(tx, colors[ci % colors.length], fmt));
            ci++;
        }
        if (allTx.isEmpty()) {
            Label emptyLbl = new Label("No recent transactions");
            emptyLbl.setFont(Font.font("Segoe UI", 12));
            emptyLbl.setTextFill(Color.web(FXComponents.T_MUTED));
            emptyLbl.setPadding(new Insets(8, 0, 0, 0));
            list.getChildren().add(emptyLbl);
        }

        card.getChildren().addAll(hdr, list);
        return card;
    }

    private HBox txRow(Transaction tx, String avatarColor, DateTimeFormatter fmt) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));

        // Hover highlight + smooth slide
        row.setOnMouseEntered(e -> {
            row.setStyle("-fx-background-color:#FDF2F3; -fx-background-radius:8;");
            TranslateTransition tt = new TranslateTransition(Duration.millis(120), row);
            tt.setToX(4); tt.play();
        });
        row.setOnMouseExited(e -> {
            row.setStyle("-fx-background-color:transparent;");
            TranslateTransition tt = new TranslateTransition(Duration.millis(120), row);
            tt.setToX(0); tt.play();
        });

        String init = tx.getStudentInitial();
        StackPane av = FXComponents.avatar(init, avatarColor, 30);

        VBox mid = new VBox(1);
        HBox.setHgrow(mid, Priority.ALWAYS);
        String sName = (tx.getStudentName() != null && !tx.getStudentName().trim().isEmpty())
            ? tx.getStudentName() : "Unknown Student";
        Label nameL = new Label(sName);
        nameL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        nameL.setTextFill(Color.web(FXComponents.T_PRIMARY));
        String bTitle = (tx.getBookTitle() != null && !tx.getBookTitle().trim().isEmpty())
            ? tx.getBookTitle() : "Unknown Title";
        Label bookL = new Label(bTitle);
        bookL.setFont(Font.font("Segoe UI", 10));
        bookL.setTextFill(Color.web(FXComponents.T_MUTED));
        mid.getChildren().addAll(nameL, bookL);

        VBox right = new VBox(2);
        right.setAlignment(Pos.CENTER_RIGHT);
        String timeStr = "Recent";
        if (tx.getTimestamp() != null) {
            timeStr = tx.getTimestamp().toLocalDate().equals(java.time.LocalDate.now())
                ? tx.getTimestamp().format(fmt) : "Yesterday";
        }
        Label timeL = new Label(timeStr);
        timeL.setFont(Font.font("Segoe UI", 10));
        timeL.setTextFill(Color.web(FXComponents.T_MUTED));
        right.getChildren().addAll(timeL, FXComponents.txBadge(tx.getTypeText() != null ? tx.getTypeText() : "Borrowed"));

        row.getChildren().addAll(av, mid, right);
        return row;
    }

    // ── Most borrowed card ─────────────────────────────────────────────────
    private Node buildMostBorrowedCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));

        Label title = new Label("Most Borrowed Books");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(Color.web(FXComponents.T_PRIMARY));

        TableView<Book> tbl = new TableView<>();
        tbl.getStyleClass().add("table-view");
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Book, String> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(d -> {
            int row = tbl.getItems().indexOf(d.getValue()) + 1;
            return new javafx.beans.property.SimpleStringProperty(String.valueOf(row));
        });
        rankCol.setMaxWidth(36);
        rankCol.setMinWidth(36);

        TableColumn<Book, String> titleCol = new TableColumn<>("TITLE");
        titleCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getTitle()));

        TableColumn<Book, String> authCol = new TableColumn<>("AUTHOR");
        authCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getAuthor()));

        TableColumn<Book, String> borrowCol = new TableColumn<>("BORROWED");
        borrowCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getBorrowedCount() + "×"));
        borrowCol.setMaxWidth(80);

        tbl.getColumns().addAll(rankCol, titleCol, authCol, borrowCol);
        tbl.getItems().addAll(db.mostBorrowed());
        tbl.setMaxHeight(180);

        card.getChildren().addAll(title, tbl);
        VBox.setVgrow(tbl, Priority.ALWAYS);
        return card;
    }

    // ── Overdue alerts card ────────────────────────────────────────────────
    private Node buildOverdueCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));

        Label cnt = FXComponents.badge(String.valueOf(db.overdueBooks()), "badge-red");
        HBox hdr = FXComponents.cardHeader("Overdue Alerts", cnt);

        VBox list = new VBox(0);
        list.setPadding(new Insets(10, 0, 0, 0));

        // Live overdue data from real transactions
        List<Transaction> overdueList = db.getTransactions().stream()
            .filter(t -> t.getType() == Transaction.Type.OVERDUE)
            .sorted(Comparator.comparingInt(Transaction::getDaysOverdue).reversed())
            .collect(java.util.stream.Collectors.toList());

        if (overdueList.isEmpty()) {
            Label emptyLbl = new Label("No overdue books \u2714");
            emptyLbl.setFont(Font.font("Segoe UI", 12));
            emptyLbl.setTextFill(Color.web(FXComponents.GREEN));
            emptyLbl.setPadding(new Insets(8, 0, 0, 0));
            list.getChildren().add(emptyLbl);
        } else {
            for (Transaction t : overdueList) {
                String daysStr = "+" + t.getDaysOverdue() + " day" + (t.getDaysOverdue() == 1 ? "" : "s");
                list.getChildren().add(overdueRow(t.getStudentName(), t.getBookTitle(), daysStr));
            }
        }

        card.getChildren().addAll(hdr, list);
        return card;
    }

    private HBox overdueRow(String student, String book, String days) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new Insets(8, 0, 8, 10));
        row.setStyle("-fx-border-color:transparent transparent transparent " + FXComponents.RED + "; -fx-border-width:0 0 0 3; -fx-padding:6 0 6 10;");

        // Hover highlight + smooth slide
        row.setOnMouseEntered(e -> {
            row.setStyle("-fx-border-color:transparent transparent transparent " + FXComponents.RED + "; -fx-border-width:0 0 0 3; -fx-padding:6 0 6 10; -fx-background-color:#FDF2F3; -fx-background-radius:4;");
            TranslateTransition tt = new TranslateTransition(Duration.millis(120), row);
            tt.setToX(4); tt.play();
        });
        row.setOnMouseExited(e -> {
            row.setStyle("-fx-border-color:transparent transparent transparent " + FXComponents.RED + "; -fx-border-width:0 0 0 3; -fx-padding:6 0 6 10;");
            TranslateTransition tt = new TranslateTransition(Duration.millis(120), row);
            tt.setToX(0); tt.play();
        });

        VBox txt = new VBox(1);
        HBox.setHgrow(txt, Priority.ALWAYS);
        Label sL = new Label(student != null && !student.trim().isEmpty() ? student : "Unknown Student");
        sL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        sL.setTextFill(Color.web(FXComponents.T_PRIMARY));
        Label bL = new Label(book != null && !book.trim().isEmpty() ? book : "Untitled Book");
        bL.setFont(Font.font("Segoe UI", 10));
        bL.setTextFill(Color.web(FXComponents.T_MUTED));
        txt.getChildren().addAll(sL, bL);

        Label dL = new Label(days);
        dL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        dL.setTextFill(Color.web(FXComponents.RED));

        row.getChildren().addAll(txt, dL);
        return row;
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private Button UIToggleBtn(String text, boolean active) {
        Button b = new Button(text);
        b.setFont(Font.font("Segoe UI", 11));
        if (active) {
            b.setStyle("-fx-background-color:#CC2936; -fx-text-fill:white; -fx-background-radius:6; -fx-padding:4 12 4 12; -fx-cursor:hand;");
        } else {
            b.setStyle("-fx-background-color:#F0F2F8; -fx-text-fill:#5A6480; -fx-background-radius:6; -fx-padding:4 12 4 12; -fx-cursor:hand;");
        }
        return b;
    }

    private void setActiveToggle(Button active, Button other) {
        active.setStyle("-fx-background-color:#CC2936; -fx-text-fill:white; -fx-background-radius:6; -fx-padding:4 12 4 12; -fx-cursor:hand;");
        other.setStyle("-fx-background-color:#F0F2F8; -fx-text-fill:#5A6480; -fx-background-radius:6; -fx-padding:4 12 4 12; -fx-cursor:hand;");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Animated canvas bar chart
    // ─────────────────────────────────────────────────────────────────────
    static class BarChartPane extends javafx.scene.canvas.Canvas {
        private int[] data;
        private String[] labels;
        private Timeline animTimeline;

        BarChartPane(int[] data, String[] labels) {
            super(400, 180);
            this.data = data;
            this.labels = labels;
            widthProperty().addListener(o -> draw(1.0));
            setData(data, labels);
        }

        public void setData(int[] d, String[] l) {
            this.data = d;
            this.labels = l;
            animateIn();
        }

        private void animateIn() {
            if (animTimeline != null) animTimeline.stop();
            animTimeline = new Timeline();
            int steps = 30;
            for (int i = 1; i <= steps; i++) {
                final double pct = (double) i / steps;
                animTimeline.getKeyFrames().add(
                    new KeyFrame(Duration.millis(400.0 / steps * i), e -> draw(pct)));
            }
            animTimeline.play();
        }

        private void draw(double pct) {
            javafx.scene.canvas.GraphicsContext gc = getGraphicsContext2D();
            double W = getWidth(), H = getHeight();
            gc.clearRect(0, 0, W, H);
            if (data == null || data.length == 0) return;

            // Check if all values are zero — show placeholder
            int max = 0;
            for (int v : data) if (v > max) max = v;
            if (max == 0) {
                gc.setFill(Color.web(FXComponents.T_MUTED));
                gc.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
                String msg = "No borrow activity yet";
                javafx.scene.text.Text t = new javafx.scene.text.Text(msg);
                t.setFont(gc.getFont());
                double tw = t.getBoundsInLocal().getWidth();
                gc.fillText(msg, (W - tw) / 2, H / 2 + 5);
                // Still draw day labels at bottom
                double pad2 = 10, labelH2 = 22;
                double barW2 = (W - pad2 * 2) / data.length;
                double gap2 = barW2 * 0.25, bw2 = barW2 - gap2;
                for (int i = 0; i < data.length && i < labels.length; i++) {
                    double x2 = pad2 + i * barW2 + gap2 / 2 + bw2 / 2;
                    javafx.scene.text.Text lt = new javafx.scene.text.Text(labels[i]);
                    lt.setFont(gc.getFont());
                    double tw2 = lt.getBoundsInLocal().getWidth();
                    gc.fillText(labels[i], x2 - tw2 / 2, H - 4);
                }
                return;
            }

            double pad = 10, labelH = 22, topPad = 10;
            double chartH = H - labelH - topPad;
            int n = data.length;
            double barW = (W - pad * 2) / n;
            double gap = barW * 0.25;
            double bw = barW - gap;

            // Grid lines
            gc.setStroke(Color.web("#E8ECF3"));
            gc.setLineWidth(0.8);
            for (int i = 0; i <= 3; i++) {
                double y = topPad + chartH * i / 3.0;
                gc.strokeLine(pad, y, W - pad, y);
            }

            // Bars
            for (int i = 0; i < n; i++) {
                double barH = (double) data[i] / max * chartH * pct;
                double x = pad + i * barW + gap / 2;
                double y = topPad + chartH - barH;
                boolean isMax = (data[i] == max);
                gc.setFill(isMax ? Color.web(FXComponents.RED) : Color.web("#D2D6E2"));
                // Rounded top
                gc.fillRoundRect(x, y, bw, barH, 5, 5);
                // Show count on top of bar
                if (data[i] > 0) {
                    gc.setFill(Color.web(FXComponents.T_PRIMARY));
                    gc.setFont(javafx.scene.text.Font.font("Segoe UI", 9));
                    String val = String.valueOf(data[i]);
                    javafx.scene.text.Text vt = new javafx.scene.text.Text(val);
                    vt.setFont(gc.getFont());
                    double vw = vt.getBoundsInLocal().getWidth();
                    gc.fillText(val, x + bw / 2 - vw / 2, y - 2);
                }
            }

            // Labels
            gc.setFill(Color.web(FXComponents.T_MUTED));
            gc.setFont(javafx.scene.text.Font.font("Segoe UI", 10));
            for (int i = 0; i < n && i < labels.length; i++) {
                double x = pad + i * barW + gap / 2 + bw / 2;
                javafx.scene.text.Text t = new javafx.scene.text.Text(labels[i]);
                t.setFont(gc.getFont());
                double tw = t.getBoundsInLocal().getWidth();
                gc.fillText(labels[i], x - tw / 2, H - 4);
            }
        }

        @Override public boolean isResizable() { return true; }
        @Override public double prefWidth(double h) { return 300; }
        @Override public double prefHeight(double w) { return 180; }
    }
}

