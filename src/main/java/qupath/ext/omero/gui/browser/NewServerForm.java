package qupath.ext.omero.gui.browser;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import qupath.ext.omero.core.PreferencesManager;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;

/**
 * Window that provides an input allowing the user to write a server URL.
 */
public class NewServerForm extends VBox {

    @FXML
    private TextField url;

    /**
     * Creates the new server form.
     * @throws IOException if an error occurs while creating the form
     */
    public NewServerForm() throws IOException {
        UiUtilities.loadFXML(this, NewServerForm.class.getResource("new_server_form.fxml"));

        PreferencesManager.getLastServerURI().ifPresent(uri -> url.setText(uri.toString()));
    }

    /**
     * @return the URL of the server to create a connection to
     */
    public String getURL() {
        return url.getText();
    }
}
