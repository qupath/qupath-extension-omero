package qupath.ext.omero;

import qupath.ext.omero.core.Client;
import qupath.ext.omero.gui.UiUtilities;
import qupath.fx.dialogs.Dialogs;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Utility methods for the whole extension.
 */
public class Utils {

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.omero.strings");

    private Utils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * @return the resources containing the localized strings of the extension
     */
    public static ResourceBundle getResources() {
        return resources;
    }

    public static void displayPingErrorDialogIfUiPresent(Client client) {
        if (UiUtilities.usingGUI()) {
            Dialogs.showErrorMessage(
                    resources.getString("Utils.connectionError"),
                    MessageFormat.format(
                            resources.getString("Utils.connectionClosed"),
                            client.getApisHandler().getWebServerURI()
                    )
            );
        }
    }
}
