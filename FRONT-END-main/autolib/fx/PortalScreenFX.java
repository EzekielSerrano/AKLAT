package autolib.fx;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Modern Portal Selection Screen (Clean, high-tech, and minimalist).
 * Features:
 *  - Interactive ambient particle network background
 *  - Ultra-sleek glassmorphic central portal card
 *  - Butter-smooth hover lifts, glow effects, and micro-interactions
 *  - Clean typography and professional BatStateU Red branding
 *  - Cinematic opening splash (first launch only, click / any key to skip)
 *  - Cursor-reactive particles, click shockwave, parallax depth, keyboard shortcuts
 */
public class PortalScreenFX {

    private final Stage stage;
    private Label clockLabel;
    private Timeline clockTimeline;

    /** Splash only plays the first time the portal is built in this app run. */
    private static boolean introSeen = false;

    /** Mouse state shared between the scene handlers and the particle loop. */
    private static class Pointer {
        double x = -9999, y = -9999;
        boolean burst;
        double bx, by;
    }

    private final Pointer pointer = new Pointer();
    private final ParallelTransition contentEntrance = new ParallelTransition();
    private StackPane root;
    private StackPane splash;
    private StackPane cardWrap;
    private Animation introAnim;
    private boolean introStarted, introDone, skipping, contentStarted;
    private final boolean kioskOnly;

    public PortalScreenFX(Stage stage) {
        this(stage, "true".equalsIgnoreCase(System.getProperty("autolib.kiosk")));
    }

    public PortalScreenFX(Stage stage, boolean kioskOnly) {
        this.stage = stage;
        this.kioskOnly = kioskOnly;
    }

    public Scene buildScene() {
        Pane glowOrbs = buildAmbientGlow();
        Canvas particleCanvas = buildParticleCanvas(glowOrbs);
        particleCanvas.widthProperty().bind(stage.widthProperty());
        particleCanvas.heightProperty().bind(stage.heightProperty());

        root = new StackPane(buildDeepBackground(), glowOrbs, particleCanvas, buildContent());

        if (!introSeen) {
            splash = buildSplash();
            root.getChildren().add(splash);
        } else {
            introDone = true;
        }

        Scene scene = new Scene(root, 820, 570);
        FXComponents.applyStylesheet(scene);
        wireInteractions(scene, particleCanvas);
        beginWhenShown(scene);
        return scene;
    }

    /** Starts the intro once the window is actually visible so no frames are lost. */
    private void beginWhenShown(Scene scene) {
        scene.windowProperty().addListener((obs, oldW, w) -> {
            if (w == null) return;
            if (w.isShowing()) beginIntro();
            else w.showingProperty().addListener((o, was, now) -> { if (now) beginIntro(); });
        });
    }

    private void beginIntro() {
        if (introStarted) return;
        introStarted = true;
        if (splash == null) {
            startContent();
        } else {
            introSeen = true;
            introAnim.play();
        }
    }

    private void startContent() {
        if (contentStarted) return;
        contentStarted = true;
        contentEntrance.play();
    }

    private void endIntro() {
        introDone = true;
        root.getChildren().remove(splash);
    }

    private void skipIntro() {
        if (introDone || skipping || splash == null) return;
        skipping = true;
        if (introAnim != null) introAnim.stop();
        startContent();
        FadeTransition fo = new FadeTransition(Duration.millis(250), splash);
        fo.setToValue(0);
        fo.setOnFinished(e -> endIntro());
        fo.play();
    }

    // ── Deep Tech Background ───────────────────────────────────────────────
    private Pane buildDeepBackground() {
        Pane bg = new Pane();
        bg.setStyle("-fx-background-color: radial-gradient(center 50% 40%, radius 90%, #151928, #090C15 85%);");
        bg.prefWidthProperty().bind(stage.widthProperty());
        bg.prefHeightProperty().bind(stage.heightProperty());
        return bg;
    }

