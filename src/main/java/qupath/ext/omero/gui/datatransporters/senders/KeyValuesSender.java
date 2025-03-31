package qupath.ext.omero.gui.datatransporters.senders;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.core.entities.annotations.Annotation;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.gui.datatransporters.forms.SendKeyValuePairsForm;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Send key-value pairs from the currently opened image to an OMERO server.
 * <p>
 * Since key-value pairs are only defined in projects, a project must be opened.
 * <p>
 * This class uses a {@link SendKeyValuePairsForm} to prompt the user for parameters.
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
        logger.debug("Creating KVP sender for {}", quPath);
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
        logger.debug("Attempting to send KVP to OMERO");

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

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.KeyValuesSender.gettingExistingNamespaces")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        logger.debug("Getting annotations of image with ID {}", omeroImageServer.getId());
        omeroImageServer.getClient().getApisHandler().getAnnotations(omeroImageServer.getId(), Image.class).whenComplete(((annotationGroup, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (annotationGroup == null) {
                logger.error("Cannot get annotations of image with ID {}. Can't send KVP", omeroImageServer.getId(), error);

                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                        resources.getString("DataTransporters.KeyValuesSender.cannotGetExistingKvp")
                );
                return;
            }
            logger.debug("Got annotations {} for image with ID {}", annotationGroup, omeroImageServer.getId());

            sendKeyValuePairs(
                    omeroImageServer,
                    keyValues,
                    annotationGroup.getAnnotationsOfClass(MapAnnotation.class).stream()
                            .map(Annotation::getNamespace)
                            .flatMap(Optional::stream)
                            .distinct()
                            .toList()
            );
        })));
    }

    @Override
    public String toString() {
        return String.format("KVP sender for %s", quPath);
    }

    private void sendKeyValuePairs(OmeroImageServer omeroImageServer, Map<String,String> keyValues, List<Namespace> namespaces) {
        logger.debug("Got namespaces {} for image with ID {}", namespaces, omeroImageServer.getId());

        SendKeyValuePairsForm sendKeyValuePairsForm;
        try {
            sendKeyValuePairsForm = new SendKeyValuePairsForm(keyValues, namespaces);
        } catch (IOException e) {
            logger.error("Error when creating the send key-values form", e);
            return;
        }

        boolean confirmed = Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                sendKeyValuePairsForm
        );
        if (!confirmed) {
            logger.debug("Sending KVP dialog not confirmed. Not sending KVP");
            return;
        }

        if (sendKeyValuePairsForm.getKeyValuePairsToSend().isEmpty()) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesSender.sendKeyValues"),
                    resources.getString("DataTransporters.KeyValuesSender.noKeyValueSelected")
            );
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.KeyValuesSender.sendingKeyValues")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        logger.debug(
                "Sending {} with namespace {} to image with ID {}",
                sendKeyValuePairsForm.getKeyValuePairsToSend(),
                sendKeyValuePairsForm.getSelectedNamespace(),
                omeroImageServer.getId()
        );
        omeroImageServer.getClient().getApisHandler().sendKeyValuePairs(
                omeroImageServer.getId(),
                sendKeyValuePairsForm.getSelectedNamespace(),
                sendKeyValuePairsForm.getKeyValuePairsToSend(),
                sendKeyValuePairsForm.getSelectedChoice().equals(SendKeyValuePairsForm.Choice.REPLACE_EXITING)
        ).whenComplete((v, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (error == null) {
                logger.debug("KVP {} sent to image with ID {}", sendKeyValuePairsForm.getKeyValuePairsToSend(), omeroImageServer.getId());
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
        }));
    }
}
