package qupath.ext.omero.gui.login;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.gui.UiUtils;

import java.io.IOException;

/**
 * A modal window that displays a loading indicator while an operation is happening.
 * It can optionally be cancelled.
 */
public class WaitingWindow extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(WaitingWindow.class);
    private final Runnable onCancelClicked;
    @FXML
    private Label label;
    @FXML
    private Button cancel;

    /**
     * Create the window, giving the user the option to cancel the operation.
     *
     * @param owner the stage this window should be modal to
     * @param label a text describing the operation
     * @param onCancelClicked a function that will be called if the user wants to cancel the operation. If that happens,
     *                        this window is automatically closed. This function will be called from the JavaFX Application
     *                        Thread
     * @throws IOException if an error occurs while getting the FXML file of this window
     */
    public WaitingWindow(Stage owner, String label, Runnable onCancelClicked) throws IOException {
        logger.debug("Creating waiting window for {}", label);
        this.onCancelClicked = onCancelClicked;

        UiUtils.loadFXML(this, WaitingWindow.class.getResource("waiting_window.fxml"));

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);

        setTitle(label);
        this.label.setText(label);

        if (onCancelClicked == null) {
            cancel.setVisible(false);
            cancel.setManaged(false);
        }
    }

    /**
     * Create the window, without giving the user the option to cancel the operation.
     *
     * @param owner the stage this window should be modal to
     * @param label a text describing the operation
     * @throws IOException if an error occurs while getting the FXML file of this window
     */
    public WaitingWindow(Stage owner, String label) throws IOException {
        this(owner, label, null);
    }

    @FXML
    private void onCancelClicked(ActionEvent ignored) {
        logger.debug("Cancel button clicked. Closing waiting window");
        close();

        if (onCancelClicked != null) {
            onCancelClicked.run();
        }
    }
}
