package qupath.ext.omero.gui.browser.newserver;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import qupath.ext.omero.core.ClientsPreferencesManager;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Window that provides an input allowing the user to write a server URL.
 */
public class NewServerForm extends VBox {

    @FXML
    private TextField url;

    @FXML
    private CheckBox skipAuthentication;

    /**
     * Creates the new server form.
     * @throws IOException if an error occurs while creating the form
     */
    public NewServerForm() throws IOException {
        UiUtilities.loadFXML(this, NewServerForm.class.getResource("new_server_form.fxml"));

        String lastURI = ClientsPreferencesManager.getLastServerURI();
        url.setText(lastURI);

        boolean skipAuthentication = true;
        try {
            skipAuthentication = ClientsPreferencesManager.getEnableUnauthenticated(new URI(lastURI)).orElse(true);
        } catch (URISyntaxException ignored) {
            // Nothing happens if the last URI is not valid
        }
        this.skipAuthentication.setSelected(skipAuthentication);
    }

    /**
     * @return the URL of the server to create a connection to
     */
    public String getURL() {
        return url.getText();
    }

    /**
     * @return whether to try skipping authentication when connecting to the server
     */
    public boolean canSkipAuthentication() {
        return skipAuthentication.isSelected();
    }
}
