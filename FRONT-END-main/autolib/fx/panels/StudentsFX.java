package autolib.fx.panels;

import autolib.DataStore;
import autolib.fx.FXComponents;
import autolib.fx.LibrarianPortalFX;
import autolib.models.Student;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;

/**
 * Students panel — searchable table with NFC badges, loan counts,
 * inline action buttons (Edit, NFC link, Delete), and comprehensive editing.
 */
public class StudentsFX {

    private final LibrarianPortalFX portal;
    private final DataStore db = DataStore.get();
    private TextField searchField;
    private ComboBox<String> deptCombo;
    private TableView<Student> table;
    private Label countLabel;

    // Feedback banner
    private HBox banner;
    private Label bannerLabel;

    public StudentsFX(LibrarianPortalFX portal) { this.portal = portal; }

    public Node build() {
        VBox wrapper = new VBox(0);
        wrapper.setStyle("-fx-background-color:#F4F6FA;");
        wrapper.setPadding(new Insets(20));

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 18, 16, 18));
        VBox.setVgrow(card, Priority.ALWAYS);

        // Feedback Banner
        banner = new HBox(10);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(10, 14, 10, 14));
        banner.setVisible(false);
        banner.setManaged(false);

        bannerLabel = new Label();
        bannerLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        HBox.setHgrow(bannerLabel, Priority.ALWAYS);
        banner.getChildren().add(bannerLabel);

        // Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("\uD83D\uDD0D  Search student by name, ID, or department\u2026");
        searchField.getStyleClass().add("input-field");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((o, ov, nv) -> refresh());

        List<String> depts = db.getDepartments();
        String[] dArr = new String[depts.size() + 1];
        dArr[0] = "All Departments";
        for (int i = 0; i < depts.size(); i++) dArr[i + 1] = depts.get(i);
        deptCombo = FXComponents.styledCombo(dArr);
        deptCombo.setOnAction(e -> refresh());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = FXComponents.primaryButton("+ Register Student");
        addBtn.setOnAction(e -> showRegisterDialog());

        toolbar.getChildren().addAll(searchField, deptCombo, spacer, addBtn);

        // Table
        table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Student, String> idCol = new TableColumn<>("STUDENT ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStudentId()));
        idCol.setCellFactory(tc -> monoCell());
        idCol.setPrefWidth(100);

        TableColumn<Student, String> nameCol = new TableColumn<>("NAME");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        nameCol.setPrefWidth(160);

        TableColumn<Student, String> deptCol = new TableColumn<>("DEPT");
        deptCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDepartment()));
        deptCol.setPrefWidth(70);

        TableColumn<Student, String> yearCol = new TableColumn<>("YEAR");
        yearCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getYear()));
        yearCol.setPrefWidth(80);

        TableColumn<Student, String> nfcCol = new TableColumn<>("NFC");
        nfcCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNfcStatusText()));
        nfcCol.setPrefWidth(70);
        nfcCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else setGraphic(FXComponents.badge(item, "Yes".equals(item) ? "badge-green" : "badge-amber"));
            }
        });

        TableColumn<Student, String> loansCol = new TableColumn<>("LOANS");
        loansCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getActiveLoans())));
        loansCol.setPrefWidth(60);

        TableColumn<Student, String> overdueCol = new TableColumn<>("OVERDUE");
        overdueCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getOverdueCount())));
        overdueCol.setPrefWidth(70);
        overdueCol.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                int v = Integer.parseInt(item);
                setText(item);
                setTextFill(v > 0 ? Color.web(FXComponents.RED) : Color.web(FXComponents.T_PRIMARY));
                setFont(Font.font("Segoe UI", v > 0 ? FontWeight.BOLD : FontWeight.NORMAL, 13));
            }
        });

        // ACTIONS column with Edit, NFC, and Delete buttons
        TableColumn<Student, Void> actionsCol = new TableColumn<>("ACTIONS");
        actionsCol.setPrefWidth(125);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            final Button editBtn = new Button("✏️");
            final Button nfcBtn  = new Button("🪪");
            final Button delBtn  = new Button("🗑️");
            final HBox box       = new HBox(4, editBtn, nfcBtn, delBtn);
            {
                box.setAlignment(Pos.CENTER);
                editBtn.getStyleClass().add("btn-icon");
                editBtn.setTooltip(new Tooltip("Edit Student Information"));
                nfcBtn.getStyleClass().add("btn-icon");
                nfcBtn.setTooltip(new Tooltip("Link / Change NFC Card"));
                delBtn.getStyleClass().add("btn-icon");
                delBtn.setTooltip(new Tooltip("Delete Student Record"));

                editBtn.setOnAction(e -> {
                    Student s = getTableView().getItems().get(getIndex());
                    if (s != null) showEditStudentDialog(s);
                });
                nfcBtn.setOnAction(e -> {
                    Student s = getTableView().getItems().get(getIndex());
                    if (s != null) showLinkNfcDialog(s);
                });
                delBtn.setOnAction(e -> {
                    Student s = getTableView().getItems().get(getIndex());
                    if (s != null) confirmDeleteStudent(s);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().addAll(idCol, nameCol, deptCol, yearCol, nfcCol, loansCol, overdueCol, actionsCol);

        table.setRowFactory(tv -> {
            TableRow<Student> row = new TableRow<>();
            row.setOnMouseEntered(e -> { if (!row.isEmpty()) row.setStyle("-fx-background-color:#FDF2F3;"); });
            row.setOnMouseExited(e -> row.setStyle(""));
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showEditStudentDialog(row.getItem());
                }
            });

            ContextMenu cm = new ContextMenu();
            MenuItem editItem = new MenuItem("✏️  Edit Student Information…");
            editItem.setOnAction(e -> {
                if (!row.isEmpty()) showEditStudentDialog(row.getItem());
            });
            MenuItem linkItem = new MenuItem("🪪  Link / Change NFC Card…");
            linkItem.setOnAction(e -> {
                if (!row.isEmpty()) showLinkNfcDialog(row.getItem());
            });
            MenuItem delItem = new MenuItem("🗑️  Delete Student…");
            delItem.setOnAction(e -> {
                if (!row.isEmpty()) confirmDeleteStudent(row.getItem());
            });

            cm.getItems().addAll(editItem, linkItem, new SeparatorMenuItem(), delItem);
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(cm)
            );
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

    private void refresh() {
        String q = searchField == null ? "" : searchField.getText().trim();
        String dept = deptCombo == null ? "All Departments" : (String) deptCombo.getValue();
        List<Student> students = db.searchStudents(q, dept);
        table.getItems().setAll(students);
        countLabel.setText("Showing " + students.size() + " student(s)");
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
    //  EDIT STUDENT DIALOG
    // ══════════════════════════════════════════════════════════════════

    public void showEditStudentDialog(Student student) {
        if (student == null) return;

        Stage dlg = new Stage();
        if (portal != null && portal.getStage() != null) {
            dlg.initOwner(portal.getStage());
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Student — " + student.getName());
        dlg.setResizable(false);

        VBox root = new VBox(14);
        root.setPrefWidth(460);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        // Header
        Label title = new Label("✏️  Edit Student Information");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label sub = new Label("Update student details, department, year level, and NFC connection.");
        sub.setFont(Font.font("Segoe UI", 11));
        sub.setTextFill(Color.web(FXComponents.T_MUTED));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(8, 0, 8, 0));

        // Student ID (Read-only primary key)
        TextField idF = FXComponents.inputField("");
        idF.setText(student.getStudentId());
        idF.setEditable(false);
        idF.setStyle("-fx-background-color:#F1F5F9; -fx-text-fill:#64748B; -fx-border-color:#CBD5E1; -fx-border-radius:6; -fx-background-radius:6;");
        Label idTip = new Label("🔒 Primary ID cannot be modified");
        idTip.setFont(Font.font("Segoe UI", 10));
        idTip.setTextFill(Color.web(FXComponents.T_MUTED));
        VBox idBox = new VBox(2, idF, idTip);

        // Name
        TextField nameF = FXComponents.inputField("Last, First M.");
        nameF.setText(student.getName());

        // Department
        ComboBox<String> deptF = new ComboBox<>();
        deptF.setEditable(true);
        deptF.getItems().addAll("CICS", "COE", "CAS", "COED", "CABEIHM", "CIT", "CONAHS", "CTE");
        deptF.setValue(student.getDepartment());
        deptF.setMaxWidth(Double.MAX_VALUE);
        deptF.setStyle("-fx-background-color:white; -fx-border-color:#CBD5E1; -fx-border-radius:6; -fx-background-radius:6;");

        // Year Level
        ComboBox<String> yearF = new ComboBox<>();
        yearF.setEditable(true);
        yearF.getItems().addAll("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year");
        yearF.setValue(student.getYear());
        yearF.setMaxWidth(Double.MAX_VALUE);
        yearF.setStyle("-fx-background-color:white; -fx-border-color:#CBD5E1; -fx-border-radius:6; -fx-background-radius:6;");

        // NFC Card UID field with "Tap NFC" button
        TextField nfcF = FXComponents.inputField("NFC Card UID (optional)");
        if (student.getNfcUid() != null) nfcF.setText(student.getNfcUid());

        Button tapNfcBtn = new Button("🪪 Tap Card");
        tapNfcBtn.setStyle("-fx-background-color:#F1F5F9; -fx-text-fill:#3B82F6; -fx-font-weight:bold; -fx-border-color:#CBD5E1; -fx-border-radius:6; -fx-background-radius:6; -fx-cursor:hand;");
        tapNfcBtn.setOnAction(e -> {
            showLinkNfcDialog(student);
            if (student.getNfcUid() != null) nfcF.setText(student.getNfcUid());
        });
        HBox nfcRow = new HBox(8, nfcF, tapNfcBtn);
        HBox.setHgrow(nfcF, Priority.ALWAYS);

        grid.add(new Label("Student ID:"), 0, 0); grid.add(idBox, 1, 0);
        grid.add(new Label("Full Name *:"), 0, 1); grid.add(nameF, 1, 1);
        grid.add(new Label("Department *:"), 0, 2); grid.add(deptF, 1, 2);
        grid.add(new Label("Year Level *:"), 0, 3); grid.add(yearF, 1, 3);
        grid.add(new Label("NFC UID:"), 0, 4); grid.add(nfcRow, 1, 4);

        ColumnConstraints c0 = new ColumnConstraints(100);
        ColumnConstraints c1 = new ColumnConstraints(280, 300, Double.MAX_VALUE);
        c1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1);

        Label errorLbl = new Label();
        errorLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        errorLbl.setTextFill(Color.web(FXComponents.RED));
        errorLbl.setVisible(false);

        Button saveBtn = FXComponents.primaryButton("Save Changes");
        saveBtn.setPrefHeight(40);
        saveBtn.setMaxWidth(Double.MAX_VALUE);

        Button cancelBtn = FXComponents.ghostButton("Cancel");
        cancelBtn.setPrefHeight(40);
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> dlg.close());

        HBox btnRow = new HBox(10, cancelBtn, saveBtn);
        btnRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);
        HBox.setHgrow(saveBtn, Priority.ALWAYS);

        saveBtn.setOnAction(e -> {
            String newName = nameF.getText().trim();
            String newDept = deptF.getValue() != null ? deptF.getValue().trim() : "";
            String newYear = yearF.getValue() != null ? yearF.getValue().trim() : "";
            String newNfc  = nfcF.getText().trim();

            if (newName.isEmpty()) {
                errorLbl.setText("⚠ Student Name cannot be empty.");
                errorLbl.setVisible(true);
                nameF.requestFocus();
                return;
            }
            if (newDept.isEmpty()) {
                errorLbl.setText("⚠ Please select or enter a Department.");
                errorLbl.setVisible(true);
                deptF.requestFocus();
                return;
            }
            if (newYear.isEmpty()) {
                errorLbl.setText("⚠ Please select or enter a Year Level.");
                errorLbl.setVisible(true);
                yearF.requestFocus();
                return;
            }

            // Check if NFC UID is already taken by another student
            if (!newNfc.isEmpty()) {
                Student other = db.findStudentByNfc(newNfc);
                if (other != null && !other.getStudentId().equalsIgnoreCase(student.getStudentId())) {
                    errorLbl.setText("⚠ Card UID already assigned to " + other.getName() + " (" + other.getStudentId() + ")");
                    errorLbl.setVisible(true);
                    nfcF.requestFocus();
                    return;
                }
            }

            student.setName(newName);
            student.setDepartment(newDept);
            student.setYear(newYear);
            if (newNfc.isEmpty()) {
                student.setNfcUid(null);
                student.setNfcRegistered(false);
            } else {
                student.setNfcUid(newNfc);
                student.setNfcRegistered(true);
            }

            boolean ok = db.updateStudent(student);
            if (ok) {
                refresh();
                showBanner("✓ Student \"" + newName + "\" updated successfully!", "success");
                dlg.close();
            } else {
                errorLbl.setText("✕ Database error: Could not save student updates.");
                errorLbl.setVisible(true);
            }
        });

        root.getChildren().addAll(title, sub, grid, errorLbl, btnRow);

        Scene scene = new Scene(root);
        FXComponents.applyStylesheet(scene);
        dlg.setScene(scene);
        dlg.showAndWait();
    }

    private void confirmDeleteStudent(Student student) {
        if (student == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (portal != null && portal.getStage() != null) {
            alert.initOwner(portal.getStage());
        }
        alert.setTitle("Delete Student");
        alert.setHeaderText("Delete Student Record?");
        alert.setContentText("Are you sure you want to remove " + student.getName() + " (" + student.getStudentId() + ")?\nThis action cannot be undone.");

        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(deleteBtn, cancelBtn);

        alert.showAndWait().ifPresent(btn -> {
            if (btn == deleteBtn) {
                db.deleteStudent(student.getStudentId());
                refresh();
                showBanner("Student \"" + student.getName() + "\" removed.", "warn");
            }
        });
    }

    private void showRegisterDialog() {
        Dialog<Student> dlg = new Dialog<>();
        dlg.setTitle("Register Student");
        dlg.setHeaderText("Enter student details");
        if (portal != null && portal.getStage() != null) {
            dlg.initOwner(portal.getStage());
        }
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField idF   = FXComponents.inputField("e.g. 25-10001");
        TextField nameF = FXComponents.inputField("Last, First M.");
        TextField deptF = FXComponents.inputField("e.g. CICS");
        TextField yearF = FXComponents.inputField("e.g. 2nd Year");

        grid.add(new Label("Student ID / SR-Code:"), 0, 0); grid.add(idF,   1, 0);
        grid.add(new Label("Name:"),                0, 1); grid.add(nameF, 1, 1);
        grid.add(new Label("Department:"),          0, 2); grid.add(deptF, 1, 2);
        grid.add(new Label("Year Level:"),          0, 3); grid.add(yearF, 1, 3);

        dlg.getDialogPane().setContent(grid);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String sid = idF.getText().trim();
            String sname = nameF.getText().trim();
            if (sid.isEmpty() || sname.isEmpty()) {
                ev.consume();
                Alert warn = new Alert(Alert.AlertType.WARNING, "Student ID and Name cannot be empty.", ButtonType.OK);
                if (portal != null) warn.initOwner(portal.getStage());
                warn.showAndWait();
                return;
            }
            if (db.findStudentById(sid) != null) {
                ev.consume();
                Alert err = new Alert(Alert.AlertType.ERROR, "A student with ID \"" + sid + "\" is already registered.", ButtonType.OK);
                if (portal != null) err.initOwner(portal.getStage());
                err.showAndWait();
                return;
            }
        });

        dlg.setResultConverter(btn -> btn == ButtonType.OK
            ? new Student(idF.getText().trim(), nameF.getText().trim(), deptF.getText().trim(), yearF.getText().trim(), false, 0, 0)
            : null);

        Platform.runLater(idF::requestFocus);

        dlg.showAndWait().ifPresent(s -> {
            if (s != null) {
                db.addStudent(s);
                refresh();
                showBanner("✓ Student \"" + s.getName() + "\" registered successfully!", "success");

                // Ask the user if they will add NFC connection to that student
                Alert askNfc = new Alert(Alert.AlertType.CONFIRMATION);
                if (portal != null && portal.getStage() != null) {
                    askNfc.initOwner(portal.getStage());
                }
                askNfc.setTitle("Link NFC Connection");
                askNfc.setHeaderText("Student Registered Successfully!");
                askNfc.setContentText("Would you like to link an NFC card/tag for " + s.getName() + " (" + s.getStudentId() + ") now?");

                ButtonType yesBtn = new ButtonType("Yes, Link NFC", ButtonBar.ButtonData.YES);
                ButtonType noBtn  = new ButtonType("No, Skip",       ButtonBar.ButtonData.NO);
                askNfc.getButtonTypes().setAll(yesBtn, noBtn);

                Optional<ButtonType> choice = askNfc.showAndWait();
                if (choice.isPresent() && choice.get() == yesBtn) {
                    showLinkNfcDialog(s);
                }
            }
        });
    }

    /**
     * Dedicated modal dialog for tapping/entering an NFC card UID to link to a student.
     */
    public void showLinkNfcDialog(Student student) {
        Stage dlg = new Stage();
        if (portal != null && portal.getStage() != null) {
            dlg.initOwner(portal.getStage());
        }
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Link NFC Card — " + student.getName());
        dlg.setResizable(false);

        VBox root = new VBox(14);
        root.setPrefWidth(440);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        // Student identity summary card
        VBox studentCard = new VBox(3);
        studentCard.setStyle("-fx-background-color:#F8FAFD; -fx-background-radius:10; -fx-border-color:#E2E8F0; -fx-border-radius:10; -fx-padding:12 16 12 16;");
        Label nameLbl = new Label(student.getName());
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        nameLbl.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label infoLbl = new Label("ID: " + student.getStudentId() + "  ·  " + student.getDepartment() + " (" + student.getYear() + ")");
        infoLbl.setFont(Font.font("Segoe UI", 11.5));
        infoLbl.setTextFill(Color.web(FXComponents.T_MUTED));

        String curUid = student.getNfcUid();
        Label statusLbl = new Label(student.isNfcRegistered()
            ? "● Linked Card UID: " + curUid
            : "● Status: Pending (No card linked)");
        statusLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        statusLbl.setTextFill(Color.web(student.isNfcRegistered() ? FXComponents.GREEN : FXComponents.AMBER));
        studentCard.getChildren().addAll(nameLbl, infoLbl, statusLbl);

        // NFC icon & instruction banner
        HBox nfcRow = new HBox(14);
        nfcRow.setAlignment(Pos.CENTER_LEFT);
        nfcRow.setPadding(new Insets(4, 0, 4, 0));

        StackPane nfcIcon = FXComponents.nfcPulse(64);
        VBox nfcTextBox = new VBox(2);
        Label tapTitle = new Label("Tap NFC Card on Reader");
        tapTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        tapTitle.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label tapDesc = new Label("Place the card on the USB reader to auto-capture UID,\nor enter the UID number manually below.");
        tapDesc.setFont(Font.font("Segoe UI", 11));
        tapDesc.setTextFill(Color.web(FXComponents.T_MUTED));
        tapDesc.setWrapText(true);
        nfcTextBox.getChildren().addAll(tapTitle, tapDesc);
        nfcRow.getChildren().addAll(nfcIcon, nfcTextBox);

        // Input field for UID (wedge scanner types here + Enter)
        Label fieldTitle = new Label("NFC CARD UID");
        fieldTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        fieldTitle.setTextFill(Color.web(FXComponents.T_MUTED));

        TextField uidField = FXComponents.inputField("Tap card on reader or type UID...");
        uidField.setPrefHeight(42);
        if (student.getNfcUid() != null) {
            uidField.setText(student.getNfcUid());
        }

        Label feedback = new Label("Waiting for NFC card tap…");
        feedback.setFont(Font.font("Segoe UI", 11.5));
        feedback.setTextFill(Color.web("#3B82F6"));
        feedback.setWrapText(true);

        Button saveBtn = FXComponents.primaryButton("✔  Link NFC Card");
        saveBtn.setPrefHeight(40);
        saveBtn.setMaxWidth(Double.MAX_VALUE);

        Button cancelBtn = FXComponents.ghostButton("Cancel");
        cancelBtn.setPrefHeight(40);
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> dlg.close());

        HBox btnRow = new HBox(10, cancelBtn, saveBtn);
        btnRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);
        HBox.setHgrow(saveBtn, Priority.ALWAYS);

        Runnable doSave = () -> {
            String uid = uidField.getText().trim();
            if (uid.isEmpty()) {
                feedback.setText("Please tap an NFC card on the reader or type a UID.");
                feedback.setTextFill(Color.web(FXComponents.RED));
                FXComponents.flagInvalid(uidField);
                return;
            }

            // Check if UID is already assigned to another student
            Student existing = db.findStudentByNfc(uid);
            if (existing != null && !existing.getStudentId().equalsIgnoreCase(student.getStudentId())) {
                feedback.setText("⚠ This card UID is already registered to " + existing.getName() + " (" + existing.getStudentId() + ")!");
                feedback.setTextFill(Color.web(FXComponents.RED));
                FXComponents.flagInvalid(uidField);
                return;
            }

            student.setNfcUid(uid);
            db.updateStudentNfc(student);
            refresh();

            feedback.setText("✓ Card successfully linked: " + uid);
            feedback.setTextFill(Color.web(FXComponents.GREEN));
            saveBtn.setDisable(true);

            PauseTransition pause = new PauseTransition(Duration.millis(500));
            pause.setOnFinished(e -> dlg.close());
            pause.play();
        };

        saveBtn.setOnAction(e -> doSave.run());
        uidField.setOnAction(e -> doSave.run());

        root.getChildren().addAll(studentCard, nfcRow, fieldTitle, uidField, feedback, btnRow);

        Scene scene = new Scene(root);
        FXComponents.applyStylesheet(scene);
        dlg.setScene(scene);

        Platform.runLater(uidField::requestFocus);
        dlg.showAndWait();
    }

    private TableCell<Student, String> monoCell() {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); }
                else { setText(item); setFont(Font.font("Consolas", 12)); setTextFill(Color.web(FXComponents.T_MUTED)); }
            }
        };
    }
}
