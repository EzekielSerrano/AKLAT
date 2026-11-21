package autolib.fx.kiosk;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Student profile card (left section of the kiosk Borrow screen).
 *
 * Dark "glass" card: translucent chips and tiles over a navy gradient with a soft red glow.
 * Shows name, program/department, student ID and three live counters:
 * borrowed books, next due date, overdue books (pulsing alert when &gt; 0).
 *
 * Call {@link #refresh()} after a borrow/return to update the counters in place.
 */
public class KioskProfileCardFX {

    public static final int MAX_LOANS     = 5;
    public static final int LOAN_DAYS     = 7;
    public static final int DUE_SOON_DAYS = 2;

    private static final String GLASS_BG     = "rgba(255,255,255,0.08)";
    private static final String GLASS_BORDER = "rgba(255,255,255,0.15)";

    private final Student student;
    private final VBox body = new VBox(12);

    public KioskProfileCardFX(Student student) {
        this.student = student;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /** Builds the card. The returned node is stretchable; set width from the parent. */
    public Region build() {
        body.setPadding(new Insets(22));

        Circle glow = new Circle(110, Color.web(FXComponents.RED, 0.30));
        glow.setEffect(new GaussianBlur(60));
        glow.setMouseTransparent(true);
        glow.setTranslateX(70);
        glow.setTranslateY(-70);

        StackPane inner = new StackPane(glow, body);
        StackPane.setAlignment(glow, Pos.TOP_RIGHT);
        StackPane.setAlignment(body, Pos.TOP_LEFT);
        inner.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #1B2150, #12163A);"
            + "-fx-background-radius:20; -fx-border-color:rgba(255,255,255,0.12);"
            + "-fx-border-radius:20; -fx-border-width:1;");

        // Clip on the inner pane, shadow on the outer one (a clip would otherwise cut the shadow off)
        Rectangle clip = new Rectangle();
        clip.setArcWidth(40);
        clip.setArcHeight(40);
        clip.widthProperty().bind(inner.widthProperty());
        clip.heightProperty().bind(inner.heightProperty());
        inner.setClip(clip);

        StackPane outer = new StackPane(inner);
        outer.setEffect(new DropShadow(26, 0, 8, Color.web("#12163A", 0.35)));

        refresh();
        return outer;
    }

    /** Rebuilds the contents from the current Student + DataStore state. */
    public void refresh() {
        body.getChildren().clear();
        body.getChildren().addAll(
            buildIdentity(),
            divider(),
            sectionLabel("LOAN SUMMARY"),
            buildBorrowedTile(),
            buildDueTile(),
            buildOverdueTile()
        );
        if (student.getOverdueCount() > 0) body.getChildren().add(buildOverdueNote());
    }

    /**
     * Books this student currently holds, worked out from the transaction log:
     * the latest transaction per barcode must be BORROWED or OVERDUE.
     */
    public static List<Transaction> activeLoans(Student s) {
        Map<String, Transaction> latest = new LinkedHashMap<>();
        for (Transaction tx : DataStore.get().getTransactions()) {
            if (!tx.getStudentId().equals(s.getStudentId())) continue;
            Transaction prev = latest.get(tx.getBookBarcode());
            if (prev == null || !tx.getTimestamp().isBefore(prev.getTimestamp()))
                latest.put(tx.getBookBarcode(), tx);
        }
        List<Transaction> out = new ArrayList<>();
        for (Transaction tx : latest.values()) {
            if (tx.getType() == Transaction.Type.BORROWED || tx.getType() == Transaction.Type.OVERDUE)
                out.add(tx);
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Identity block
    // ─────────────────────────────────────────────────────────────────────
    private Node buildIdentity() {
        VBox box = new VBox(12);

        String init = "";
        if (student.getName() != null && !student.getName().isBlank()) {
            String[] parts = student.getName().trim().split("[,\\s]+");
            if (parts.length >= 2) init = parts[0].substring(0, 1).toUpperCase() + parts[parts.length - 1].substring(0, 1).toUpperCase();
            else init = parts[0].substring(0, 1).toUpperCase();
        }
        StackPane avatar = FXComponents.avatar(init, FXComponents.RED, 58);

        Label name = new Label(nz(student.getName()));
        name.setFont(Font.font("Segoe UI", FontWeight.BOLD, 19));
        name.setTextFill(Color.WHITE);
        name.setWrapText(true);

        Label id = new Label(nz(student.getStudentId()));
        id.setFont(Font.font("Consolas", 13));
        id.setTextFill(Color.web("#AEB7CC"));

        VBox nameBox = new VBox(3, name, id);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        HBox top = new HBox(14, avatar, nameBox);
        top.setAlignment(Pos.CENTER_LEFT);

        FlowPane chips = new FlowPane(6, 6);
        chips.getChildren().addAll(
            chip(nz(student.getDepartment()), "rgba(59,130,246,0.30)", "#BFD6FF"),
            chip(nz(student.getYear()), GLASS_BG, "#D5DAE8"),
            student.isNfcRegistered()
                ? chip("✓ NFC Registered", "rgba(34,197,94,0.25)", "#9AF0BC")
                : chip("⏳ NFC Pending", "rgba(202,138,4,0.28)", "#FCD34D")
        );

        box.getChildren().addAll(top, chips);
        return box;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Counter tiles
    // ─────────────────────────────────────────────────────────────────────
    private Node buildBorrowedTile() {
        int active = student.getActiveLoans();

        Label value = bigNumber();
        FXComponents.countUp(value, active, 900, 100);
        Label of = new Label("/ " + MAX_LOANS);
        of.setFont(Font.font("Segoe UI", 12));
        of.setTextFill(Color.web("#8C96AA"));
        HBox valueRow = new HBox(6, value, of);
        valueRow.setAlignment(Pos.BASELINE_LEFT);

        HBox pips = new HBox(4);
        for (int i = 0; i < MAX_LOANS; i++) {
            Region pip = new Region();
            pip.setPrefSize(26, 5);
            pip.setMinSize(26, 5);
            pip.setMaxSize(26, 5);
            pip.setStyle("-fx-background-radius:3; -fx-background-color:"
                + (i < active ? FXComponents.BLUE : "rgba(255,255,255,0.14)") + ";");
            pips.getChildren().add(pip);
        }
        return tile("📚", "BORROWED BOOKS", valueRow, FXComponents.BLUE, false, pips);
    }

    private Node buildDueTile() {
        LocalDate today = LocalDate.now();
        LocalDate nextDue = null;
        for (Transaction tx : activeLoans(student)) {
            LocalDate due = tx.getTimestamp().toLocalDate().plusDays(LOAN_DAYS);
            if (nextDue == null || due.isBefore(nextDue)) nextDue = due;
        }

        Label value = bigNumber();
        Label sub = new Label();
        sub.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        String accent;

        if (nextDue == null) {
            value.setText("—");
            sub.setText("No pending returns");
            sub.setTextFill(Color.web("#8C96AA"));
            accent = FXComponents.T_MUTED;
        } else {
            long days = ChronoUnit.DAYS.between(today, nextDue);
            value.setText(nextDue.format(DateTimeFormatter.ofPattern("MMM d")));
            if (days < 0) {
                sub.setText("Overdue by " + (-days) + " day" + (days == -1 ? "" : "s"));
                accent = FXComponents.RED;
            } else if (days == 0) {
                sub.setText("Due today");
                accent = FXComponents.AMBER;
            } else if (days <= DUE_SOON_DAYS) {
                sub.setText("Due soon · in " + days + " day" + (days == 1 ? "" : "s"));
                accent = FXComponents.AMBER;
            } else {
                sub.setText("In " + days + " days");
                accent = FXComponents.GREEN;
            }
            sub.setTextFill(Color.web(brighten(accent)));
        }
        return tile("🗓", "NEXT DUE DATE", value, accent, false, sub);
    }

    private Node buildOverdueTile() {
        int overdue = student.getOverdueCount();
        boolean alert = overdue > 0;

        Label value = bigNumber();
        FXComponents.countUp(value, overdue, 900, 250);

        Label sub = new Label(alert ? "⚠ Return ASAP" : "All clear");
        sub.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        sub.setTextFill(Color.web(alert ? "#FF8A93" : "#9AF0BC"));

        return tile("⏰", "OVERDUE BOOKS", value, alert ? FXComponents.RED : FXComponents.GREEN, alert, sub);
    }

    private Node buildOverdueNote() {
        Label l = new Label("Overdue books add a ₱5.00 / day penalty. Please return them at the counter or use Return a Book.");
        l.setFont(Font.font("Segoe UI", 10.5));
        l.setTextFill(Color.web("#FFB3B9"));
        l.setWrapText(true);
        return l;
    }

    /** One glass tile: icon badge, caption, value, optional extra node under it. */
    private VBox tile(String icon, String caption, Node value, String accent, boolean alert, Node extra) {
        StackPane badge = new StackPane();
        badge.setMinSize(36, 36);
        badge.setMaxSize(36, 36);
        badge.setStyle("-fx-background-radius:11; -fx-background-color:" + accent + "33;");
        Label ic = new Label(icon);
        ic.setFont(Font.font("Segoe UI Emoji", 16));
        badge.getChildren().add(ic);

        Label cap = new Label(caption);
        cap.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        cap.setTextFill(Color.web("#8C96AA"));

        VBox text = new VBox(1, cap, value);
        HBox top = new HBox(12, badge, text);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox tile = new VBox(8, top);
        if (extra != null) tile.getChildren().add(extra);
        tile.setPadding(new Insets(11, 13, 11, 13));
        tile.setStyle("-fx-background-radius:14; -fx-border-radius:14; -fx-border-width:1;"
            + (alert
                ? "-fx-background-color:rgba(204,41,54,0.26); -fx-border-color:rgba(255,107,118,0.7);"
                : "-fx-background-color:" + GLASS_BG + "; -fx-border-color:" + GLASS_BORDER + ";"));

        if (alert) {
            DropShadow pulse = new DropShadow(4, Color.web(FXComponents.RED, 0.35));
            tile.setEffect(pulse);
            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pulse.radiusProperty(), 4, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(900), new KeyValue(pulse.radiusProperty(), 18, Interpolator.EASE_BOTH)));
            tl.setAutoReverse(true);
            tl.setCycleCount(Animation.INDEFINITE);
            FXComponents.animateWhileShowing(tile, tl);
        }
        return tile;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Small helpers
    // ─────────────────────────────────────────────────────────────────────
    private Label bigNumber() {
        Label l = new Label("0");
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        l.setTextFill(Color.WHITE);
        return l;
    }

    private Label chip(String text, String bg, String fg) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10.5));
        l.setTextFill(Color.web(fg));
        l.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:20;"
            + "-fx-border-color:" + GLASS_BORDER + "; -fx-border-radius:20; -fx-padding:4 11 4 11;");
        return l;
    }

    private Region divider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setStyle("-fx-background-color:rgba(255,255,255,0.12);");
        VBox.setMargin(r, new Insets(2, 0, 2, 0));
        return r;
    }

    private Label sectionLabel(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        l.setTextFill(Color.web("#6F7A94"));
        return l;
    }

    private static String brighten(String hex) {
        return switch (hex) {
            case FXComponents.RED   -> "#FF8A93";
            case FXComponents.GREEN -> "#9AF0BC";
            case FXComponents.AMBER -> "#FCD34D";
            default                 -> "#AEB7CC";
        };
    }

    private static String nz(String s) { return s == null ? "" : s; }
}

