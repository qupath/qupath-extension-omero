package qupath.ext.omero.gui.datatransporters.senders;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.KeyValuesForm;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Send key-value pairs from the currently opened image to an OMERO server.
 * <p>
 * Since key-value pairs are only defined in projects, a project must be opened.
 * <p>
 * This class uses a {@link KeyValuesForm} to prompt the user for parameters.
 */
public class KeyValuesSender implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(KeyValuesSender.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final QuPathGUI quPath;

    /**
     * Create the key-value sender.
     *
     * @param quPath the quPath window
     */
    public KeyValuesSender(QuPathGUI quPath) {
        this.quPath = quPath;
    }

    @Override
    public String getMenuTitle() {
        return resources.getString("DataTransporters.KeyValuesSender.sendKeyValues");
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return projectOpened;
    }

    @Override
    public void transportData() {
        if (quPath.getProject() == null) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    resources.getString("DataTransporters.KeyValuesSender.projectNotOpened")
            );
            return;
        }

        if (quPath.getViewer() == null || !(quPath.getViewer().getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    resources.getString("DataTransporters.KeyValuesSender.notFromOMERO")
            );
            return;
        }

        ProjectImageEntry<BufferedImage> entry = quPath.getProject().getEntry(quPath.getImageData());
        Map<String,String> keyValues = entry.getMetadata();
        if (keyValues.isEmpty()) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    resources.getString("DataTransporters.KeyValuesSender.noValues")
            );
            return;
        }

        KeyValuesForm keyValuesForm;
        try {
            keyValuesForm = new KeyValuesForm();
        } catch (IOException e) {
            logger.error("Error when creating the key-values form", e);
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    e.getLocalizedMessage()
            );
            return;
        }

        boolean confirmed = Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                keyValuesForm
        );
        if (!confirmed) {
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.KeyValuesSender.sendingKeyValues")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window");
            return;
        }
        waitingWindow.show();

        omeroImageServer.getClient().getApisHandler().sendKeyValuePairs(
                omeroImageServer.getId(),
                keyValues,
                keyValuesForm.getChoice().equals(KeyValuesForm.Choice.REPLACE_EXITING),
                keyValuesForm.getChoice().equals(KeyValuesForm.Choice.DELETE_ALL)
        ).handle((v, error) -> {
            Platform.runLater(() -> {
                waitingWindow.close();

                if (error == null) {
                    Dialogs.showInfoNotification(
                            resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                            resources.getString("DataTransporters.KeyValuesSender.keyValuesSent")
                    );
                } else {
                    logger.error("Error while sending key-value pairs", error);
                    Dialogs.showErrorNotification(
                            resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                            resources.getString("DataTransporters.KeyValuesSender.keyValuesNotSent")
                    );
                }
            });

            return null;
        });
    }
}
