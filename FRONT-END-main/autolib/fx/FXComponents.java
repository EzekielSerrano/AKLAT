package autolib.fx;

import autolib.DataStore;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.WritableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared animated JavaFX component factory for AutoLib.
 *
 * All animations live here. Callers just call factory methods
 * and wire event handlers.
 *
 * Every public method from the previous version keeps its signature, so
 * existing screens keep compiling. New helpers (all static):
 *
 *   fadeInSlide(node, delayMs)    entrance with a delay
 *   staggerIn(nodes...)           cascading entrance for a group of nodes
 *   shake(node)                   "no!" wiggle for wrong input
 *   flagInvalid(control)          shake + temporary red border
 *   showToast(root, msg, type)    slide-in notification (success/error/warning/info)
 *   pulseOnce(node)               quick attention bump
 *   popIn(node)                   springy appear
 *   addPopHover(node, scale)      grow slightly on hover
 *   addNavHover(region)           sidebar item nudge on hover
 *   animateWhileShowing(n, anim)  run an endless animation only while on screen
 */
public final class FXComponents {

    private FXComponents() {}

    // ─────────────────────────────────────────────────────────────────────
    // Colours (hex constants matching UIConstants palette)
    // ─────────────────────────────────────────────────────────────────────
    public static final String RED       = "#CC2936";
    public static final String RED_DARK  = "#AA1C28";
    public static final String GREEN     = "#22C55E";
    public static final String ORANGE    = "#EA580C";
    public static final String BLUE      = "#3B82F6";
    public static final String AMBER     = "#CA8A04";
    public static final String SIDEBAR   = "#12163A";
    public static final String CONTENT   = "#F4F6FA";
    public static final String CARD      = "#FFFFFF";
    public static final String DARK_CARD = "#161B22";
    public static final String T_PRIMARY = "#141926";
    public static final String T_SECOND  = "#5A6480";
    public static final String T_MUTED   = "#949EB2";
    public static final String T_WHITE   = "#FFFFFF";
    public static final String BORDER    = "#E4E8F0";

    // ─────────────────────────────────────────────────────────────────────
    // Animation plumbing (private helpers)
    // ─────────────────────────────────────────────────────────────────────

    /** Smooth "fast start, soft landing" curve used by almost everything. */
    private static final Interpolator EASE = Interpolator.SPLINE(0.22, 0.61, 0.36, 1.0);

    // Keys used to remember the running animation on a node so a new one
    // can cancel it (prevents hover animations from fighting each other).
    private static final String K_HOVER  = "fx.anim.hover";
    private static final String K_SHADOW = "fx.shadow.base";
    private static final String K_FOCUS  = "fx.anim.focus";
    private static final String K_SHAKE  = "fx.anim.shake";
    private static final String K_POP    = "fx.anim.pop";
    private static final String K_ENTER  = "fx.anim.enter";
    private static final String K_NAV    = "fx.anim.nav";
    private static final String K_TOASTS = "fx.toast.stack";

    private static KeyValue d(DoubleProperty p, double v) {
        return new KeyValue(p, v, EASE);
    }

    private static KeyValue d(DoubleProperty p, double v, Interpolator i) {
        return new KeyValue(p, v, i);
    }

    private static <T> KeyValue o(WritableValue<T> p, T v) {
        return new KeyValue(p, v, EASE);
    }

