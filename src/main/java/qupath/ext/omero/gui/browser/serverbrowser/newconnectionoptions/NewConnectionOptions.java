package qupath.ext.omero.gui.browser.serverbrowser.newconnectionoptions;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;

/**
 * A form to prompt the user for parameters before attempting to create a connection to a server.
 */
public class NewConnectionOptions extends VBox {

    @FXML
    private CheckBox skipAuthentication;

    /**
     * Creates the new connection options form.
     *
     * @throws IOException if an error occurs while creating the form
     */
    public NewConnectionOptions() throws IOException {
        UiUtilities.loadFXML(this, NewConnectionOptions.class.getResource("new_connection_options.fxml"));
    }

    /**
     * @return whether to try skipping authentication when connecting to the server
     */
    public boolean canSkipAuthentication() {
        return skipAuthentication.isSelected();
    }
}

