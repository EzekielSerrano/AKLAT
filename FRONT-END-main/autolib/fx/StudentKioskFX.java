package autolib.fx;

import autolib.DataStore;
import autolib.fx.kiosk.*;
import autolib.models.Student;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Student Kiosk — two-state scene:
 *   1. Welcome / tap ID (dark, animated NFC pulse, hidden NFC UID wedge capture)
 *   2. Student dashboard (sidebar + content)
 */
public class StudentKioskFX {

    private final Stage stage;
    private Student currentStudent;
    private Timeline clockTimeline;
    private Label clockLabel;

    // Welcome screen controls for NFC handling
    private TextField idField;
    private Label errorLabel;
    private StackPane nfcIcon;

    // Hardware NFC wedge scanner buffer (< 60ms between characters)
    private final StringBuilder nfcBuffer = new StringBuilder();
    private long lastKeyTime = 0;
    private static final long SCANNER_INTERVAL_MS = 60;

    // Dashboard state
    private StackPane stackRoot;
    private StackPane contentArea;
    private StackPane contentPane;
    private StackPane loaderOverlay;
    private Label loaderMessageLabel;
    private Label pageTitleLabel;
    private Button activeNavBtn;
    private Button refreshBtn;
    private String currentPanelName = "Dashboard";

    // Lazy panels (view-only: borrowing & returning is handled at the librarian's desk)
    private Node dashNode, histNode, profileNode;

    public StudentKioskFX(Stage stage) {
        this.stage = stage;
        DataStore.warmUpAsync();
    }

    public Scene buildScene() {
        StackPane root = new StackPane(buildBackground(), buildWelcomeContent());
        Scene scene = new Scene(root);
        FXComponents.applyStylesheet(scene);
        setupNfcWedgeFilter(scene);
        return scene;
    }

