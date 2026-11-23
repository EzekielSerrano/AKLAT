package autolib.fx;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * AutoLib JavaFX — Application entry point.
 *
 * Usage (after JavaFX SDK is in lib/javafx-sdk-17/lib):
 *
 *   Run via run_autolib_fx.bat  (auto-generated beside this class)
 *   or manually:
 *
 *   javac --module-path lib\javafx-sdk-17\lib --add-modules javafx.controls,javafx.fxml
 *         -encoding UTF-8 -d out -sourcepath . autolib\fx\AutoLibFX.java ...
 *
 *   java --module-path lib\javafx-sdk-17\lib --add-modules javafx.controls,javafx.fxml
 *        -cp out autolib.fx.AutoLibFX
 */
public class AutoLibFX extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Enable font anti-aliasing (Windows)
        System.setProperty("prism.lcdtext", "true");
        System.setProperty("prism.text", "t2k");

        // Pre-connect to PostgreSQL and warm up in-memory caches in background immediately
        autolib.DataStore.warmUpAsync();

        boolean kioskOnly = "true".equalsIgnoreCase(System.getProperty("autolib.kiosk"));

        if (kioskOnly) {
            // ── Student Kiosk mode: skip the portal, go straight to kiosk ──
            primaryStage.setTitle("A.K.L.A.T. — Student Kiosk");
            StudentKioskFX kiosk = new StudentKioskFX(primaryStage);
            Scene scene = kiosk.buildScene();
            primaryStage.setScene(scene);
            primaryStage.setWidth(1120);
            primaryStage.setHeight(700);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);
            primaryStage.centerOnScreen();
            primaryStage.show();
        } else {
            // ── Normal mode: show the portal selection screen ──
            PortalScreenFX portal = new PortalScreenFX(primaryStage);
            Scene scene = portal.buildScene();
            primaryStage.setTitle("A.K.L.A.T. — Automated Key Library Analytics & Tracking");
            primaryStage.setScene(scene);
            primaryStage.setWidth(1024);
            primaryStage.setHeight(620);
            primaryStage.setMinWidth(820);
            primaryStage.setMinHeight(540);
            primaryStage.centerOnScreen();
            primaryStage.show();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

