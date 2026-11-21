package autolib.fx;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * CameraBarcodeScannerFX — reusable JavaFX modal dialog that opens the
 * webcam, displays a live feed, and continuously decodes barcodes
 * (ISBN-13, ISBN-10, CODE_128, QR_CODE, EAN_8, etc.) using ZXing.
 *
 * Robust camera switching:
 * - Automatically defaults to native physical cameras (e.g. HD Camera, Integrated Camera)
 * - Identifies and deprioritizes virtual cameras (Iriun, OBS, DroidCam)
 * - Thread-safe switching without DirectShow deadlocks
 * - One-click "Use Native Camera" shortcut
 */
public class CameraBarcodeScannerFX {

    private static final int CAM_W = 640;
    private static final int CAM_H = 480;
    private static final int DECODE_INTERVAL_MS = 120;

    // Remembers preferred camera across scans
    private static String preferredCameraName = null;

    private Stage    stage;
    private Canvas   canvas;
    private Label    statusLbl;
    private Label    lastCodeLbl;
    private ComboBox<Webcam> cameraCombo;
    private Button   nativeCamBtn;

    private volatile boolean running = false;
    private final AtomicBoolean isSwitching = new AtomicBoolean(false);
    private boolean suppressEvents = false;

    private Webcam webcam;
    private Thread cameraThread;
    private Thread decodeThread;
    private final BlockingQueue<BufferedImage> frameQueue = new LinkedBlockingQueue<>(2);
    private final AtomicReference<String> lastDetected = new AtomicReference<>(null);

    private Timeline scanLineAnim;
    private Rectangle scanLine;

    // ── Public API ────────────────────────────────────────────────────────

    public void show(Stage owner, Consumer<String> onResult) {
        if (stage != null && stage.isShowing()) return;

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("A.K.L.A.T. — Camera Scanner");
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> shutdown());

        VBox root = buildUI(onResult);
        Scene scene = new Scene(root, CAM_W + 40, CAM_H + 215);
        stage.setScene(scene);

        try {
            String css = getClass().getResource("/autolib.css") != null
                ? getClass().getResource("/autolib.css").toExternalForm() : null;
            if (css != null) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        stage.show();
        initCamerasAndStart(onResult);
    }

    // ── UI ────────────────────────────────────────────────────────────────

