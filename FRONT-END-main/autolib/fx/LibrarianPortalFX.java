package autolib.fx;

import autolib.DataStore;
import autolib.fx.panels.*;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Librarian Portal — modern sidebar + animated content area.
 */
public class LibrarianPortalFX {

    private final Stage stage;
    private StackPane contentArea;
    private Label pageTitleLabel;
    private Label dateLabel;
    private Timeline clockTimeline;
    private Button activeNavBtn;

    // Lazy panels
    private Node dashboardNode, catalogNode, studentsNode,
                 transactionsNode, reportsNode, settingsNode;

    public LibrarianPortalFX(Stage stage) {
        this.stage = stage;
    }

    public Scene buildScene() {
        HBox root = new HBox(0);
        root.setStyle("-fx-background-color: #F4F6FA;");

        VBox sidebar = buildSidebar();
        BorderPane mainArea = buildMainArea();
        HBox.setHgrow(mainArea, Priority.ALWAYS);

        root.getChildren().addAll(sidebar, mainArea);

        Scene scene = new Scene(root);
        FXComponents.applyStylesheet(scene);

        FXComponents.slideIn(sidebar, 220);
        return scene;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sidebar
    // ─────────────────────────────────────────────────────────────────────
    private VBox buildSidebar() {
        VBox sb = new VBox(0);
        sb.getStyleClass().add("sidebar");
        sb.setPrefWidth(220);
        sb.setMinWidth(220);
        sb.setMaxWidth(220);

        sb.getChildren().add(buildSidebarLogo());
        sb.getChildren().add(sectionLabel("MAIN"));

        Button dashBtn = navItem("⌂  Dashboard", true);
        Button bookBtn = navItem("⊞  Book Catalog", false);
        Button stuBtn  = navItem("☺  Students", false);
        sb.getChildren().addAll(dashBtn, bookBtn, stuBtn);

        sb.getChildren().add(sectionLabel("REPORTS"));
        Button txBtn  = navItem("⇄  Transactions", false);
        Button repBtn = navItem("≡  Reports", false);
        sb.getChildren().addAll(txBtn, repBtn);

        sb.getChildren().add(sectionLabel("SYSTEM"));
        Button kioskBtn    = navItem("☉  Student Kiosk ↗", false);
        Button settingsBtn = navItem("⚙  Settings", false);
        sb.getChildren().addAll(kioskBtn, settingsBtn);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sb.getChildren().add(spacer);
        sb.getChildren().add(buildSidebarUserCard());

        activeNavBtn = dashBtn;
        dashBtn.setOnAction(e    -> navigate(dashBtn, "Dashboard"));
        bookBtn.setOnAction(e    -> navigate(bookBtn, "Book Catalog"));
        stuBtn.setOnAction(e     -> navigate(stuBtn, "Students"));
        txBtn.setOnAction(e      -> navigate(txBtn, "Transactions"));
        repBtn.setOnAction(e     -> navigate(repBtn, "Reports"));
        settingsBtn.setOnAction(e -> navigate(settingsBtn, "Settings"));
        kioskBtn.setOnAction(e   -> openKiosk());

        return sb;
    }

    private HBox buildSidebarLogo() {
        HBox row = new HBox(12);
        row.setPadding(new Insets(18, 16, 18, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-border-color: transparent transparent rgba(255,255,255,0.07) transparent; -fx-border-width: 0 0 1 0;");

        StackPane icon = FXComponents.logoIcon(34);
        VBox txt = new VBox(2);
        Label nm = new Label("A.K.L.A.T.");
        nm.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        nm.setTextFill(Color.WHITE);
        Label rl = new Label("LIBRARIAN PORTAL");
        rl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        rl.setStyle("-fx-text-fill: #CC2936; -fx-letter-spacing: 0.8;");
        txt.getChildren().addAll(nm, rl);
        row.getChildren().addAll(icon, txt);
        return row;
    }

    private HBox buildSidebarUserCard() {
        HBox row = new HBox(10);
        row.setStyle(
            "-fx-background-color: rgba(0,0,0,0.25);" +
            "-fx-border-color: rgba(255,255,255,0.07) transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;" +
            "-fx-padding: 14 16 14 16;"
        );
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        StackPane av = FXComponents.avatar("LR", FXComponents.RED, 34);

        VBox info = new VBox(1);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameL = new Label("Librarian Reyes");
        nameL.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        nameL.setTextFill(Color.WHITE);
        Label roleL = new Label("Head Librarian");
        roleL.setFont(Font.font("Segoe UI", 10));
        roleL.setTextFill(Color.web("#5A6480"));
        info.getChildren().addAll(nameL, roleL);

        Button logout = new Button("⏻");
        logout.setStyle(
            "-fx-background-color: rgba(204,41,54,0.15);" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #CC2936;" +
            "-fx-font-size: 13;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 7 4 7;"
        );
        logout.setOnMouseEntered(e -> logout.setStyle(
            "-fx-background-color: rgba(204,41,54,0.3);" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #FF5566;" +
            "-fx-font-size: 13;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 7 4 7;"
        ));
        logout.setOnMouseExited(e -> logout.setStyle(
            "-fx-background-color: rgba(204,41,54,0.15);" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #CC2936;" +
            "-fx-font-size: 13;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 7 4 7;"
        ));
        FXComponents.addPopHover(logout, 1.12);
        logout.setOnAction(e -> goPortal());

        row.getChildren().addAll(av, info, logout);
        return row;
    }

    private void goPortal() {
        if (clockTimeline != null) clockTimeline.stop();
        stage.setTitle("A.K.L.A.T. — Portal Selection");
        PortalScreenFX portal = new PortalScreenFX(stage);
        Scene next = portal.buildScene();
        stage.setScene(next);
        stage.setWidth(760);
        stage.setHeight(540);
        stage.centerOnScreen();
        FXComponents.fadeInSlide(next.getRoot());
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

    // ─────────────────────────────────────────────────────────────────────
    // Main area
    // ─────────────────────────────────────────────────────────────────────
    private BorderPane buildMainArea() {
        BorderPane main = new BorderPane();
        main.setStyle("-fx-background-color: #F4F6FA;");
        main.setTop(buildTopBar());

        contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: #F4F6FA;");
        main.setCenter(contentArea);

        showContent(getDashboard(), "Dashboard");
        return main;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(0);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 20, 0, 24));
        bar.setPrefHeight(60);

        // Left section
        HBox left = new HBox(12);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);

        // Back button with hover animation
        Button backBtn = new Button("‹ Portal");
        backBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #8C96B4;" +
            "-fx-font-size: 11.5;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10 4 10;"
        );
        backBtn.setOnMouseEntered(e -> backBtn.setStyle(
            "-fx-background-color: #EEF0F8;" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #3B4568;" +
            "-fx-font-size: 11.5;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10 4 10;"
        ));
        backBtn.setOnMouseExited(e -> backBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #8C96B4;" +
            "-fx-font-size: 11.5;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10 4 10;"
        ));
        FXComponents.addPopHover(backBtn, 1.05);
        backBtn.setOnAction(e -> goPortal());

