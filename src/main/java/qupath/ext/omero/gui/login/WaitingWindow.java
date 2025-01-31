package qupath.ext.omero.gui.login;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;

/**
 * A modal window that displays a loading indicator while an operation is happening.
 * It can optionally be cancelled.
 */
public class WaitingWindow extends Stage {

    private final Runnable onCancelClicked;
    @FXML
    private Label label;

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
        this.onCancelClicked = onCancelClicked;

        UiUtilities.loadFXML(this, WaitingWindow.class.getResource("waiting_window.fxml"));

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);

        //TODO: what happens if user closes this window?

        setTitle(label);
        this.label.setText(label);
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
        close();
        onCancelClicked.run();
    }
}
