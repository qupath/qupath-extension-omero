package qupath.ext.omero.gui.datatransporters.senders;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.KeyValuesForm;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * <p>
 *     Send key value pairs from the currently opened image to an OMERO server.
 * </p>
 * <p>
 *     Since key value pairs are only defined in projects, a project must be opened.
 * </p>
 * <p>
 *     This class uses a {@link KeyValuesForm} to prompt the user for parameters.
 * </p>
 */
public class KeyValuesSender implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(KeyValuesSender.class);
    private static final ResourceBundle resources = UiUtilities.getResources();

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
        QuPathGUI qupath = QuPathGUI.getInstance();

        if (qupath.getProject() == null) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    resources.getString("DataTransporters.KeyValuesSender.projectNotOpened")
            );
        } else if (qupath.getViewer() == null || !(qupath.getViewer().getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    resources.getString("DataTransporters.KeyValuesSender.notFromOMERO")
            );
        } else {
            ProjectImageEntry<BufferedImage> entry = qupath.getProject().getEntry(qupath.getImageData());
            Map<String,String> keyValues = entry.getMetadataMap();

            if (keyValues.isEmpty()) {
                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                        resources.getString("DataTransporters.KeyValuesSender.noValues")
                );
            } else {
                KeyValuesForm keyValuesForm;
                try {
                    keyValuesForm = new KeyValuesForm();
                } catch (IOException e) {
                    logger.error("Error when creating the key values form", e);
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

                if (confirmed) {
                    omeroImageServer.getClient().getApisHandler().sendKeyValuePairs(
                            omeroImageServer.getId(),
                            keyValues,
                            keyValuesForm.getChoice().equals(KeyValuesForm.Choice.REPLACE_EXITING),
                            keyValuesForm.getChoice().equals(KeyValuesForm.Choice.DELETE_ALL)
                    ).handle((v, error) -> {
                        if (error == null) {
                            Dialogs.showInfoNotification(
                                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                                    resources.getString("DataTransporters.KeyValuesSender.keyValuesSent")
                            );
                        } else {
                            logger.error("Error while sending key value pairs", error);
                            Dialogs.showErrorNotification(
                                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                                    resources.getString("DataTransporters.KeyValuesSender.keyValuesNotSent")
                            );
                        }
                        return null;
                    });
                }
            }
        }
    }
}