    private VBox buildUI(Consumer<String> onResult) {
        VBox root = new VBox(10);
        root.setStyle("-fx-background-color:#0D1117;");
        root.setPadding(new Insets(14, 20, 14, 20));
        root.setAlignment(Pos.CENTER);

        // Header
        Label title = new Label("\uD83D\uDCF7  Camera Barcode Scanner");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        title.setTextFill(Color.WHITE);

        Label sub = new Label("Hold a barcode or ISBN in front of the camera");
        sub.setFont(Font.font("Segoe UI", 11));
        sub.setTextFill(Color.web("#8C96AA"));

        // Camera source switcher row
        HBox camRow = new HBox(10);
        camRow.setAlignment(Pos.CENTER);
        camRow.setPadding(new Insets(2, 0, 4, 0));

        Label camLbl = new Label("Camera:");
        camLbl.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        camLbl.setTextFill(Color.web("#C5D3F5"));

        cameraCombo = new ComboBox<>();
        cameraCombo.setPrefWidth(270);
        cameraCombo.setStyle(
            "-fx-background-color:#1E2535;" +
            "-fx-text-fill:white;" +
            "-fx-border-color:#2D3748;" +
            "-fx-border-radius:6;" +
            "-fx-background-radius:6;"
        );

        cameraCombo.setCellFactory(lv -> new ListCell<Webcam>() {
            @Override protected void updateItem(Webcam w, boolean empty) {
                super.updateItem(w, empty);
                if (empty || w == null) {
                    setText(null);
                } else {
                    boolean isVirt = isVirtualCamera(w);
                    boolean isNat = isNativeCamera(w);
                    String badge = isVirt ? "  (Virtual)" : (isNat ? "  [Native Built-in]" : "");
                    setText(w.getName() + badge);
                }
            }
        });
        cameraCombo.setButtonCell(new ListCell<Webcam>() {
            @Override protected void updateItem(Webcam w, boolean empty) {
                super.updateItem(w, empty);
                if (empty || w == null) {
                    setText("Detecting cameras…");
                } else {
                    boolean isVirt = isVirtualCamera(w);
                    boolean isNat = isNativeCamera(w);
                    String badge = isVirt ? "  (Virtual)" : (isNat ? "  [Native]" : "");
                    setText(w.getName() + badge);
                }
            }
        });

        cameraCombo.setOnAction(e -> {
            if (suppressEvents) return;
            Webcam selected = cameraCombo.getValue();
            if (selected != null) {
                switchCamera(selected, onResult);
            }
        });

        nativeCamBtn = new Button("💻 Use Native Camera");
        nativeCamBtn.setStyle(
            "-fx-background-color:#1E3A8A;" +
            "-fx-text-fill:#93C5FD;" +
            "-fx-font-weight:bold;" +
            "-fx-font-size:11px;" +
            "-fx-background-radius:6;" +
            "-fx-cursor:hand;" +
            "-fx-padding:6 12 6 12;"
        );
        nativeCamBtn.setTooltip(new Tooltip("Switch directly to your computer's built-in / integrated camera"));
        nativeCamBtn.setOnAction(e -> {
            Webcam nativeCam = findBestCamera(cameraCombo.getItems());
            if (nativeCam != null) {
                suppressEvents = true;
                cameraCombo.setValue(nativeCam);
                suppressEvents = false;
                switchCamera(nativeCam, onResult);
            }
        });

        camRow.getChildren().addAll(camLbl, cameraCombo, nativeCamBtn);

        // Camera canvas + scan overlay
        StackPane viewport = buildViewport();

        // Status label
        statusLbl = new Label("\u23F3  Searching for camera devices\u2026");
        statusLbl.setFont(Font.font("Segoe UI", 12));
        statusLbl.setTextFill(Color.web("#8C96AA"));

        lastCodeLbl = new Label();
        lastCodeLbl.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        lastCodeLbl.setTextFill(Color.web("#27AE60"));
        lastCodeLbl.setVisible(false);

        // Manual entry fallback
        HBox manualRow = new HBox(8);
        manualRow.setAlignment(Pos.CENTER);
        TextField manualTf = new TextField();
        manualTf.setPromptText("Or enter barcode / ISBN manually\u2026");
        manualTf.setPrefWidth(280);
        manualTf.setFont(Font.font("Consolas", 13));
        manualTf.setStyle(
            "-fx-background-color:#1E2535;" +
            "-fx-text-fill:white;" +
            "-fx-prompt-text-fill:#555;" +
            "-fx-border-color:#2D3748;" +
            "-fx-border-radius:6;" +
            "-fx-background-radius:6;" +
            "-fx-padding:7 10 7 10;"
        );
        Button useBtn = new Button("Use");
        useBtn.setStyle(
            "-fx-background-color:#8B1A2B;" +
            "-fx-text-fill:white;" +
            "-fx-font-weight:bold;" +
            "-fx-background-radius:6;" +
            "-fx-cursor:hand;" +
            "-fx-padding:7 16 7 16;"
        );
        useBtn.setOnAction(e -> {
            String val = manualTf.getText().trim();
            if (!val.isEmpty()) {
                shutdown();
                Platform.runLater(() -> onResult.accept(val));
            }
        });
        manualTf.setOnAction(e -> useBtn.fire());
        manualRow.getChildren().addAll(manualTf, useBtn);

        // Cancel button
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(
            "-fx-background-color:transparent;" +
            "-fx-text-fill:#8C96AA;" +
            "-fx-border-color:#2D3748;" +
            "-fx-border-radius:6;" +
            "-fx-cursor:hand;" +
            "-fx-padding:5 20 5 20;"
        );
        cancelBtn.setOnAction(e -> shutdown());

        root.getChildren().addAll(title, sub, camRow, viewport, statusLbl, lastCodeLbl, manualRow, cancelBtn);
        return root;
    }

