package autolib.fx;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Modern Librarian Login Dialog.
 * Clean, minimalist aesthetic with fluid animations and responsive hover effects.
 * Credentials: admin / admin123
 */
public class LoginDialogFX {

    private final Stage owner;
    private Stage dialog;
    private Consumer<Boolean> callback;
    private TextField userField;
    private PasswordField passField;
    private Label errorLabel;
    private Button loginBtn;

    public LoginDialogFX(Stage owner) {
        this.owner = owner;
    }

    public void show(Consumer<Boolean> onResult) {
        this.callback = onResult;

        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(false);

        VBox card = buildCard();
        Scene scene = new Scene(card, 440, 440);
        scene.setFill(Color.TRANSPARENT);
        FXComponents.applyStylesheet(scene);
        dialog.setScene(scene);
        dialog.centerOnScreen();

        card.setOpacity(0);
        dialog.show();
        FXComponents.fadeInSlide(card);
    }

    private VBox buildCard() {
        VBox root = new VBox(0);
        root.setMaxWidth(440);
        root.setPrefWidth(440);
        root.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 20;" +
            "-fx-border-radius: 20;" +
            "-fx-border-color: #E2E8F4;" +
            "-fx-border-width: 1.5;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.4), 45, 0, 0, 16);"
        );

        root.getChildren().addAll(buildHeader(), buildBody());
        return root;
    }

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setStyle(
            "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #12163A, #1B214C);" +
            "-fx-background-radius: 19 19 0 0;" +
            "-fx-padding: 22 26 22 26;"
        );
        header.setAlignment(Pos.CENTER_LEFT);

        // Logo icon
        StackPane logo = FXComponents.logoIcon(36);

        VBox titleBox = new VBox(2);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label title = new Label("Staff Authentication");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.WHITE);

        Label sub = new Label("A.K.L.A.T. · Administrative Access");
        sub.setFont(Font.font("Segoe UI", 10.5));
        sub.setTextFill(Color.web("#7888B0"));
        titleBox.getChildren().addAll(title, sub);

        // Close button with hover animation
        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.08);" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: #94A3B8;" +
            "-fx-font-size: 13;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 10 5 10;"
        );

        closeBtn.setOnMouseEntered(e -> {
            closeBtn.setStyle(
                "-fx-background-color: #CC2936;" +
                "-fx-background-radius: 8;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 5 10 5 10;"
            );
        });

        closeBtn.setOnMouseExited(e -> {
            closeBtn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.08);" +
                "-fx-background-radius: 8;" +
                "-fx-text-fill: #94A3B8;" +
                "-fx-font-size: 13;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 5 10 5 10;"
            );
        });
        FXComponents.addPopHover(closeBtn, 1.1);

        closeBtn.setOnAction(e -> closeDialog(false));

        header.getChildren().addAll(logo, titleBox, closeBtn);

        // Draggable window support
        double[] dragOffset = new double[2];
        header.setOnMousePressed(e -> {
            dragOffset[0] = e.getSceneX();
            dragOffset[1] = e.getSceneY();
        });
        header.setOnMouseDragged(e -> {
            dialog.setX(e.getScreenX() - dragOffset[0]);
            dialog.setY(e.getScreenY() - dragOffset[1]);
        });

        return header;
    }

    private VBox buildBody() {
        VBox body = new VBox(0);
        body.setPadding(new Insets(28, 32, 28, 32));
        body.setStyle("-fx-background-color: white; -fx-background-radius: 0 0 19 19;");

        // Username Field
        Label userLbl = fieldLabel("Username / Staff ID");
        userField = FXComponents.inputField("e.g. admin");
        userField.setMaxWidth(Double.MAX_VALUE);

        // Password Field
        Label passLbl = fieldLabel("Password");
        VBox.setMargin(passLbl, new Insets(16, 0, 0, 0));
        passField = FXComponents.passwordField("Enter staff password");
        passField.setMaxWidth(Double.MAX_VALUE);

        // Error message row
        HBox errorRow = new HBox(7);
        errorRow.setAlignment(Pos.CENTER_LEFT);
        errorRow.setPadding(new Insets(10, 0, 0, 0));
        errorRow.setOpacity(0);
        errorRow.setManaged(false);

        Label warnIcon = new Label("⚠");
        warnIcon.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        warnIcon.setTextFill(Color.web(FXComponents.RED));

        errorLabel = new Label("Invalid username or password. Please try again.");
        errorLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11.5));
        errorLabel.setTextFill(Color.web(FXComponents.RED));
        errorRow.getChildren().addAll(warnIcon, errorLabel);

        // Login Button
        loginBtn = FXComponents.primaryButton("Sign In to Portal  →");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPadding(new Insets(13, 0, 13, 0));
        VBox.setMargin(loginBtn, new Insets(20, 0, 0, 0));

        // Hint Card
        HBox hintRow = new HBox(8);
        hintRow.setAlignment(Pos.CENTER);
        VBox.setMargin(hintRow, new Insets(16, 0, 0, 0));
        hintRow.setStyle(
            "-fx-background-color: #F8FAFD;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #E2E8F0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-padding: 9 14 9 14;"
        );

        Label keyIcon = new Label("🔑");
        keyIcon.setFont(Font.font("Segoe UI Emoji", 11));
        Label hint = new Label("Default credentials:  admin  /  admin123");
        hint.setFont(Font.font("Segoe UI", 11));
        hint.setTextFill(Color.web("#64748B"));
        hintRow.getChildren().addAll(keyIcon, hint);

        loginBtn.setOnAction(e -> doLogin(errorRow));
        passField.setOnAction(e -> doLogin(errorRow));
        userField.setOnAction(e -> passField.requestFocus());

        body.getChildren().addAll(userLbl, userField, passLbl, passField, errorRow, loginBtn, hintRow);
        return body;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#334155"));
        l.setPadding(new Insets(0, 0, 6, 0));
        return l;
    }

    private void doLogin(HBox errorRow) {
        String u = userField.getText().trim();
        String p = passField.getText().trim();

        String staffName = autolib.DataStore.get().authenticateStaff(u, p);
        if (staffName != null) {
            loginBtn.setText("✔  Welcome, " + staffName);
            loginBtn.setDisable(true);
            loginBtn.setStyle(
                "-fx-background-color: #10B981;" +
                "-fx-background-radius: 10;" +
                "-fx-text-fill: white;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13.5px;" +
                "-fx-padding: 13 0 13 0;"
            );

            FXComponents.pulseOnce(loginBtn);

            PauseTransition pause = new PauseTransition(Duration.millis(450));
            pause.setOnFinished(e -> closeDialog(true));
            pause.play();
        } else {
            // Error shake animation
            errorRow.setManaged(true);
            FXComponents.fadeInSlide(errorRow);
            FXComponents.shake(dialog.getScene().getRoot());
            FXComponents.flagInvalid(passField);
            passField.clear();
            passField.requestFocus();
        }
    }

    private void closeDialog(boolean authenticated) {
        Node root = dialog.getScene().getRoot();

        FadeTransition ft = new FadeTransition(Duration.millis(180), root);
        ft.setToValue(0);

        ScaleTransition st = new ScaleTransition(Duration.millis(180), root);
        st.setToX(0.95);
        st.setToY(0.95);

        ParallelTransition exit = new ParallelTransition(ft, st);
        exit.setOnFinished(e -> {
            dialog.close();
            if (callback != null) callback.accept(authenticated);
        });
        exit.play();
    }
}