    /** Stops whatever animation is stored under key on the node, then plays a new one. */
    private static Timeline playOn(Node n, String key, int ms, KeyValue... kvs) {
        Object prev = n.getProperties().get(key);
        if (prev instanceof Animation) ((Animation) prev).stop();
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(ms), kvs));
        n.getProperties().put(key, tl);
        tl.play();
        return tl;
    }

    private static Color withAlpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Remembers a node's drop-shadow so hover animations can grow it and shrink it back. */
    private static final class ShadowBase {
        final DropShadow ds;
        final double radius, offsetY;
        final Color color, hoverColor;
        ShadowBase(DropShadow ds, double radius, double offsetY, Color color, Color hoverColor) {
            this.ds = ds; this.radius = radius; this.offsetY = offsetY;
            this.color = color; this.hoverColor = hoverColor;
        }
    }

    private static ShadowBase shadowFor(Node n, Color glow) {
        Object cached = n.getProperties().get(K_SHADOW);
        if (cached != null) return (ShadowBase) cached;

        ShadowBase sb;
        if (n.getEffect() == null) {
            // No effect yet: create a fully transparent shadow we can fade in on hover.
            DropShadow ds = new DropShadow(0, 0, 0, withAlpha(glow, 0));
            n.setEffect(ds);
            sb = new ShadowBase(ds, 0, 0, withAlpha(glow, 0), glow);
        } else if (n.getEffect() instanceof DropShadow) {
            // CSS (or code) already gave it a shadow: animate that one.
            DropShadow ds = (DropShadow) n.getEffect();
            Color c = ds.getColor();
            Color stronger = withAlpha(c, Math.min(1.0, c.getOpacity() * 1.5 + 0.05));
            sb = new ShadowBase(ds, ds.getRadius(), ds.getOffsetY(), c, stronger);
        } else {
            sb = new ShadowBase(null, 0, 0, glow, glow); // some other effect, leave it alone
        }
        n.getProperties().put(K_SHADOW, sb);
        return sb;
    }

    /** One animated hover/press state change: lift, scale and shadow, all in one smooth tween. */
    private static void hover(Node n, boolean in, double lift, double scale, Color glow) {
        ShadowBase sb = shadowFor(n, glow);
        List<KeyValue> kvs = new ArrayList<>();
        kvs.add(d(n.translateYProperty(), in ? -lift : 0));
        kvs.add(d(n.scaleXProperty(), in ? scale : 1.0));
        kvs.add(d(n.scaleYProperty(), in ? scale : 1.0));
        if (sb.ds != null) {
            kvs.add(d(sb.ds.radiusProperty(), in ? sb.radius + 14 : sb.radius));
            kvs.add(d(sb.ds.offsetYProperty(), in ? sb.offsetY + 5 : sb.offsetY));
            kvs.add(o(sb.ds.colorProperty(), in ? sb.hoverColor : sb.color));
        }
        playOn(n, K_HOVER, 190, kvs.toArray(new KeyValue[0]));
    }

    /** Wires hover lift + glow and (optionally) a press-down squish onto any node. */
    private static void attachInteractive(Node n, double lift, double hoverScale,
                                          double pressScale, Color glow) {
        n.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> hover(n, true, lift, hoverScale, glow));
        n.addEventHandler(MouseEvent.MOUSE_EXITED,  e -> hover(n, false, lift, hoverScale, glow));
        if (pressScale > 0) {
            n.addEventHandler(MouseEvent.MOUSE_PRESSED,
                e -> hover(n, true, 0, pressScale, glow));
            n.addEventHandler(MouseEvent.MOUSE_RELEASED,
                e -> hover(n, n.isHover(), lift, hoverScale, glow));
        }
    }

    /**
     * Plays an (endless) animation only while the node is part of a visible scene,
     * and pauses it when the node is removed. Stops pulses from burning CPU
     * on screens nobody is looking at.
     */
    public static void animateWhileShowing(Node node, Animation anim) {
        node.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) anim.play(); else anim.pause();
        });
        if (node.getScene() != null) anim.play();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Stylesheet Helper (resolves reliably from any working directory)
    // ─────────────────────────────────────────────────────────────────────
    public static void applyStylesheet(javafx.scene.Scene scene) {
        if (scene == null) return;
        java.io.File[] candidates = {
            new java.io.File("resources/autolib.css"),
            new java.io.File("FRONT-END-main/resources/autolib.css")
        };
        for (java.io.File f : candidates) {
            if (f.exists()) {
                scene.getStylesheets().add(f.toURI().toString());
                return;
            }
        }
        var res = FXComponents.class.getResource("/resources/autolib.css");
        if (res != null) {
            scene.getStylesheets().add(res.toExternalForm());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Logo icon (University Seal or fallback red rounded square)
    //   • image version: slow breathing red glow
    //   • both versions: grow slightly on hover
    // ─────────────────────────────────────────────────────────────────────
    public static StackPane logoIcon(double size) {
        try {
            Image img = BatStateULogo.get();
            if (img != null && !img.isError()) {
                Circle disc = new Circle(size / 2);
                disc.setFill(Color.WHITE);
                DropShadow glow = new DropShadow(size * 0.22, Color.web(RED, 0.4));
                disc.setEffect(glow);

                ImageView iv = new ImageView(img);
                iv.setFitWidth(size * 0.9);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);

                StackPane box = new StackPane(disc, iv);
                box.setMinSize(size, size);
                box.setMaxSize(size, size);

                Timeline breathe = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        d(glow.radiusProperty(), size * 0.16, Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.millis(1900),
                        d(glow.radiusProperty(), size * 0.34, Interpolator.EASE_BOTH)));
                breathe.setAutoReverse(true);
                breathe.setCycleCount(Animation.INDEFINITE);
                animateWhileShowing(box, breathe);

                addPopHover(box, 1.08);
                return box;
            }
        } catch (Throwable ignored) { }

        StackPane pane = new StackPane();
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);

        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.24);
        bg.setArcHeight(size * 0.24);
        bg.setFill(Color.web(RED_DARK));

        double lw = size * 0.80;
        double cx = size / 2;
        double gap = size * 0.18;
        Line l1 = line(cx - lw / 2, cx - gap, cx + lw / 2, cx - gap);
        Line l2 = line(cx - lw / 2, cx,       cx + lw / 2, cx);
        Line l3 = line(cx - lw / 2, cx + gap, cx + lw * 0.27, cx + gap);
        for (Line l : new Line[]{l1, l2, l3}) {
            l.setStroke(Color.WHITE);
            l.setStrokeWidth(size * 0.09);
            l.setStrokeLineCap(StrokeLineCap.ROUND);
        }

        pane.getChildren().addAll(bg, l1, l2, l3);
        addPopHover(pane, 1.08);
        return pane;
    }

    private static Line line(double x1, double y1, double x2, double y2) {
        return new Line(x1, y1, x2, y2);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Animated Stat Card
    //   icon badge wiggles on hover, card lifts + glows, number counts up
    //   and gives a little "pop" when it lands.
    // ─────────────────────────────────────────────────────────────────────
    public static VBox statCard(String emoji, int targetNumber, String label, String subText, String subColor) {
        return statCard(emoji, targetNumber, label, subText, subColor, 0);
    }

    /**
     * Same as above, but with an index so a row of cards can cascade in:
     * pass 0, 1, 2, 3 ... in the order you add them to the row.
     */
    public static VBox statCard(String emoji, int targetNumber, String label, String subText,
                                String subColor, int index) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(220);

        // Top row: icon badge + sub-badge
        HBox topRow = new HBox(0);
        topRow.setAlignment(Pos.TOP_LEFT);
        topRow.setPadding(new Insets(0, 0, 8, 0));

        // Icon circle
        StackPane iconBadge = new StackPane();
        iconBadge.setMinSize(42, 42);
        iconBadge.setMaxSize(42, 42);
        iconBadge.setStyle(
            "-fx-background-color: " + subColor + "18;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + subColor + "30;" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        );
        Label iconLbl = new Label(emoji);
        iconLbl.setFont(Font.font("Segoe UI Emoji", 20));
        iconBadge.getChildren().add(iconLbl);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label subLbl = new Label(subText);
        subLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        subLbl.setTextFill(Color.web(subColor));
        subLbl.setStyle("-fx-background-color: " + subColor + "18; -fx-background-radius:20; -fx-padding:3 10 3 10;");

        topRow.getChildren().addAll(iconBadge, topSpacer, subLbl);

        // Number (count-up)
        Label numLbl = new Label("0");
        numLbl.getStyleClass().add("stat-number");
        numLbl.setPadding(new Insets(2, 0, 0, 0));

        Label nameLbl = new Label(label);
        nameLbl.setFont(Font.font("Segoe UI", 12));
        nameLbl.setTextFill(Color.web(T_SECOND));

        card.getChildren().addAll(topRow, numLbl, nameLbl);

        // Staggered entrance, then the number counts up once the card is visible
        double delay = Math.max(0, index) * 90.0;
        fadeInSlide(card, delay);
        countUp(numLbl, targetNumber, 1200, delay + 150);

        // Interactions
        addHoverLift(card);
        card.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> playOn(iconBadge, K_HOVER, 220,
            d(iconBadge.rotateProperty(), -8),
            d(iconBadge.scaleXProperty(), 1.12),
            d(iconBadge.scaleYProperty(), 1.12)));
        card.addEventHandler(MouseEvent.MOUSE_EXITED, e -> playOn(iconBadge, K_HOVER, 220,
            d(iconBadge.rotateProperty(), 0),
            d(iconBadge.scaleXProperty(), 1.0),
            d(iconBadge.scaleYProperty(), 1.0)));

        return card;
    }

    /** Animates a Label from 0 up to target over durationMs milliseconds. */
    public static void countUp(Label label, int target, int durationMs) {
        countUp(label, target, durationMs, 0);
    }

    /** Count-up with a start delay; ends with a tiny scale "pop" so the final number lands. */
    public static void countUp(Label label, int target, int durationMs, double delayMs) {
        DoubleProperty value = new SimpleDoubleProperty(0);
        value.addListener((obs, was, now) ->
            label.setText(String.valueOf((int) Math.round(now.doubleValue()))));

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(value, 0)),
            new KeyFrame(Duration.millis(durationMs), new KeyValue(value, target, EASE)));
        tl.setDelay(Duration.millis(delayMs));
        tl.setOnFinished(e -> {
            label.setText(String.valueOf(target));
            pulseOnce(label);
        });
        tl.play();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Modern Hover Lift (Smooth Y-elevation + subtle scale + shadow bloom)
    // ─────────────────────────────────────────────────────────────────────
    public static void addHoverLift(Region node) {
        attachInteractive(node, 4.0, 1.01, -1, Color.web(SIDEBAR, 0.18));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entrance animations
    // ─────────────────────────────────────────────────────────────────────

    /** Fade-slide transition between panels. */
    public static void fadeInSlide(Node node) {
        fadeInSlide(node, 0);
    }

    /** Fade-slide with a start delay (used for cascading entrances). */
    public static void fadeInSlide(Node node, double delayMs) {
        node.setOpacity(0);
        node.setTranslateY(18);
        Timeline tl = playOn(node, K_ENTER, 360,
            d(node.opacityProperty(), 1),
            d(node.translateYProperty(), 0));
        if (delayMs > 0) {
            tl.stop();
            tl.setDelay(Duration.millis(delayMs));
            tl.play();
        }
    }

    /** Cascading entrance: each node starts 70ms after the previous one. */
    public static void staggerIn(Node... nodes) {
        staggerIn(70, nodes);
    }

    public static void staggerIn(double stepMs, Node... nodes) {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) fadeInSlide(nodes[i], i * stepMs);
        }
    }

    /** Springy appear: grows slightly past full size, then settles. */
    public static void popIn(Node node) {
        node.setOpacity(0);
        node.setScaleX(0.6);
        node.setScaleY(0.6);
        Object prev = node.getProperties().get(K_POP);
        if (prev instanceof Animation) ((Animation) prev).stop();
        Timeline tl = new Timeline(
            new KeyFrame(Duration.millis(150),
                d(node.opacityProperty(), 1),
                d(node.scaleXProperty(), 1.12),
                d(node.scaleYProperty(), 1.12)),
            new KeyFrame(Duration.millis(260),
                d(node.scaleXProperty(), 1.0),
                d(node.scaleYProperty(), 1.0)));
        node.getProperties().put(K_POP, tl);
        tl.play();
    }

    /** Quick attention bump (e.g. after a value changes or a save succeeds). */
    public static void pulseOnce(Node node) {
        Timeline tl = new Timeline(
            new KeyFrame(Duration.millis(110),
                d(node.scaleXProperty(), 1.1, Interpolator.EASE_OUT),
                d(node.scaleYProperty(), 1.1, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(260),
                d(node.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                d(node.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)));
        tl.play();
    }

    /** Grow slightly while the mouse is over the node. */
    public static void addPopHover(Node node, double scale) {
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> playOn(node, K_POP, 150,
            d(node.scaleXProperty(), scale), d(node.scaleYProperty(), scale)));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> playOn(node, K_POP, 150,
            d(node.scaleXProperty(), 1.0), d(node.scaleYProperty(), 1.0)));
    }

    /** Sidebar/nav items: nudge a few pixels to the right on hover. */
    public static void addNavHover(Region item) {
        item.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> playOn(item, K_NAV, 150,
            d(item.translateXProperty(), 4)));
        item.addEventHandler(MouseEvent.MOUSE_EXITED, e -> playOn(item, K_NAV, 150,
            d(item.translateXProperty(), 0)));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Portal button (dark rounded panel)
    //   hover: card lifts with a glow, icon grows and warms up,
    //          arrow slides right.   press: squishes.
    // ─────────────────────────────────────────────────────────────────────
    public static HBox portalButton(String title, String sub, String icon) {
        return portalButton(title, sub, icon, RED);
    }

    public static HBox portalButton(String title, String sub, String icon, String colorHex) {
        String accentHex = (colorHex != null && !colorHex.isBlank()) ? colorHex : RED;
        Color accent = Color.web(accentHex);

        HBox box = new HBox(14);
        box.getStyleClass().add("portal-btn");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(290);
        box.setMaxWidth(290);
        box.setCursor(javafx.scene.Cursor.HAND);

        // Icon circle
        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(38, 38);
        iconCircle.setMaxSize(38, 38);
        Circle c = new Circle(19, Color.web("#28334A"));
        Label iconLbl = new Label(icon);
        iconLbl.setFont(Font.font("Segoe UI Emoji", 15));
        iconLbl.setTextFill(accent);
        iconCircle.getChildren().addAll(c, iconLbl);

        // Text
        VBox txt = new VBox(2);
        HBox.setHgrow(txt, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        titleLbl.setTextFill(Color.WHITE);
        Label subLbl = new Label(sub);
        subLbl.setFont(Font.font("Segoe UI", 10));
        subLbl.setTextFill(Color.web("#7380A0"));
        txt.getChildren().addAll(titleLbl, subLbl);

        // Arrow
        Label arrow = new Label("›");
        arrow.setFont(Font.font("Segoe UI", 22));
        arrow.setTextFill(Color.web("#5A6680"));

        box.getChildren().addAll(iconCircle, txt, arrow);

        // Whole-button hover/press (lift + colored glow + squish)
        attachInteractive(box, 2.0, 1.015, 0.975, Color.web(accentHex, 0.35));

        // Inner parts react too
        box.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            playOn(arrow, K_HOVER, 200,
                d(arrow.translateXProperty(), 6),
                o(arrow.textFillProperty(), accent));
            playOn(iconCircle, K_HOVER, 200,
                d(iconCircle.scaleXProperty(), 1.14),
                d(iconCircle.scaleYProperty(), 1.14));
            playOn(c, K_HOVER, 200, o(c.fillProperty(), Color.web(accentHex, 0.25)));
        });
        box.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            playOn(arrow, K_HOVER, 200,
                d(arrow.translateXProperty(), 0),
                o(arrow.textFillProperty(), Color.web("#5A6680")));
            playOn(iconCircle, K_HOVER, 200,
                d(iconCircle.scaleXProperty(), 1.0),
                d(iconCircle.scaleYProperty(), 1.0));
            playOn(c, K_HOVER, 200, o(c.fillProperty(), Color.web("#28334A")));
        });
        return box;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Status dot row  (online = expanding "sonar" ring)
    // ─────────────────────────────────────────────────────────────────────
    public static HBox statusDot(String text, boolean online) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 4, 0, 4));

        StackPane dotStack = new StackPane();
        dotStack.setMinSize(10, 10);
        dotStack.setMaxSize(10, 10);

        Circle outerRing = new Circle(5, Color.web(online ? GREEN : RED, online ? 0.45 : 0.25));
        Circle dot = new Circle(3.5, Color.web(online ? GREEN : RED));
        dotStack.getChildren().addAll(outerRing, dot);

        if (online) {
            Timeline sonar = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(outerRing.scaleXProperty(), 0.7),
                    new KeyValue(outerRing.scaleYProperty(), 0.7),
                    new KeyValue(outerRing.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(1500),
                    d(outerRing.scaleXProperty(), 2.1),
                    d(outerRing.scaleYProperty(), 2.1),
                    d(outerRing.opacityProperty(), 0.0)));
            sonar.setCycleCount(Animation.INDEFINITE);
            animateWhileShowing(dotStack, sonar);
        }

        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", 10.5));
        lbl.setTextFill(Color.web(online ? T_SECOND : "#9B6060"));

        row.getChildren().addAll(dotStack, lbl);
        return row;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Status Badge label
    // ─────────────────────────────────────────────────────────────────────
    public static Label badge(String text, String cssClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", cssClass);
        return l;
    }

    public static Label bookStatusBadge(String status) {
        return switch (status) {
            case "All Out"  -> badge("All Out",   "badge-amber");
            case "Borrowed" -> badge("Borrowed",  "badge-red");
            default         -> badge("Available", "badge-green");
        };
    }

    /** "Overdue" badges breathe gently so they catch the eye. */
    public static Label txBadge(String type) {
        return switch (type) {
            case "Borrowed" -> badge("Borrowed", "badge-red");
            case "Overdue"  -> {
                Label l = badge("Overdue", "badge-orange");
                Timeline breathe = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        d(l.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.millis(900),
                        d(l.opacityProperty(), 0.55, Interpolator.EASE_BOTH)));
                breathe.setAutoReverse(true);
                breathe.setCycleCount(Animation.INDEFINITE);
                animateWhileShowing(l, breathe);
                yield l;
            }
            default         -> badge("Returned", "badge-green");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Animated buttons (lift + glow on hover, squish on press)
    // ─────────────────────────────────────────────────────────────────────
    public static Button primaryButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-primary");
        addPressEffect(btn, Color.web(RED, 0.45));
        return btn;
    }

    public static Button ghostButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-ghost");
        addPressEffect(btn, Color.web(SIDEBAR, 0.18));
        return btn;
    }

    public static void addPressEffect(Button btn) {
        addPressEffect(btn, Color.web(SIDEBAR, 0.22));
    }

    private static void addPressEffect(Button btn, Color glow) {
        attachInteractive(btn, 1.8, 1.0, 0.95, glow);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input fields (soft red glow while focused)
    // ─────────────────────────────────────────────────────────────────────
    public static TextField inputField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.getStyleClass().add("input-field");
        addFocusGlow(f);
        return f;
    }

    public static PasswordField passwordField(String prompt) {
        PasswordField f = new PasswordField();
        f.setPromptText(prompt);
        f.getStyleClass().add("password-field");
        addFocusGlow(f);
        return f;
    }

    public static ComboBox<String> styledCombo(String[] items) {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll(items);
        cb.getSelectionModel().selectFirst();
        cb.getStyleClass().add("combo-box");
        addFocusGlow(cb);
        return cb;
    }

    private static void addFocusGlow(Control f) {
        DropShadow glow = new DropShadow(0, 0, 0, Color.web(RED, 0));
        f.setEffect(glow);
        f.focusedProperty().addListener((obs, was, focused) -> playOn(f, K_FOCUS, 200,
            d(glow.radiusProperty(), focused ? 12 : 0),
            o(glow.colorProperty(), Color.web(RED, focused ? 0.30 : 0.0))));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Feedback: shake, invalid flag, toast notifications
    // ─────────────────────────────────────────────────────────────────────

    /** Horizontal "nope" wiggle. Great for wrong passwords or failed scans. */
    public static void shake(Node node) {
        Object prev = node.getProperties().get(K_SHAKE);
        if (prev instanceof Animation) ((Animation) prev).stop();

        double[] xs = {-10, 9, -7, 5, -3, 0};
        Timeline tl = new Timeline();
        for (int i = 0; i < xs.length; i++) {
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(60 * (i + 1)),
                new KeyValue(node.translateXProperty(), xs[i], Interpolator.EASE_BOTH)));
        }
        node.getProperties().put(K_SHAKE, tl);
        tl.play();
    }

    /** Shake + red border for ~1.6 seconds. */
    public static void flagInvalid(Control c) {
        shake(c);
        String prev = c.getStyle() == null ? "" : c.getStyle();
        String bad = "-fx-border-color: " + RED + "; -fx-border-width: 1.5;";
        String flagged = prev.isEmpty() ? bad : prev + ";" + bad;
        c.setStyle(flagged);

        PauseTransition undo = new PauseTransition(Duration.millis(1600));
        undo.setOnFinished(e -> {
            if (flagged.equals(c.getStyle())) c.setStyle(prev);
        });
        undo.play();
    }

    /**
     * Slide-in notification in the top-right corner of a StackPane root.
     * type: "success", "error", "warning" or anything else for info.
     * Click it to dismiss early; otherwise it leaves on its own after ~3s.
     * Multiple toasts stack neatly.
     */
    public static void showToast(StackPane root, String message, String type) {
        String t = type == null ? "info" : type.toLowerCase();
        String color;
        String glyph;
        switch (t) {
            case "success": color = GREEN; glyph = "✓"; break;
            case "error":   color = RED;   glyph = "✕"; break;
            case "warning": color = AMBER; glyph = "!"; break;
            default:        color = BLUE;  glyph = "i"; break;
        }

        VBox stack = (VBox) root.getProperties().get(K_TOASTS);
        if (stack == null) {
            stack = new VBox(10);
            stack.setAlignment(Pos.TOP_RIGHT);
            stack.setPadding(new Insets(20));
            stack.setPickOnBounds(false);
            stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            StackPane.setAlignment(stack, Pos.TOP_RIGHT);
            root.getProperties().put(K_TOASTS, stack);
        }
        if (!root.getChildren().contains(stack)) root.getChildren().add(stack);
        stack.toFront();

        Circle bubble = new Circle(11, Color.web(color));
        Label glyphLbl = new Label(glyph);
        glyphLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        glyphLbl.setTextFill(Color.WHITE);
        StackPane iconBubble = new StackPane(bubble, glyphLbl);

        Label msg = new Label(message);
        msg.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12.5));
        msg.setTextFill(Color.web(T_PRIMARY));
        msg.setWrapText(true);
        msg.setMaxWidth(280);

        HBox toast = new HBox(12, iconBubble, msg);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(12, 18, 12, 14));
        toast.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        toast.setCursor(javafx.scene.Cursor.HAND);
        toast.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + color + "55;" +
            "-fx-border-radius: 12;");
        toast.setEffect(new DropShadow(20, 0, 6, Color.web("#000000", 0.18)));
        toast.setOpacity(0);
        toast.setTranslateX(40);

        final VBox host = stack;
        stack.getChildren().add(toast);

        Timeline in = new Timeline(new KeyFrame(Duration.millis(300),
            d(toast.opacityProperty(), 1), d(toast.translateXProperty(), 0)));
        PauseTransition hold = new PauseTransition(Duration.millis(3000));
        Timeline out = new Timeline(new KeyFrame(Duration.millis(240),
            d(toast.opacityProperty(), 0), d(toast.translateXProperty(), 30)));
        out.setOnFinished(e -> host.getChildren().remove(toast));

        SequentialTransition life = new SequentialTransition(in, hold, out);
        toast.setOnMouseClicked(e -> {
            life.stop();
            out.playFromStart();
        });
        life.play();
    }

    // ─────────────────────────────────────────────────────────────────────
    // NFC Pulse (kiosk welcome animation)
    //   waves ripple outward one after another, the card floats up and down
    //   and its outline glows in and out.
    // ─────────────────────────────────────────────────────────────────────
    public static StackPane nfcPulse(double size) {
        StackPane pane = new StackPane();
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);

        // Card shape
        Rectangle card = new Rectangle(size * 0.55, size * 0.38);
        card.setArcWidth(size * 0.08);
        card.setArcHeight(size * 0.08);
        card.setFill(Color.TRANSPARENT);
        card.setStroke(Color.web(RED));
        card.setStrokeWidth(2);

        DropShadow glow = new DropShadow(4, Color.web(RED, 0.2));
        card.setEffect(glow);

        Label nfcText = new Label("NFC");
        nfcText.setFont(Font.font("Segoe UI", FontWeight.BOLD, (int) (size * 0.11)));
        nfcText.setTextFill(Color.web(RED));

        StackPane cardStack = new StackPane(card, nfcText);
        cardStack.setTranslateX(-size * 0.08);

        // 3 rings, lit up in sequence like a signal travelling outward
        Timeline waves = new Timeline();
        for (int i = 1; i <= 3; i++) {
            Arc arc = new Arc(0, 0, size * 0.12 * i, size * 0.12 * i, 25, 130);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(Color.web(RED));
            arc.setStrokeWidth(1.8);
            arc.setStrokeLineCap(StrokeLineCap.ROUND);
            arc.setTranslateX(size * 0.08);
            arc.setOpacity(0);

            double start = (i - 1) * 230;
            waves.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                new KeyValue(arc.opacityProperty(), 0.0)));
            if (start > 0) {
                waves.getKeyFrames().add(new KeyFrame(Duration.millis(start),
                    new KeyValue(arc.opacityProperty(), 0.0)));
            }
            waves.getKeyFrames().add(new KeyFrame(Duration.millis(start + 300),
                d(arc.opacityProperty(), 0.95 - i * 0.15)));
            waves.getKeyFrames().add(new KeyFrame(Duration.millis(start + 850),
                d(arc.opacityProperty(), 0.0)));
            pane.getChildren().add(arc);
        }
        waves.getKeyFrames().add(new KeyFrame(Duration.millis(2000))); // resting gap
        waves.setCycleCount(Animation.INDEFINITE);

        // Card glow breathing
        Timeline breathe = new Timeline(
            new KeyFrame(Duration.ZERO,
                d(glow.radiusProperty(), 4, Interpolator.EASE_BOTH),
                o(glow.colorProperty(), Color.web(RED, 0.15))),
            new KeyFrame(Duration.millis(1400),
                d(glow.radiusProperty(), 20, Interpolator.EASE_BOTH),
                o(glow.colorProperty(), Color.web(RED, 0.55))));
        breathe.setAutoReverse(true);
        breathe.setCycleCount(Animation.INDEFINITE);

        // Gentle float
        Timeline bob = new Timeline(
            new KeyFrame(Duration.ZERO,
                d(cardStack.translateYProperty(), 3, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1600),
                d(cardStack.translateYProperty(), -3, Interpolator.EASE_BOTH)));
        bob.setAutoReverse(true);
        bob.setCycleCount(Animation.INDEFINITE);

        pane.getChildren().add(cardStack);

        animateWhileShowing(pane, waves);
        animateWhileShowing(pane, breathe);
        animateWhileShowing(pane, bob);
        return pane;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Card header row (title left, widget right)
    // ─────────────────────────────────────────────────────────────────────
    public static HBox cardHeader(String title, Node right) {
        HBox hdr = new HBox();
        hdr.setAlignment(Pos.CENTER_LEFT);

        Label t = new Label(title);
        t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        t.setTextFill(Color.web(T_PRIMARY));
        HBox.setHgrow(t, Priority.ALWAYS);
        hdr.getChildren().add(t);
        if (right != null) hdr.getChildren().add(right);
        return hdr;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Avatar circle (grows a little on hover)
    // ─────────────────────────────────────────────────────────────────────
    public static StackPane avatar(String initials, String hexColor, double size) {
        Circle c = new Circle(size / 2, Color.web(hexColor));
        Label l = new Label(initials);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, (int) (size * 0.38)));
        l.setTextFill(Color.WHITE);
        StackPane sp = new StackPane(c, l);
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        addPopHover(sp, 1.1);
        return sp;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Slide-in sidebar animation (now fades in as it slides)
    // ─────────────────────────────────────────────────────────────────────
    public static void slideIn(Node sidebar, double width) {
        sidebar.setTranslateX(-width);
        sidebar.setOpacity(0);
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(380),
            d(sidebar.translateXProperty(), 0),
            d(sidebar.opacityProperty(), 1)));
        tl.play();
    }
}