    private StackPane buildViewport() {
        canvas = new Canvas(CAM_W, CAM_H);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#0B0F1A"));
        gc.fillRect(0, 0, CAM_W, CAM_H);
        gc.setFill(Color.web("#1E2535"));
        gc.setFont(Font.font("Segoe UI", 16));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("Starting camera feed\u2026", CAM_W / 2.0, CAM_H / 2.0);

        scanLine = new Rectangle(CAM_W - 40, 3);
        scanLine.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#FF4D5E", 0.0)),
            new Stop(0.5, Color.web("#FF4D5E", 1.0)),
            new Stop(1.0, Color.web("#FF4D5E", 0.0))));
        scanLine.setEffect(new javafx.scene.effect.DropShadow(14, Color.web("#FF4D5E", 0.9)));

        scanLineAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(scanLine.translateYProperty(), -(CAM_H / 2.0 - 20))),
            new KeyFrame(Duration.millis(1500),
                new KeyValue(scanLine.translateYProperty(), CAM_H / 2.0 - 20,
                    Interpolator.EASE_BOTH)));
        scanLineAnim.setAutoReverse(true);
        scanLineAnim.setCycleCount(Animation.INDEFINITE);
        scanLineAnim.play();

        StackPane reticle = buildReticle();

        StackPane vp = new StackPane(canvas, scanLine, reticle);
        vp.setStyle("-fx-background-color:#0B0F1A;");
        vp.setMaxSize(CAM_W, CAM_H);
        vp.setMinSize(CAM_W, CAM_H);

        Rectangle clip = new Rectangle(CAM_W, CAM_H);
        clip.setArcWidth(16); clip.setArcHeight(16);
        vp.setClip(clip);

        return vp;
    }

    private StackPane buildReticle() {
        StackPane frame = new StackPane();
        frame.setMouseTransparent(true);
        for (Pos p : new Pos[]{Pos.TOP_LEFT, Pos.TOP_RIGHT, Pos.BOTTOM_LEFT, Pos.BOTTOM_RIGHT}) {
            frame.getChildren().add(cornerMark(p));
        }
        return frame;
    }

    private StackPane cornerMark(Pos pos) {
        Polyline pl = new Polyline(0, 32, 0, 0, 32, 0);
        pl.setStroke(Color.web("#FF4D5E"));
        pl.setFill(Color.TRANSPARENT);
        pl.setStrokeWidth(3.5);
        pl.setStrokeLineCap(StrokeLineCap.ROUND);

        StackPane box = new StackPane(pl);
        box.setMaxSize(36, 36);
        box.setMinSize(36, 36);
        box.setScaleX(pos.getHpos() == HPos.RIGHT ? -1 : 1);
        box.setScaleY(pos.getVpos() == VPos.BOTTOM ? -1 : 1);
        StackPane.setAlignment(box, pos);
        StackPane.setMargin(box, new Insets(16));
        return box;
    }

    // ── Camera Initialization & Switching ─────────────────────────────────

    private void initCamerasAndStart(Consumer<String> onResult) {
        new Thread(() -> {
            List<Webcam> cams;
            try {
                cams = Webcam.getWebcams();
            } catch (Exception ex) {
                cams = Collections.emptyList();
            }

            final List<Webcam> camList = cams;
            Platform.runLater(() -> {
                suppressEvents = true;
                cameraCombo.getItems().setAll(camList);

                if (camList.isEmpty()) {
                    suppressEvents = false;
                    statusLbl.setText("\u274C No camera devices found. Use manual entry below.");
                    return;
                }

                // Pick best camera: preferred (if valid) > best native physical > first
                Webcam toSelect = null;
                if (preferredCameraName != null) {
                    for (Webcam w : camList) {
                        if (w.getName().equalsIgnoreCase(preferredCameraName)) {
                            toSelect = w;
                            break;
                        }
                    }
                }
                if (toSelect == null) {
                    toSelect = findBestCamera(camList);
                }
                if (toSelect == null && !camList.isEmpty()) {
                    toSelect = camList.get(0);
                }

                if (toSelect != null) {
                    cameraCombo.setValue(toSelect);
                    suppressEvents = false;
                    switchCamera(toSelect, onResult);
                } else {
                    suppressEvents = false;
                }
            });
        }, "aklat-cam-enum").start();
    }

    private void switchCamera(Webcam newCam, Consumer<String> onResult) {
        if (newCam == null) return;

        // Prevent concurrent double-invocations
        if (!isSwitching.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            boolean isNat = isNativeCamera(newCam);
            statusLbl.setText("\u23F3 Connecting to " + newCam.getName() + (isNat ? " [Native]" : "") + "\u2026");
            statusLbl.setTextFill(Color.web("#8C96AA"));

            // Clear canvas to dark placeholder while switching
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.web("#0B0F1A"));
            gc.fillRect(0, 0, CAM_W, CAM_H);
            gc.setFill(Color.web("#1E2535"));
            gc.setFont(Font.font("Segoe UI", 15));
            gc.fillText("Connecting to " + newCam.getName() + "…", CAM_W / 2.0, CAM_H / 2.0);
        });

        new Thread(() -> {
            try {
                // Signal threads to stop and give them a short moment to exit loop
                running = false;
                if (cameraThread != null) cameraThread.interrupt();
                if (decodeThread != null) decodeThread.interrupt();

                try { Thread.sleep(120); } catch (Exception ignored) {}

                // Close previously opened webcam safely
                if (webcam != null && webcam.isOpen()) {
                    try {
                        webcam.close();
                    } catch (Exception ex) {
                        System.err.println("[AutoLib Camera] Error closing previous camera: " + ex.getMessage());
                    }
                }

                webcam = newCam;
                preferredCameraName = newCam.getName();
                frameQueue.clear();
                lastDetected.set(null);

                // Configure view size if supported
                Dimension[] supported = webcam.getViewSizes();
                Dimension chosen = null;
                if (supported != null && supported.length > 0) {
                    for (Dimension d : supported) {
                        if (d.width == 640 && d.height == 480) {
                            chosen = d;
                            break;
                        }
                    }
                    if (chosen == null) chosen = supported[0];
                }
                if (chosen != null) {
                    try { webcam.setViewSize(chosen); } catch (Exception ignored) {}
                }

                // Start capture
                startCapture(onResult);

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    statusLbl.setText("\u274C Could not open " + newCam.getName() + ": " + ex.getMessage());
                    statusLbl.setTextFill(Color.web("#C0392B"));
                });
            } finally {
                isSwitching.set(false);
            }
        }, "aklat-cam-switch").start();
    }

    private void startCapture(Consumer<String> onResult) {
        running = true;

        // Camera grab thread
        cameraThread = new Thread(() -> {
            try {
                if (webcam == null) return;

                if (!webcam.isOpen()) {
                    boolean opened = webcam.open();
                    if (!opened) {
                        Platform.runLater(() -> {
                            statusLbl.setText("\u274C Failed to open " + webcam.getName() + ". Try another camera.");
                            statusLbl.setTextFill(Color.web("#C0392B"));
                        });
                        return;
                    }
                }

                Platform.runLater(() -> {
                    boolean isNat = isNativeCamera(webcam);
                    statusLbl.setText("\u25CF LIVE (" + webcam.getName() + (isNat ? " \u2014 Native" : "") + ")  \u2014  Align barcode within frame");
                    statusLbl.setTextFill(Color.web("#4E8CFF"));
                });

                while (running && webcam.isOpen()) {
                    BufferedImage frame = webcam.getImage();
                    if (frame != null) {
                        WritableImage fxImg = bufferedToFX(frame);
                        Platform.runLater(() -> {
                            canvas.getGraphicsContext2D().drawImage(fxImg, 0, 0, CAM_W, CAM_H);
                        });
                        frameQueue.offer(frame);
                    }
                    Thread.sleep(33); // ~30 FPS
                }
            } catch (InterruptedException ignored) {
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLbl.setText("\u274C Camera stream ended: " + ex.getMessage() + ". Try another camera or manual entry.");
                    statusLbl.setTextFill(Color.web("#C0392B"));
                });
            }
        }, "aklat-camera-grab");
        cameraThread.setDaemon(true);
        cameraThread.start();

        // ZXing decode thread
        decodeThread = new Thread(() -> {
            MultiFormatReader reader = new MultiFormatReader();
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            // Strict formats for books & library barcodes (avoids false-positive partial slices from UPC_E / EAN_8)
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.CODE_128,
                BarcodeFormat.UPC_A,
                BarcodeFormat.QR_CODE
            ));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            reader.setHints(hints);

            String candidate = null;
            int consecutiveHits = 0;

            while (running) {
                try {
                    BufferedImage frame = frameQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (frame == null) continue;

                    BinaryBitmap bitmap = new BinaryBitmap(
                        new HybridBinarizer(new BufferedImageLuminanceSource(frame)));

                    try {
                        Result result = reader.decodeWithState(bitmap);
                        String code = result != null ? result.getText().trim() : "";

                        // Reject codes that are too short (blurry partial slices)
                        if (code.length() >= 7) {
                            if (code.equals(candidate)) {
                                consecutiveHits++;
                                // Require 2 consecutive matching frames to confirm scan
                                if (consecutiveHits >= 2) {
                                    running = false;
                                    Platform.runLater(() -> onScanSuccess(code, onResult));
                                    break;
                                }
                            } else {
                                candidate = code;
                                consecutiveHits = 1;
                            }
                        }
                    } catch (NotFoundException ignored) {
                        // Reset consecutive count if barcode is lost
                        consecutiveHits = 0;
                    } finally {
                        reader.reset();
                    }

                    Thread.sleep(DECODE_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }, "aklat-barcode-decode");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    private void onScanSuccess(String code, Consumer<String> onResult) {
        scanLine.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#27AE60", 0.0)),
            new Stop(0.5, Color.web("#27AE60", 1.0)),
            new Stop(1.0, Color.web("#27AE60", 0.0))));
        scanLine.setEffect(new javafx.scene.effect.DropShadow(14, Color.web("#27AE60", 0.9)));

        statusLbl.setText("\u2705  Barcode detected: " + code);
        statusLbl.setTextFill(Color.web("#27AE60"));
        lastCodeLbl.setText(code);
        lastCodeLbl.setVisible(true);

        PauseTransition pause = new PauseTransition(Duration.millis(600));
        pause.setOnFinished(e -> {
            shutdown();
            onResult.accept(code);
        });
        pause.play();
    }

    // ── Classification Helpers ───────────────────────────────────────────

    /**
     * Identifies virtual or phone-relay cameras that lack a physical sensor on this machine.
     */
    public static boolean isVirtualCamera(Webcam w) {
        if (w == null) return false;
        String n = w.getName().toLowerCase();
        return n.contains("iriun")
            || n.contains("obs")
            || n.contains("droidcam")
            || n.contains("camo")
            || n.contains("epoccam")
            || n.contains("virtual")
            || n.contains("manycam")
            || n.contains("vysor")
            || n.contains("splitcam")
            || n.contains("snap camera")
            || n.contains("unity")
            || n.contains("ndi");
    }

    /**
     * Checks if a webcam is a physical built-in native camera.
     */
    public static boolean isNativeCamera(Webcam w) {
        if (w == null || isVirtualCamera(w)) return false;
        String n = w.getName().toLowerCase();
        return n.contains("hd camera")
            || n.contains("integrated")
            || n.contains("built-in")
            || n.contains("builtin")
            || n.contains("internal")
            || n.contains("easycamera")
            || n.contains("facetime")
            || n.contains("front")
            || n.contains("facing")
            || n.contains("laptop")
            || n.contains("pc camera");
    }

    /**
     * Finds the best camera to open:
     * 1. Built-in physical native camera (e.g. HD Camera 1)
     * 2. Any physical non-virtual camera
     * 3. Fallback to first available
     */
    public static Webcam findBestCamera(List<Webcam> cams) {
        if (cams == null || cams.isEmpty()) return null;

        // 1. Try explicit native camera first (non-virtual)
        for (Webcam w : cams) {
            if (isNativeCamera(w)) return w;
        }

        // 2. Try any physical non-virtual camera
        for (Webcam w : cams) {
            if (!isVirtualCamera(w)) return w;
        }

        // 3. Fallback
        return cams.get(0);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────

    private void shutdown() {
        running = false;
        if (scanLineAnim != null) scanLineAnim.stop();
        if (cameraThread != null) cameraThread.interrupt();
        if (decodeThread != null) decodeThread.interrupt();
        new Thread(() -> {
            try {
                if (webcam != null && webcam.isOpen()) webcam.close();
            } catch (Exception ignored) {}
        }, "aklat-cam-close").start();
        Platform.runLater(() -> {
            if (stage != null && stage.isShowing()) stage.close();
        });
    }

    // ── Pixel conversion ─────────────────────────────────────────────────

    private static WritableImage bufferedToFX(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h,
            PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return img;
    }
}