        // Vertical divider
        Region vDivider = new Region();
        vDivider.setPrefSize(1, 24);
        vDivider.setStyle("-fx-background-color: #E0E8F0;");

        pageTitleLabel = new Label("Dashboard");
        pageTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 19));
        pageTitleLabel.setTextFill(Color.web("#141926"));

        dateLabel = new Label();
        dateLabel.setFont(Font.font("Segoe UI", 11));
        dateLabel.setTextFill(Color.web("#9BA8C0"));
        refreshDate();

        clockTimeline = new Timeline(new KeyFrame(Duration.minutes(1), e -> refreshDate()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        left.getChildren().addAll(backBtn, vDivider, pageTitleLabel, dateLabel);

        // Right section
        HBox right = new HBox(16);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.getChildren().addAll(
            FXComponents.statusDot("NFC Ready", true),
            FXComponents.statusDot("Scanner Ready", true)
        );

        bar.getChildren().addAll(left, right);
        return bar;
    }

    private void refreshDate() {
        if (dateLabel != null)
            dateLabel.setText("  ·  " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────
    private void navigate(Button btn, String panelName) {
        if (activeNavBtn != null) {
            activeNavBtn.getStyleClass().remove("active");
        }
        btn.getStyleClass().add("active");
        activeNavBtn = btn;

        Node panel = switch (panelName) {
            case "Book Catalog"  -> getCatalog();
            case "Students"      -> getStudents();
            case "Transactions"  -> getTransactions();
            case "Reports"       -> getReports();
            case "Settings"      -> getSettings();
            default              -> getDashboard();
        };
        showContent(panel, panelName);
    }

    private void showContent(Node node, String title) {
        pageTitleLabel.setText(title);
        FXComponents.fadeInSlide(pageTitleLabel);

        contentArea.getChildren().setAll(node);
        FXComponents.fadeInSlide(node);
    }

    private void openKiosk() {
        if (clockTimeline != null) clockTimeline.stop();
        stage.setTitle("A.K.L.A.T. — Student Kiosk");
        StudentKioskFX kiosk = new StudentKioskFX(stage);
        stage.setScene(kiosk.buildScene());
        stage.centerOnScreen();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lazy panel getters
    // ─────────────────────────────────────────────────────────────────────
    private Node getDashboard() {
        if (dashboardNode == null) dashboardNode = new DashboardFX(this).build();
        return dashboardNode;
    }
    private Node getCatalog() {
        if (catalogNode == null) catalogNode = new BookCatalogFX(this).build();
        return catalogNode;
    }
    private Node getStudents() {
        if (studentsNode == null) studentsNode = new StudentsFX(this).build();
        return studentsNode;
    }
    private Node getTransactions() {
        if (transactionsNode == null) transactionsNode = new TransactionsFX(this).build();
        return transactionsNode;
    }
    private Node getReports() {
        if (reportsNode == null) reportsNode = new ReportsFX(this).build();
        return reportsNode;
    }
    private Node getSettings() {
        if (settingsNode == null) settingsNode = new SettingsFX(this).build();
        return settingsNode;
    }

    public void refreshCatalog()  { catalogNode = null; }
    public void refreshStudents() { studentsNode = null; }
    public Stage getStage() { return stage; }
}