    private void setupNfcWedgeFilter(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                if (nfcBuffer.length() >= 6) {
                    ev.consume();
                    String uid = nfcBuffer.toString().trim();
                    nfcBuffer.setLength(0);
                    processNfcTap(uid);
                }
            }
        });

        scene.addEventFilter(KeyEvent.KEY_TYPED, ev -> {
            String ch = ev.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            char c = ch.charAt(0);

            if (c == '\r' || c == '\n') {
                if (nfcBuffer.length() >= 6) {
                    ev.consume();
                    String uid = nfcBuffer.toString().trim();
                    nfcBuffer.setLength(0);
                    processNfcTap(uid);
                }
                return;
            }

            long now = System.currentTimeMillis();
            long elapsed = now - lastKeyTime;
            lastKeyTime = now;

            if (elapsed < SCANNER_INTERVAL_MS) {
                // High-speed keystrokes from USB NFC hardware wedge
                ev.consume();
                nfcBuffer.append(c);
                if (idField != null && !idField.getText().isEmpty()) {
                    idField.clear();
                }
            } else {
                // Human typing interval (or 1st character of scan)
                nfcBuffer.setLength(0);
                nfcBuffer.append(c);
            }
        });
    }

    private void processNfcTap(String uid) {
        if (idField != null) idField.clear();
        Student s = DataStore.get().findStudentByNfc(uid);
        if (s != null) {
            if (!s.isNfcRegistered()) {
                showError("Card is not active. Please contact the librarian.");
                return;
            }
            showSuccessAndLogin(s);
        } else {
            showError("Card not recognized. Please sign in with your SR-Code.");
        }
    }

    private void showSuccessAndLogin(Student s) {
        if (errorLabel != null) {
            errorLabel.setTextFill(Color.web(FXComponents.GREEN));
            errorLabel.setText("✓ Card Verified: " + s.getName() + " — Logging in...");
        }
        if (nfcIcon != null) {
            ScaleTransition st = new ScaleTransition(Duration.millis(160), nfcIcon);
            st.setFromX(1.0); st.setFromY(1.0);
            st.setToX(1.15);  st.setToY(1.15);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.play();
        }
        PauseTransition pt = new PauseTransition(Duration.millis(300));
        pt.setOnFinished(e -> loginStudent(s));
        pt.play();
    }

    private void showError(String msg) {
        if (errorLabel != null) {
            errorLabel.setTextFill(Color.web(FXComponents.RED));
            errorLabel.setText(msg);
        }
        if (idField != null) {
            FXComponents.flagInvalid(idField);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Welcome screen
    // ─────────────────────────────────────────────────────────────────────
    private Pane buildBackground() {
        Pane bg = new Pane();
        bg.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #12061A, #0D1117);");
        bg.prefWidthProperty().bind(stage.widthProperty());
        bg.prefHeightProperty().bind(stage.heightProperty());
        return bg;
    }

    private BorderPane buildWelcomeContent() {
        BorderPane bp = new BorderPane();
        bp.setStyle("-fx-background-color:transparent;");
        bp.setTop(buildWelcomeTopBar());
        bp.setCenter(buildWelcomeCard());
        bp.setBottom(buildStatusBar());
        return bp;
    }

    private HBox buildWelcomeTopBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(10, 20, 0, 20));
        bar.setAlignment(Pos.CENTER);

        boolean isKioskMode = "true".equalsIgnoreCase(System.getProperty("autolib.kiosk"));
        if (!isKioskMode) {
            Button backBtn = new Button("‹ Back to Portal");
            backBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#8C96AA; -fx-font-size:11; -fx-cursor:hand;");
            FXComponents.addPopHover(backBtn, 1.05);
            backBtn.setOnAction(e -> {
                if (clockTimeline != null) clockTimeline.stop();
                stage.setTitle("A.K.L.A.T. — Portal Selection");
                stage.setScene(new PortalScreenFX(stage).buildScene());
                stage.setWidth(1024);
                stage.setHeight(620);
                stage.centerOnScreen();
            });
            bar.getChildren().add(backBtn);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        clockLabel = new Label();
        clockLabel.getStyleClass().add("clock-chip");
        refreshClock();
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> refreshClock()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        bar.getChildren().addAll(spacer, clockLabel);
        return bar;
    }

    private StackPane buildWelcomeCard() {
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color:#161B22; -fx-background-radius:18; -fx-border-radius:18; -fx-border-color:#2C3648; -fx-border-width:1;");
        card.setPadding(new Insets(40, 44, 36, 44));
        card.setPrefWidth(420);
        card.setMaxWidth(420);

        // Logo
        HBox logoRow = new HBox(12);
        logoRow.setAlignment(Pos.CENTER_LEFT);
        StackPane logoIcon = FXComponents.logoIcon(42);
        VBox logoText = new VBox(1);
        Label appName = new Label("A.K.L.A.T.");
        appName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 21));
        appName.setTextFill(Color.WHITE);
        Label kiosk = new Label("STUDENT KIOSK");
        kiosk.setFont(Font.font("Segoe UI", 9));
        kiosk.setTextFill(Color.web("#64708A"));
        logoText.getChildren().addAll(appName, kiosk);
        logoRow.getChildren().addAll(logoIcon, logoText);

        // NFC pulse animation (centered in card)
        nfcIcon = FXComponents.nfcPulse(110);
        StackPane nfcWrap = new StackPane(nfcIcon);
        nfcWrap.setAlignment(Pos.CENTER);
        nfcWrap.setMaxWidth(Double.MAX_VALUE);
        nfcWrap.setPadding(new Insets(14, 0, 8, 0));

        Label tapTitle = new Label("Tap Your Student ID");
        tapTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        tapTitle.setTextFill(Color.WHITE);
        tapTitle.setAlignment(Pos.CENTER);
        tapTitle.setMaxWidth(Double.MAX_VALUE);

        Label tapSub = new Label("Place your NFC student ID on the reader\nor enter your student number below.");
        tapSub.setFont(Font.font("Segoe UI", 11));
        tapSub.setTextFill(Color.web("#82899E"));
        tapSub.setAlignment(Pos.CENTER);
        tapSub.setMaxWidth(Double.MAX_VALUE);
        tapSub.setPadding(new Insets(6, 0, 18, 0));

        // ID input
        idField = FXComponents.inputField("e.g. 2024-10042");
        idField.setPrefHeight(40);
        HBox.setHgrow(idField, Priority.ALWAYS);

        // Safety listener: auto-detect NFC card if typed directly by card reader hardware
        idField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) return;
            String clean = newVal.trim();

            // Only check in-memory cache if input length matches an NFC UID pattern (>= 7 chars)
            if (clean.length() >= 7) {
                Student byNfc = DataStore.get().findStudentByNfcInMemory(clean);
                if (byNfc != null) {
                    Platform.runLater(() -> {
                        idField.clear();
                        showSuccessAndLogin(byNfc);
                    });
                }
            }
        });

        // Dark-card styling on top of the shared input field (no -fx-effect: the focus glow comes from FXComponents)
        final String idBase = "-fx-background-color:#1E2632; -fx-text-fill:white; -fx-prompt-text-fill:#5A6680; -fx-border-color:#32405A; -fx-border-radius:8; -fx-background-radius:8; -fx-border-width:1; -fx-padding:8 12 8 12; -fx-font-family:'Segoe UI'; -fx-font-size:13;";
        final String idActive = "-fx-background-color:#222C3A; -fx-text-fill:white; -fx-prompt-text-fill:#7A86A0; -fx-border-color:#CC2936; -fx-border-radius:8; -fx-background-radius:8; -fx-border-width:1.5; -fx-padding:8 12 8 12; -fx-font-family:'Segoe UI'; -fx-font-size:13;";
        Runnable restyle = () -> idField.setStyle((idField.isHover() || idField.isFocused()) ? idActive : idBase);
        restyle.run();
        idField.hoverProperty().addListener((o, a, b) -> restyle.run());
        idField.focusedProperty().addListener((o, a, b) -> restyle.run());

        Button goBtn = FXComponents.primaryButton("Proceed →");
        goBtn.setPrefHeight(40);

        HBox inputRow = new HBox(8, idField, goBtn);
        inputRow.setAlignment(Pos.CENTER);

        errorLabel = new Label(" ");
        errorLabel.setFont(Font.font("Segoe UI", 11));
        errorLabel.setTextFill(Color.web(FXComponents.RED));
        errorLabel.setAlignment(Pos.CENTER);
        errorLabel.setMaxWidth(Double.MAX_VALUE);
        errorLabel.setPadding(new Insets(6, 0, 0, 0));

        Runnable doLogin = () -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) {
                showError("Please enter your student ID / SR-Code.");
                return;
            }
            // Fast in-memory check first (instant, 0ms)
            Student found = DataStore.get().findStudentByIdOrNfcInMemory(id);
            if (found == null) {
                // If not in cache, fallback to live database
                found = DataStore.get().findStudentByIdOrNfc(id);
            }
            if (found == null) {
                if (id.matches("\\d{8,}")) {
                    idField.clear();
                    showError("Card not recognized. Please sign in with your SR-Code.");
                } else {
                    showError("Student ID not found.");
                }
                return;
            }
            if (!found.isNfcRegistered()) {
                showError("NFC not registered. Contact the librarian.");
                return;
            }
            idField.clear();
            errorLabel.setText(" ");
            loginStudent(found);
        };

        goBtn.setOnAction(e -> doLogin.run());
        idField.setOnAction(e -> doLogin.run());

        card.getChildren().addAll(logoRow, nfcWrap, tapTitle, tapSub, inputRow, errorLabel);

        StackPane wrap = new StackPane(card);
        StackPane.setAlignment(card, Pos.CENTER);
        FXComponents.fadeInSlide(card);
        FXComponents.staggerIn(logoRow, nfcWrap, tapTitle, tapSub, inputRow);
        return wrap;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(22);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 0, 14, 0));
        bar.getChildren().addAll(
            FXComponents.statusDot("NFC Reader Online", true)
        );
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dashboard (after login)
    // ─────────────────────────────────────────────────────────────────────
    private void loginStudent(Student s) {
        this.currentStudent = s;
        this.dashNode = this.histNode = this.profileNode = null;
        DataStore.get().invalidateTransactions();

        HBox root = new HBox(0);
        root.setStyle("-fx-background-color:#F4F6FA;");

        VBox sidebar = buildDashSidebar();
        BorderPane mainArea = buildDashMainArea();
        HBox.setHgrow(mainArea, Priority.ALWAYS);
        root.getChildren().addAll(sidebar, mainArea);

        this.stackRoot = new StackPane(root);
        Scene scene = new Scene(this.stackRoot);
        FXComponents.applyStylesheet(scene);
        stage.setScene(scene);
        FXComponents.slideIn(sidebar, 220);
        String first = s.getName() == null ? "" : s.getName().split("[,\\s]+")[0];
        FXComponents.showToast(stackRoot, "Welcome, " + first + "!", "success");
        // Land on the account dashboard right after tap-in
        navTo(activeNavBtn, "Dashboard", getDash());
    }

    private VBox buildDashSidebar() {
        VBox sb = new VBox(0);
        sb.getStyleClass().add("sidebar");
        sb.setPrefWidth(220);
        sb.setMinWidth(220);
        sb.setMaxWidth(220);

        // Logo
        HBox logoRow = new HBox(10);
        logoRow.setPadding(new Insets(14));
        logoRow.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = FXComponents.logoIcon(32);
        VBox txt = new VBox(1);
        Label nm = new Label("A.K.L.A.T.");
        nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        nm.setTextFill(Color.WHITE);
        Label rl = new Label("STUDENT KIOSK");
        rl.setFont(Font.font("Segoe UI", 9));
        rl.setTextFill(Color.web("#5A6480"));
        txt.getChildren().addAll(nm, rl);
        logoRow.getChildren().addAll(icon, txt);
        sb.getChildren().add(logoRow);

        sb.getChildren().add(sectionLabel("OVERVIEW"));
        Button dashBtn   = navItem("⌂   Dashboard", true);
        sb.getChildren().add(dashBtn);

        sb.getChildren().add(sectionLabel("ACCOUNT"));
        Button histBtn    = navItem("⇄   My History", false);
        Button profileBtn = navItem("☺   My Profile", false);
        sb.getChildren().addAll(histBtn, profileBtn);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sb.getChildren().add(spacer);
        sb.getChildren().add(buildStudentCard());

        activeNavBtn = dashBtn;
        dashBtn.setOnAction(e    -> navTo(dashBtn,   "Dashboard",     getDash()));
        histBtn.setOnAction(e    -> navTo(histBtn,   "My History",    getHist()));
        profileBtn.setOnAction(e -> navTo(profileBtn,"My Profile",    getProfile()));

        return sb;
    }

    private HBox buildStudentCard() {
        HBox row = new HBox(10);
        row.setStyle("-fx-background-color:#0E121E; -fx-padding:12 14 12 14;");
        row.setAlignment(Pos.CENTER_LEFT);

        String init = "";
        if (currentStudent.getName() != null) {
            String[] parts = currentStudent.getName().split("[,\\s]+");
            if (parts.length >= 2) init = parts[0].substring(0,1).toUpperCase() + parts[parts.length-1].substring(0,1).toUpperCase();
            else if (parts.length == 1) init = parts[0].substring(0,1).toUpperCase();
        }
        StackPane av = FXComponents.avatar(init, FXComponents.BLUE, 32);

        VBox info = new VBox(1);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameL = new Label(currentStudent.getName());
        nameL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        nameL.setTextFill(Color.WHITE);
        Label idL = new Label(currentStudent.getStudentId());
        idL.setFont(Font.font("Segoe UI", 10));
        idL.setTextFill(Color.web("#64708C"));
        info.getChildren().addAll(nameL, idL);

        Button logout = new Button("→");
        logout.setStyle("-fx-background-color:transparent; -fx-text-fill:#64708C; -fx-font-size:16; -fx-cursor:hand;");
        FXComponents.addPopHover(logout, 1.2);
        logout.setOnAction(e -> doLogout());

        row.getChildren().addAll(av, info, logout);
        return row;
    }

    private BorderPane buildDashMainArea() {
        BorderPane main = new BorderPane();
        main.setStyle("-fx-background-color:#F4F6FA;");
        main.setTop(buildDashTopBar());

        contentPane = new StackPane();
        contentPane.setStyle("-fx-background-color:#F4F6FA;");

        loaderOverlay = buildLoaderOverlay();
        contentArea = new StackPane(contentPane, loaderOverlay);
        contentArea.setStyle("-fx-background-color:#F4F6FA;");

        main.setCenter(contentArea);
        return main;
    }

    private StackPane buildLoaderOverlay() {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(244, 246, 250, 0.78);");

        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24, 32, 24, 32));
        box.setMaxWidth(260);
        box.setMaxHeight(135);
        box.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.14), 16, 0, 0, 4); -fx-border-color: #E2E8F0; -fx-border-radius: 14; -fx-border-width: 1;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(38, 38);
        spinner.setStyle("-fx-progress-color: #CC2936;");

        loaderMessageLabel = new Label("Updating records...");
        loaderMessageLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        loaderMessageLabel.setTextFill(Color.web(FXComponents.T_PRIMARY));

        Label subText = new Label("Please wait a moment");
        subText.setFont(Font.font("Segoe UI", 11));
        subText.setTextFill(Color.web(FXComponents.T_MUTED));

        box.getChildren().addAll(spinner, loaderMessageLabel, subText);
        overlay.getChildren().add(box);
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }

    public void setLoading(boolean loading, String message) {
        if (loaderOverlay != null) {
            if (loading) {
                if (loaderMessageLabel != null && message != null) {
                    loaderMessageLabel.setText(message);
                }
                loaderOverlay.setVisible(true);
                loaderOverlay.setManaged(true);
                loaderOverlay.toFront();
                FXComponents.fadeInSlide(loaderOverlay);
            } else {
                loaderOverlay.setVisible(false);
                loaderOverlay.setManaged(false);
            }
        }
    }

    public void triggerRefresh() {
        if (refreshBtn != null) refreshBtn.setDisable(true);
        setLoading(true, "Refreshing library records...");

        Thread t = new Thread(() -> {
            try {
                // Invalidate caches and pull fresh data from DB on background thread
                DataStore.get().invalidateTransactions();
                DataStore.get().invalidateStudents();
                DataStore.get().getTransactions(true);
                DataStore.get().getStudents(true);

                Student fresh = DataStore.get().findStudentById(currentStudent.getStudentId());
                if (fresh != null) {
                    currentStudent.setActiveLoans(fresh.getActiveLoans());
                    currentStudent.setOverdueCount(fresh.getOverdueCount());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    dashNode = null;
                    histNode = null;
                    profileNode = null;

                    Node refreshedNode = switch (currentPanelName) {
                        case "My History" -> getHist();
                        case "My Profile" -> getProfile();
                        default           -> getDash();
                    };
                    showDashPanel(refreshedNode, currentPanelName);
                    setLoading(false, null);
                    if (refreshBtn != null) refreshBtn.setDisable(false);
                    if (stackRoot != null) {
                        FXComponents.showToast(stackRoot, "Library records updated!", "success");
                    }
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private HBox buildDashTopBar() {
        HBox bar = new HBox(0);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 20, 0, 20));
        bar.setPrefHeight(58);

        HBox left = new HBox(10);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);

        Button logoutBtn = FXComponents.ghostButton("⎋  Log Out");
        logoutBtn.setPrefHeight(32);
        logoutBtn.setStyle("-fx-padding: 4 14 4 10; -fx-font-size: 12px; -fx-text-fill: #CC2936; -fx-border-color: #F87171; -fx-background-color: #FFF1F2; -fx-background-radius: 8; -fx-border-radius: 8; -fx-font-weight: bold; -fx-cursor: hand;");
        FXComponents.addPopHover(logoutBtn, 1.05);
        logoutBtn.setOnAction(e -> doLogout());

        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep.setPrefHeight(22);

        pageTitleLabel = new Label("Dashboard");
        pageTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        pageTitleLabel.setTextFill(Color.web(FXComponents.T_PRIMARY));

        String firstName = currentStudent.getName().contains(",")
            ? currentStudent.getName().split(",")[0] : currentStudent.getName();
        Label welcomeLbl = new Label("Welcome, " + firstName + "!");
        welcomeLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        welcomeLbl.setTextFill(Color.web(FXComponents.T_PRIMARY));

        left.getChildren().addAll(logoutBtn, sep, pageTitleLabel);

        HBox right = new HBox(12);
        right.setAlignment(Pos.CENTER_RIGHT);

        refreshBtn = FXComponents.ghostButton("↻  Refresh");
        refreshBtn.setPrefHeight(32);
        refreshBtn.setStyle("-fx-padding: 4 12 4 10; -fx-font-size: 12px;");
        refreshBtn.setOnAction(e -> triggerRefresh());

        right.getChildren().addAll(refreshBtn, FXComponents.statusDot("NFC Reader", true), welcomeLbl);

        bar.getChildren().addAll(left, right);
        return bar;
    }

    private void navTo(Button btn, String title, Node panel) {
        if (activeNavBtn != null) activeNavBtn.getStyleClass().remove("active");
        btn.getStyleClass().add("active");
        activeNavBtn = btn;
        showDashPanel(panel, title);
    }

    private void showDashPanel(Node node, String title) {
        if (pageTitleLabel != null) { pageTitleLabel.setText(title); FXComponents.fadeInSlide(pageTitleLabel); }
        currentPanelName = title;
        contentPane.getChildren().setAll(node);
        FXComponents.fadeInSlide(node);
    }

    public void doLogout() {
        if (clockTimeline != null) clockTimeline.stop();
        stage.setTitle("A.K.L.A.T. — Student Kiosk");
        StudentKioskFX fresh = new StudentKioskFX(stage);
        Scene scene = fresh.buildScene();
        stage.setScene(scene);
        stage.setWidth(1120);
        stage.setHeight(700);
        stage.centerOnScreen();
    }

    private void goToPortal() {
        doLogout();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lazy panel helpers
    private Node getDash() {
        if (dashNode == null) {
            dashNode = new KioskDashboardFX(this, currentStudent).build();
        }
        return dashNode;
    }

    private Node getHist() {
        if (histNode == null) {
            histNode = new KioskHistoryFX(this, currentStudent).build();
        }
        return histNode;
    }

    private Node getProfile() {
        if (profileNode == null) {
            profileNode = new KioskProfileFX(this, currentStudent).build();
        }
        return profileNode;
    }

    public void refreshDash() {
        dashNode = null;
        histNode = null;
    }

    // Public nav for panel buttons
    public void navigateTo(String panelName) {
        Node panel = switch (panelName) {
            case "My History"    -> getHist();
            case "My Profile"    -> getProfile();
            default              -> getDash();
        };
        showDashPanel(panel, panelName);
    }

    private void refreshClock() {
        if (clockLabel != null)
            clockLabel.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a")));
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section-label");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private Button navItem(String text, boolean active) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-item");
        if (active) btn.getStyleClass().add("active");
        FXComponents.addNavHover(btn);
        return btn;
    }
}
