package autolib.fx.panels;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import autolib.models.Book;
import autolib.models.Student;
import autolib.models.Transaction;
import javafx.collections.FXCollections;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
        genBtn.setOnAction(e -> generate(title));

        card.getChildren().addAll(iconLbl, titleLbl, subLbl, genBtn);
        FXComponents.addHoverLift(card);
        return card;
    }

    // ───────────────────────── Report generation ─────────────────────────

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** A generated report: title, summary line, column headers and rows. */
    private static class Report {
        final String title;
        String summary = "";
        String[] headers = new String[0];
        List<String[]> rows = new ArrayList<>();
        Report(String title) { this.title = title; }
    }

    private void generate(String title) {
        try {
            switch (title) {
                case "Borrowing Summary": showBorrowingSummary(); break;
                case "Export Data":       showExport(); break;
                default:                  showReport(build(title), null); break;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR, "Could not generate report: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Report");
            a.showAndWait();
        }
    }

    private Report build(String title) {
        switch (title) {
            case "Overdue Report":    return overdueReport();
            case "Inventory Report":  return inventoryReport();
            case "Student Activity":  return studentActivityReport();
            case "Popular Books":     return popularBooksReport();
            default:                  return borrowingReport("Monthly");
        }
    }

    private Report borrowingReport(String period) {
        LocalDate today = LocalDate.now();
        int days = period.equals("Daily") ? 1 : period.equals("Weekly") ? 7 : 30;
        LocalDate from = today.minusDays(days - 1L);

        Map<LocalDate, int[]> perDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) perDay.put(d, new int[3]);

        int b = 0, r = 0, o = 0;
        for (Transaction t : DataStore.get().getTransactions()) {
            if (t.getTimestamp() == null) continue;
            int[] slot = perDay.get(t.getTimestamp().toLocalDate());
            if (slot == null) continue;
            switch (t.getType()) {
                case BORROWED: slot[0]++; b++; break;
                case RETURNED: slot[1]++; r++; break;
                case OVERDUE:  slot[2]++; o++; break;
            }
        }
        Report rep = new Report("Borrowing Summary (" + period + ")");
        rep.headers = new String[]{"Date", "Borrowed", "Returned", "Overdue"};
        for (Map.Entry<LocalDate, int[]> e : perDay.entrySet())
            rep.rows.add(new String[]{e.getKey().toString(), "" + e.getValue()[0], "" + e.getValue()[1], "" + e.getValue()[2]});
        rep.summary = from + " to " + today + "  •  Borrowed: " + b + "  •  Returned: " + r + "  •  Overdue: " + o;
        return rep;
    }

    private Report overdueReport() {
        Report rep = new Report("Overdue Report");
        rep.headers = new String[]{"Student ID", "Student", "Book", "Barcode", "Date", "Days Overdue"};
        for (Transaction t : DataStore.get().overdueAlerts())
            rep.rows.add(new String[]{nz(t.getStudentId()), nz(t.getStudentName()), nz(t.getBookTitle()),
                    nz(t.getBookBarcode()), t.getTimestamp() == null ? "" : t.getTimestamp().format(DT),
                    "" + t.getDaysOverdue()});
        rep.summary = rep.rows.size() + " overdue record(s)";
        return rep;
    }

    private Report inventoryReport() {
        Map<String, int[]> bySection = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int titles = 0, total = 0, avail = 0;
        for (Book b : DataStore.get().getBooks()) {
            String sec = (b.getSection() == null || b.getSection().isBlank()) ? "Uncategorized" : b.getSection().trim();
            int[] v = bySection.computeIfAbsent(sec, k -> new int[4]);
            v[0]++; v[1] += b.getTotalCopies(); v[2] += b.getAvailableCopies(); v[3] += b.getBorrowedCount();
            titles++; total += b.getTotalCopies(); avail += b.getAvailableCopies();
        }
        Report rep = new Report("Inventory Report");
        rep.headers = new String[]{"Section", "Titles", "Total Copies", "Available", "Borrowed"};
        for (Map.Entry<String, int[]> e : bySection.entrySet()) {
            int[] v = e.getValue();
            rep.rows.add(new String[]{e.getKey(), "" + v[0], "" + v[1], "" + v[2], "" + v[3]});
        }
        rep.summary = titles + " titles  •  " + total + " copies  •  " + avail + " available  •  " + (total - avail) + " borrowed";
        return rep;
    }

    private Report studentActivityReport() {
        Map<String, int[]> counts = new HashMap<>(); // borrowed, returned, overdue
        for (Transaction t : DataStore.get().getTransactions()) {
            if (t.getStudentId() == null) continue;
            int[] v = counts.computeIfAbsent(t.getStudentId(), k -> new int[3]);
            switch (t.getType()) {
                case BORROWED: v[0]++; break;
                case RETURNED: v[1]++; break;
                case OVERDUE:  v[2]++; break;
            }
        }
        Report rep = new Report("Student Activity");
        rep.headers = new String[]{"Student ID", "Name", "Department", "Year", "Borrowed", "Returned", "Overdue", "Active Loans"};
        for (Student s : DataStore.get().getStudents()) {
            int[] v = counts.getOrDefault(s.getStudentId(), new int[3]);
            rep.rows.add(new String[]{nz(s.getStudentId()), nz(s.getName()), nz(s.getDepartment()), nz(s.getYear()),
                    "" + v[0], "" + v[1], "" + v[2], "" + s.getActiveLoans()});
        }
        rep.rows.sort((a, b) -> Integer.parseInt(b[4]) - Integer.parseInt(a[4]));
        rep.summary = rep.rows.size() + " student(s)";
        return rep;
    }

    private Report popularBooksReport() {
        LocalDate now = LocalDate.now();
        Map<String, Object[]> byTitle = new HashMap<>(); // title -> {barcode, count}
        for (Transaction t : DataStore.get().getTransactions()) {
            if (t.getType() != Transaction.Type.BORROWED || t.getTimestamp() == null) continue;
            LocalDate d = t.getTimestamp().toLocalDate();
            if (d.getYear() != now.getYear() || d.getMonth() != now.getMonth()) continue;
            Object[] v = byTitle.computeIfAbsent(nz(t.getBookTitle()), k -> new Object[]{nz(t.getBookBarcode()), 0});
            v[1] = (Integer) v[1] + 1;
        }
        List<Map.Entry<String, Object[]>> list = new ArrayList<>(byTitle.entrySet());
        list.sort((a, b) -> (Integer) b.getValue()[1] - (Integer) a.getValue()[1]);
        Report rep = new Report("Popular Books (This Month)");
        rep.headers = new String[]{"Rank", "Title", "Barcode", "Times Borrowed"};
        int rank = 1;
        for (Map.Entry<String, Object[]> e : list) {
            if (rank > 20) break;
            rep.rows.add(new String[]{"" + rank++, e.getKey(), (String) e.getValue()[0], "" + e.getValue()[1]});
        }
        rep.summary = "Top " + rep.rows.size() + " most borrowed title(s) in " + now.getMonth() + " " + now.getYear();
        return rep;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // ───────────────────────── Dialogs ─────────────────────────

    private void showBorrowingSummary() {
        ComboBox<String> period = new ComboBox<>(FXCollections.observableArrayList("Daily", "Weekly", "Monthly"));
        period.setValue("Weekly");
        showReport(borrowingReport("Weekly"), period);
    }

    private void showReport(Report initial, ComboBox<String> periodPicker) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Report");
        dlg.setHeaderText(null);
        if (portal.getStage() != null) dlg.initOwner(portal.getStage());
        dlg.setResizable(true);

        Report[] current = {initial};
        Label titleLbl = new Label(initial.title);
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        Label sumLbl = new Label(initial.summary);
        sumLbl.setFont(Font.font("Segoe UI", 12));
        sumLbl.setWrapText(true);
        sumLbl.setTextFill(Color.web(FXComponents.T_MUTED));

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No data for this report."));
        table.setPrefSize(780, 400);
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable fill = () -> {
            Report r = current[0];
            titleLbl.setText(r.title);
            sumLbl.setText(r.summary);
            table.getColumns().clear();
            for (int i = 0; i < r.headers.length; i++) {
                final int idx = i;
                TableColumn<String[], String> col = new TableColumn<>(r.headers[i]);
                col.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                        idx < cd.getValue().length ? cd.getValue()[idx] : ""));
                table.getColumns().add(col);
            }
            table.setItems(FXCollections.observableArrayList(r.rows));
        };
        fill.run();

        HBox top = new HBox(12, titleLbl);
        top.setAlignment(Pos.CENTER_LEFT);
        if (periodPicker != null) {
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            top.getChildren().addAll(sp, new Label("Period:"), periodPicker);
            periodPicker.setOnAction(ev -> {
                current[0] = borrowingReport(periodPicker.getValue());
                fill.run();
            });
        }

        Button csvBtn = FXComponents.primaryButton("Export CSV");
        csvBtn.setOnAction(ev -> exportCsv(current[0], dlg.getOwner()));

        VBox content = new VBox(10, top, sumLbl, table, csvBtn);
        content.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private void showExport() {
        String[] names = {"Borrowing Summary (Monthly)", "Overdue Report", "Inventory Report",
                "Student Activity", "Popular Books"};
        ChoiceDialog<String> pick = new ChoiceDialog<>(names[0], names);
        pick.setTitle("Export Data");
        pick.setHeaderText(null);
        pick.setContentText("Report to export as CSV:");
        if (portal.getStage() != null) pick.initOwner(portal.getStage());
        Optional<String> res = pick.showAndWait();
        if (res.isEmpty()) return;
        String choice = res.get();
        Report rep = choice.startsWith("Borrowing") ? borrowingReport("Monthly") : build(choice);
        exportCsv(rep, portal.getStage());
    }

    private void exportCsv(Report r, javafx.stage.Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save report as CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        String safe = r.title.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "");
        fc.setInitialFileName(safe + "_" + LocalDate.now() + ".csv");
        File f = fc.showSaveDialog(owner);
        if (f == null) return;
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8)) {
            w.write('\uFEFF'); // BOM so Excel reads UTF-8 correctly
            w.println(csvLine(r.headers));
            for (String[] row : r.rows) w.println(csvLine(row));
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Saved to:\n" + f.getAbsolutePath(), ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Export complete");
            a.showAndWait();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Could not save file: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    private static String csvLine(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            String c = cells[i] == null ? "" : cells[i];
            if (c.contains(",") || c.contains("\"") || c.contains("\n"))
                c = "\"" + c.replace("\"", "\"\"") + "\"";
            sb.append(c);
        }
        return sb.toString();
    }
}