    // ── Subtle Ambient Glow Orbs ──────────────────────────────────────────
    private Pane buildAmbientGlow() {
        Pane pane = new Pane();
        pane.setMouseTransparent(true);

        // Crimson glow orb top-left
        Circle redGlow = new Circle(140, 100, 260);
        redGlow.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#CC2936", 0.16)),
                new Stop(1, Color.TRANSPARENT)));
        redGlow.setEffect(new DropShadow(80, Color.web("#CC2936", 0.2)));

        // Deep blue glow orb bottom-right
        Circle blueGlow = new Circle(700, 480, 280);
        blueGlow.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#3B82F6", 0.12)),
                new Stop(1, Color.TRANSPARENT)));

        // Gentle breathing animation on glows
        Timeline pulse = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(redGlow.scaleXProperty(), 1.0),
                new KeyValue(redGlow.scaleYProperty(), 1.0),
                new KeyValue(blueGlow.scaleXProperty(), 1.0),
                new KeyValue(blueGlow.scaleYProperty(), 1.0)
            ),
            new KeyFrame(Duration.seconds(4),
                new KeyValue(redGlow.scaleXProperty(), 1.15),
                new KeyValue(redGlow.scaleYProperty(), 1.15),
                new KeyValue(blueGlow.scaleXProperty(), 1.12),
                new KeyValue(blueGlow.scaleYProperty(), 1.12)
            )
        );
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        FXComponents.animateWhileShowing(pane, pulse);

        pane.getChildren().addAll(redGlow, blueGlow);
        return pane;
    }

    // ── BatStateU Seal Logo ────────────────────────────────────────────────
    private static final String LOGO_FILE = "BatStateU-NEU-Logo.png";
    private static Image cachedLogo;
    private static boolean logoTried;

    /** Loads the seal from the embedded BatStateULogo, classpath, or working directory; null if not found. */
    private static Image loadLogo() {
        if (logoTried) return cachedLogo;
        logoTried = true;

        // 1. Try embedded BatStateULogo (Base64 compiled into bytecode - always works anywhere)
        try {
            cachedLogo = BatStateULogo.get();
            if (cachedLogo != null && !cachedLogo.isError()) {
                return cachedLogo;
            }
        } catch (Throwable ignored) { }

        // 2. Classpath search
        String[] resourcePaths = {
            "/" + LOGO_FILE, "/images/" + LOGO_FILE, "/img/" + LOGO_FILE,
            "/assets/" + LOGO_FILE, "/resources/" + LOGO_FILE, "/autolib/fx/" + LOGO_FILE
        };
        for (String path : resourcePaths) {
            try (InputStream in = PortalScreenFX.class.getResourceAsStream(path)) {
                if (in != null) {
                    cachedLogo = new Image(in, 360, 0, true, true);
                    return cachedLogo;
                }
            } catch (Exception ignored) { }
        }

        // 3. Filesystem search
        String[] filePaths = {
            LOGO_FILE,
            "autolib/fx/" + LOGO_FILE,
            "AKLAT/autolib/fx/" + LOGO_FILE,
            "FRONT-END-main/autolib/fx/" + LOGO_FILE,
            "images/" + LOGO_FILE,
            "assets/" + LOGO_FILE,
            "resources/" + LOGO_FILE,
            "src/main/resources/" + LOGO_FILE,
            "src/main/resources/images/" + LOGO_FILE
        };
        for (String path : filePaths) {
            File f = new File(path);
            if (f.isFile()) {
                cachedLogo = new Image(f.toURI().toString(), 360, 0, true, true);
                return cachedLogo;
            }
        }
        return null;
    }

    /** The university seal on a white disc (so it reads on the dark UI). Falls back to the old icon. */
    private StackPane buildBrandLogo(double size) {
        Image img = loadLogo();
        if (img == null || img.isError()) return FXComponents.logoIcon((int) size);

        Circle disc = new Circle(size / 2);
        disc.setFill(Color.WHITE);
        disc.setEffect(new DropShadow(size * 0.28, Color.web("#CC2936", 0.45)));

        ImageView iv = new ImageView(img);
        iv.setFitWidth(size * 0.9);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);

        StackPane box = new StackPane(disc, iv);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }

    // ── Opening Splash Animation ───────────────────────────────────────────
    private StackPane buildSplash() {
        StackPane sp = new StackPane();
        sp.setStyle("-fx-background-color: radial-gradient(center 50% 45%, radius 80%, #151928, #070A12 90%);");
        sp.setOnMouseClicked(e -> skipIntro());

        List<Animation> parts = new ArrayList<>();

        // Overshoot ("pop") easing
        Interpolator pop = new Interpolator() {
            @Override protected double curve(double t) {
                double c1 = 1.70158, c3 = c1 + 1;
                return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
            }
        };

        // Logo stack: sonar rings + drawn ring + logo
        StackPane logoStack = new StackPane();
        logoStack.setMinSize(130, 130);
        logoStack.setPrefSize(130, 130);
        logoStack.setMaxSize(130, 130);

        for (int i = 0; i < 3; i++) {
            Circle sonar = new Circle(48);
            sonar.setFill(Color.TRANSPARENT);
            sonar.setStroke(Color.web("#CC2936", 0.55));
            sonar.setStrokeWidth(1.5);
            sonar.setOpacity(0);
            sonar.setMouseTransparent(true);
            logoStack.getChildren().add(sonar);

            ScaleTransition sc = new ScaleTransition(Duration.millis(1400), sonar);
            sc.setFromX(1); sc.setFromY(1); sc.setToX(4.2); sc.setToY(4.2);
            sc.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition fd = new FadeTransition(Duration.millis(1400), sonar);
            fd.setFromValue(0.7); fd.setToValue(0);
            ParallelTransition pulse = new ParallelTransition(sc, fd);
            pulse.setDelay(Duration.millis(500 + i * 300));
            parts.add(pulse);
        }

        double circ = 2 * Math.PI * 54;
        Circle drawRing = new Circle(54);
        drawRing.setFill(Color.TRANSPARENT);
        drawRing.setStroke(Color.web("#CC2936"));
        drawRing.setStrokeWidth(2.5);
        drawRing.setStrokeLineCap(StrokeLineCap.ROUND);
        drawRing.getStrokeDashArray().setAll(circ, circ);
        drawRing.setStrokeDashOffset(circ);
        drawRing.setRotate(-90);
        drawRing.setEffect(new DropShadow(14, Color.web("#CC2936", 0.8)));
        parts.add(new Timeline(new KeyFrame(Duration.millis(1000),
                new KeyValue(drawRing.strokeDashOffsetProperty(), 0, Interpolator.EASE_BOTH))));

        StackPane logo = buildBrandLogo(92);
        logo.setOpacity(0);
        logo.setScaleX(0.3);
        logo.setScaleY(0.3);
        ScaleTransition logoScale = new ScaleTransition(Duration.millis(700), logo);
        logoScale.setToX(1); logoScale.setToY(1);
        logoScale.setInterpolator(pop);
        logoScale.setDelay(Duration.millis(150));
        FadeTransition logoFade = new FadeTransition(Duration.millis(400), logo);
        logoFade.setToValue(1);
        logoFade.setDelay(Duration.millis(150));
        parts.add(logoScale);
        parts.add(logoFade);

        logoStack.getChildren().addAll(drawRing, logo);

        // App name — letters rise in one by one
        HBox nameRow = new HBox(2);
        nameRow.setAlignment(Pos.CENTER);
        String name = "A.K.L.A.T.";
        for (int i = 0; i < name.length(); i++) {
            Label l = new Label(String.valueOf(name.charAt(i)));
            l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
            l.setTextFill(name.charAt(i) == '.' ? Color.web("#CC2936") : Color.WHITE);
            l.setOpacity(0);
            l.setTranslateY(18);
            nameRow.getChildren().add(l);

            FadeTransition lf = new FadeTransition(Duration.millis(350), l);
            lf.setToValue(1);
            TranslateTransition lt = new TranslateTransition(Duration.millis(350), l);
            lt.setToY(0);
            lt.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition lp = new ParallelTransition(lf, lt);
            lp.setDelay(Duration.millis(650 + i * 70));
            parts.add(lp);
        }

        Label uni = new Label("BATANGAS STATE UNIVERSITY");
        uni.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        uni.setTextFill(Color.web("#8A96B2"));
        uni.setOpacity(0);
        FadeTransition uniFade = new FadeTransition(Duration.millis(500), uni);
        uniFade.setToValue(1);
        uniFade.setDelay(Duration.millis(1100));
        parts.add(uniFade);

        Label fullTitle = new Label("Automated Key Library Analytics & Tracking");
        fullTitle.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        fullTitle.setTextFill(Color.web("#CC2936"));
        fullTitle.setOpacity(0);
        FadeTransition ftFade = new FadeTransition(Duration.millis(500), fullTitle);
        ftFade.setToValue(1);
        ftFade.setDelay(Duration.millis(1300));
        parts.add(ftFade);

        // Progress bar + boot messages
        Rectangle track = new Rectangle(240, 3);
        track.setArcWidth(3); track.setArcHeight(3);
        track.setFill(Color.web("#FFFFFF", 0.1));
        Rectangle bar = new Rectangle(0, 3);
        bar.setArcWidth(3); bar.setArcHeight(3);
        bar.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#CC2936")), new Stop(1, Color.web("#3B82F6"))));
        StackPane prog = new StackPane(track, bar);
        StackPane.setAlignment(bar, Pos.CENTER_LEFT);
        prog.setMaxSize(240, 3);
        prog.setOpacity(0);

        Label status = new Label("Starting up…");
        status.setFont(Font.font("Segoe UI", 11));
        status.setTextFill(Color.web("#7582A0"));
        status.setOpacity(0);

        FadeTransition progFade = new FadeTransition(Duration.millis(300), prog);
        progFade.setToValue(1);
        progFade.setDelay(Duration.millis(800));
        FadeTransition statusFade = new FadeTransition(Duration.millis(300), status);
        statusFade.setToValue(1);
        statusFade.setDelay(Duration.millis(800));
        parts.add(progFade);
        parts.add(statusFade);

        Timeline boot = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(bar.widthProperty(), 0)),
            new KeyFrame(Duration.millis(300),  e -> status.setText("Initializing NFC reader…")),
            new KeyFrame(Duration.millis(650),  e -> status.setText("Connecting barcode scanner…")),
            new KeyFrame(Duration.millis(1000), e -> status.setText("Loading catalog…")),
            new KeyFrame(Duration.millis(1350), e -> status.setText("Ready")),
            new KeyFrame(Duration.millis(1500),
                new KeyValue(bar.widthProperty(), 240, Interpolator.EASE_BOTH))
        );
        boot.setDelay(Duration.millis(800));
        parts.add(boot);

        Region spacer = new Region();
        spacer.setMinHeight(6);
        VBox center = new VBox(12, logoStack, nameRow, uni, spacer, prog, status);
        center.setAlignment(Pos.CENTER);
        VBox.setMargin(prog, new Insets(22, 0, 0, 0));

        Label skip = new Label("Click or press any key to skip");
        skip.setFont(Font.font("Segoe UI", 10));
        skip.setTextFill(Color.web("#55607A"));
        StackPane.setAlignment(skip, Pos.BOTTOM_CENTER);
        StackPane.setMargin(skip, new Insets(0, 0, 18, 0));

        sp.getChildren().addAll(center, skip);

        // Timeline: build-up → content starts entering → splash zooms out and fades
        ParallelTransition intro = new ParallelTransition();
        intro.getChildren().addAll(parts);
        intro.setOnFinished(e -> startContent());

        FadeTransition exitFade = new FadeTransition(Duration.millis(550), sp);
        exitFade.setToValue(0);
        ScaleTransition exitZoom = new ScaleTransition(Duration.millis(550), center);
        exitZoom.setToX(1.12); exitZoom.setToY(1.12);
        exitZoom.setInterpolator(Interpolator.EASE_IN);
        ParallelTransition exit = new ParallelTransition(exitFade, exitZoom);
        exit.setDelay(Duration.millis(200));
        exit.setOnFinished(e -> endIntro());

        introAnim = new SequentialTransition(intro, exit);
        return sp;
    }

    // ── Mouse / Keyboard Interaction ───────────────────────────────────────
    private void wireInteractions(Scene scene, Canvas canvas) {
        EventHandler<MouseEvent> track = e -> {
            Point2D p = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            pointer.x = p.getX();
            pointer.y = p.getY();
        };
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, track);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, track);

        // Click anywhere → shockwave through the particle field
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!introDone) return;
            Point2D p = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            pointer.bx = p.getX();
            pointer.by = p.getY();
            pointer.burst = true;
        });

        scene.setOnMouseExited(e -> { pointer.x = -9999; pointer.y = -9999; });

        scene.setOnKeyPressed(e -> {
            if (!introDone) { skipIntro(); return; }
            KeyCode k = e.getCode();
            if (kioskOnly) {
                if (k == KeyCode.ENTER || k == KeyCode.SPACE || k == KeyCode.DIGIT2 || k == KeyCode.NUMPAD2 || k == KeyCode.S) {
                    openStudentKiosk();
                }
            } else {
                if (k == KeyCode.DIGIT1 || k == KeyCode.NUMPAD1 || k == KeyCode.L) openLibrarianLogin();
                else if (k == KeyCode.DIGIT2 || k == KeyCode.NUMPAD2 || k == KeyCode.S) openStudentKiosk();
            }
        });
    }

    // ── Floating Constellation Particles (cursor-reactive) ─────────────────
    private Canvas buildParticleCanvas(Pane glowOrbs) {
        Canvas canvas = new Canvas(820, 570);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Random rnd = new Random(101);

        int n = 56;
        double[] px = new double[n], py = new double[n];
        double[] vx = new double[n], vy = new double[n];
        double[] bvx = new double[n], bvy = new double[n]; // resting drift
        double[] sz = new double[n];

        for (int i = 0; i < n; i++) {
            px[i] = rnd.nextDouble() * 820;
            py[i] = rnd.nextDouble() * 570;
            bvx[i] = vx[i] = (rnd.nextDouble() - 0.5) * 0.45;
            bvy[i] = vy[i] = (rnd.nextDouble() - 0.5) * 0.45;
            sz[i] = 1.2 + rnd.nextDouble() * 2.2;
        }

        // Smoothed parallax offsets: glow x/y, card x/y
        double[] par = new double[4];
        final double REACH = 150, LINK = 170;

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double w = canvas.getWidth(), h = canvas.getHeight();
                if (w <= 0 || h <= 0) return;
                gc.clearRect(0, 0, w, h);

                boolean hasMouse = pointer.x > -1000;

                // Parallax: background drifts against the cursor, card leans toward it
                double nx = hasMouse ? (pointer.x / w - 0.5) * 2 : 0;
                double ny = hasMouse ? (pointer.y / h - 0.5) * 2 : 0;
                par[0] += (-nx * 28 - par[0]) * 0.06;
                par[1] += (-ny * 20 - par[1]) * 0.06;
                par[2] += (nx * 6 - par[2]) * 0.06;
                par[3] += (ny * 4 - par[3]) * 0.06;
                glowOrbs.setTranslateX(par[0]);
                glowOrbs.setTranslateY(par[1]);
                if (cardWrap != null) {
                    cardWrap.setTranslateX(par[2]);
                    cardWrap.setTranslateY(par[3]);
                }

                // Soft halo under the cursor
                if (hasMouse) {
                    gc.setFill(new RadialGradient(0, 0, pointer.x, pointer.y, 90, false,
                            CycleMethod.NO_CYCLE,
                            new Stop(0, Color.web("#3B82F6", 0.10)),
                            new Stop(1, Color.TRANSPARENT)));
                    gc.fillOval(pointer.x - 90, pointer.y - 90, 180, 180);
                }

                for (int i = 0; i < n; i++) {
                    // Cursor repel
                    double mdx = px[i] - pointer.x, mdy = py[i] - pointer.y;
                    double md = hasMouse ? Math.hypot(mdx, mdy) : Double.MAX_VALUE;
                    double glow = 0;
                    if (md < REACH && md > 0.01) {
                        glow = 1 - md / REACH;
                        vx[i] += mdx / md * glow * 0.5;
                        vy[i] += mdy / md * glow * 0.5;
                    }

                    // Click shockwave
                    if (pointer.burst) {
                        double bdx = px[i] - pointer.bx, bdy = py[i] - pointer.by;
                        double bd = Math.hypot(bdx, bdy);
                        if (bd < 280 && bd > 0.01) {
                            double f = (1 - bd / 280) * 8;
                            vx[i] += bdx / bd * f;
                            vy[i] += bdy / bd * f;
                        }
                    }

                    // Ease back toward resting drift
                    vx[i] += (bvx[i] - vx[i]) * 0.03;
                    vy[i] += (bvy[i] - vy[i]) * 0.03;
                    px[i] += vx[i];
                    py[i] += vy[i];

                    // Wrap around edges
                    if (px[i] < -10) px[i] = w + 10; else if (px[i] > w + 10) px[i] = -10;
                    if (py[i] < -10) py[i] = h + 10; else if (py[i] > h + 10) py[i] = -10;

                    // Particle node (brightens + swells near cursor)
                    double r = sz[i] * (1 + 0.6 * glow);
                    gc.setFill(Color.web("#CC2936", Math.min(1.0, 0.35 + 0.5 * glow)));
                    gc.fillOval(px[i] - r, py[i] - r, r * 2, r * 2);

                    // Link to cursor
                    if (md < LINK) {
                        gc.setStroke(Color.web("#3B82F6", (1 - md / LINK) * 0.35));
                        gc.setLineWidth(0.9);
                        gc.strokeLine(px[i], py[i], pointer.x, pointer.y);
                    }

                    // Connecting lines to nearby nodes
                    for (int j = i + 1; j < n; j++) {
                        double dx = px[i] - px[j], dy = py[i] - py[j];
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist < 110) {
                            double alpha = (1.0 - dist / 110.0) * 0.12;
                            gc.setStroke(Color.web("#CC2936", alpha));
                            gc.setLineWidth(0.75);
                            gc.strokeLine(px[i], py[i], px[j], py[j]);
                        }
                    }
                }
                pointer.burst = false;
            }
        };
        timer.start();
        return canvas;
    }

    // ── Main UI Layout ─────────────────────────────────────────────────────
    private BorderPane buildContent() {
        BorderPane bp = new BorderPane();
        bp.setStyle("-fx-background-color: transparent;");
        bp.setTop(buildTopBar());
        bp.setCenter(buildCenterCard());
        bp.setBottom(buildStatusBar());
        return bp;
    }

    // ── Sleek Top Bar ──────────────────────────────────────────────────────
    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(20, 28, 0, 28));

        clockLabel = new Label();
        clockLabel.getStyleClass().add("clock-chip");
        refreshClock();

        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshClock()));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();

        // Clock chip hover animation
        FXComponents.addPopHover(clockLabel, 1.05);
        clockLabel.setOnMouseEntered(e -> {
            clockLabel.setStyle("-fx-background-color: rgba(255,255,255,0.14); -fx-border-color: rgba(255,255,255,0.28);");
        });
        clockLabel.setOnMouseExited(e -> {
            clockLabel.setStyle("");
        });

        bar.getChildren().add(clockLabel);

        bar.setOpacity(0);
        FadeTransition barFade = new FadeTransition(Duration.millis(500), bar);
        barFade.setToValue(1);
        contentEntrance.getChildren().add(barFade);
        return bar;
    }

    private void refreshClock() {
        clockLabel.setText(LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("EEE, MMM d   •   h:mm a")));
    }

    // ── Glassmorphism Center Card ──────────────────────────────────────────
    private StackPane buildCenterCard() {
        VBox card = new VBox(0);
        card.getStyleClass().add("card-dark");
        card.setPrefWidth(420);
        card.setMaxWidth(420);
        card.setPadding(new Insets(36, 36, 28, 36));

        // ── Brand Header ───────────────────────────────────────────────────
        HBox logoRow = new HBox(16);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        StackPane logoIcon = buildBrandLogo(58);

        // Logo subtle interactive hover reaction
        FXComponents.addPopHover(logoIcon, 1.08);

        VBox logoText = new VBox(3);
        Label appName = new Label("A.K.L.A.T.");
        appName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        appName.setTextFill(Color.WHITE);

        Label univName = new Label("BATANGAS STATE UNIVERSITY");
        univName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9.5));
        univName.setTextFill(Color.web("#CC2936"));
        univName.setStyle("-fx-letter-spacing: 1.5;");

        logoText.getChildren().addAll(appName, univName);
        logoRow.getChildren().addAll(logoIcon, logoText);

        // Divider
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.08);");
        VBox.setMargin(divider, new Insets(20, 0, 18, 0));

        // Subtitle
        Label sub = new Label("Automated Key Library Analytics & Tracking");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#8A96B2"));
        sub.setPadding(new Insets(0, 0, 20, 0));

        HBox stuBtn = FXComponents.portalButton(
            "Student / Borrower Kiosk",
            "NFC tap or Student ID to view your account & loans",
            "◉",
            "#3B82F6"
        );
        fillWidth(stuBtn);
        stuBtn.setOnMouseClicked(e -> openStudentKiosk());

        HBox libBtn = null;
        if (kioskOnly) {
            Label scanPrompt = new Label("Please tap your NFC ID on the reader or select below to scan");
            scanPrompt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            scanPrompt.setTextFill(Color.web("#94A3B8"));
            scanPrompt.setPadding(new Insets(0, 0, 16, 0));
            scanPrompt.setWrapText(true);
            scanPrompt.setTextAlignment(TextAlignment.CENTER);
            scanPrompt.setMaxWidth(Double.MAX_VALUE);
            scanPrompt.setAlignment(Pos.CENTER);

            Label hint = new Label("Place your student card on the scanner to sign in");
            hint.setFont(Font.font("Segoe UI", 10.5));
            hint.setTextFill(Color.web("#55607A"));
            hint.setPadding(new Insets(14, 0, 0, 0));
            hint.setMaxWidth(Double.MAX_VALUE);
            hint.setAlignment(Pos.CENTER);

            card.getChildren().addAll(logoRow, divider, scanPrompt, stuBtn, hint);
        } else {
            libBtn = FXComponents.portalButton(
                "Librarian / Staff Portal",
                "Access catalog, circulation reports & settings",
                "◈",
                "#CC2936"
            );
            fillWidth(libBtn);
            libBtn.setOnMouseClicked(e -> openLibrarianLogin());

            Region gap = new Region();
            gap.setMinHeight(12);

            Label foot = new Label("Staff login requires verified administrative credentials.");
            foot.setFont(Font.font("Segoe UI", 10.5));
            foot.setTextFill(Color.web("#55607A"));
            foot.setPadding(new Insets(18, 0, 0, 0));
            foot.setMaxWidth(Double.MAX_VALUE);
            foot.setAlignment(Pos.CENTER);

            Label hint = new Label("Tip: press 1 for Staff  •  2 for Kiosk");
            hint.setFont(Font.font("Segoe UI", 10));
            hint.setTextFill(Color.web("#454F68"));
            hint.setPadding(new Insets(6, 0, 0, 0));
            hint.setMaxWidth(Double.MAX_VALUE);
            hint.setAlignment(Pos.CENTER);

            card.getChildren().addAll(logoRow, divider, sub, libBtn, gap, stuBtn, foot, hint);
        }

        // Entrance (played after the splash): card fades/lifts in, then buttons slide in one by one
        StackPane wrap = new StackPane(card);
        StackPane.setAlignment(card, Pos.CENTER);
        cardWrap = wrap;

        card.setOpacity(0);
        card.setTranslateY(24);
        card.setScaleX(0.96);
        card.setScaleY(0.96);

        FadeTransition ft = new FadeTransition(Duration.millis(450), card);
        ft.setToValue(1);

        TranslateTransition tt = new TranslateTransition(Duration.millis(450), card);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition st = new ScaleTransition(Duration.millis(450), card);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition entrance = new ParallelTransition(ft, tt, st);
        entrance.setDelay(Duration.millis(120));
        contentEntrance.getChildren().add(entrance);

        HBox[] portalBtns = libBtn != null ? new HBox[]{ libBtn, stuBtn } : new HBox[]{ stuBtn };
        for (int i = 0; i < portalBtns.length; i++) {
            HBox b = portalBtns[i];
            b.setOpacity(0);
            b.setTranslateX(-28);
            FadeTransition bFade = new FadeTransition(Duration.millis(380), b);
            bFade.setToValue(1);
            TranslateTransition bSlide = new TranslateTransition(Duration.millis(380), b);
            bSlide.setToX(0);
            bSlide.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition bIn = new ParallelTransition(bFade, bSlide);
            bIn.setDelay(Duration.millis(350 + i * 130));
            contentEntrance.getChildren().add(bIn);
        }

        return wrap;
    }

    // ── Portal button sizing (animations come from FXComponents.portalButton) ──
    private static void fillWidth(HBox btn) {
        btn.setPrefWidth(Region.USE_COMPUTED_SIZE);
        btn.setMaxWidth(Double.MAX_VALUE);
    }

    // ── Status Bar ─────────────────────────────────────────────────────────
    private HBox buildStatusBar() {
        HBox bar = new HBox(26);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(12, 0, 20, 0));
        bar.getChildren().addAll(
            FXComponents.statusDot("NFC Reader Active", true),
            FXComponents.statusDot("Barcode Scanner Ready", true),
            FXComponents.statusDot("Printer Offline", false)
        );

        bar.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(500), bar);
        ft.setToValue(1);
        ft.setDelay(Duration.millis(400));
        contentEntrance.getChildren().add(ft);

        return bar;
    }

    // ── Navigation Transitions ─────────────────────────────────────────────
    private void openLibrarianLogin() {
        clockTimeline.stop();
        LoginDialogFX dialog = new LoginDialogFX(stage);
        dialog.show(authenticated -> {
            if (authenticated) {
                try {
                    LibrarianPortalFX lp = new LibrarianPortalFX(stage);
                    Scene next = lp.buildScene();
                    stage.setTitle("A.K.L.A.T. — Librarian Portal");
                    stage.setScene(next);
                    stage.setWidth(1120);
                    stage.setHeight(700);
                    stage.centerOnScreen();
                    FXComponents.fadeInSlide(next.getRoot());
                } catch (Throwable t) {
                    System.err.println("[PortalScreenFX] Error opening Librarian Portal: " + t.getMessage());
                    t.printStackTrace();
                    clockTimeline.play();
                }
            } else {
                clockTimeline.play();
            }
        });
    }

    private void openStudentKiosk() {
        clockTimeline.stop();
        StudentKioskFX kiosk = new StudentKioskFX(stage);
        stage.setTitle("A.K.L.A.T. — Student Kiosk");
        Scene next = kiosk.buildScene();
        stage.setScene(next);
        stage.setWidth(1120);
        stage.setHeight(700);
        stage.centerOnScreen();
        FXComponents.fadeInSlide(next.getRoot());
    }
}