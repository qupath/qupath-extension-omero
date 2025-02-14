package qupath.ext.omero.gui.datatransporters.importers;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.repositoryentities.Server;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.ImportAnnotationForm;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Import QuPath annotations from an OMERO server to the currently opened image.
 * <p>
 * Here, an annotation refers to a QuPath annotation (a path object) and <b>not</b>
 * an OMERO annotation (some metadata attached to images for example).
 * <p>
 * This class uses an {@link ImportAnnotationForm} to prompt the user for parameters.
 */
public class AnnotationImporter implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationImporter.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final QuPathGUI quPath;

    /**
     * Create the annotation importer.
     *
     * @param quPath the quPath window
     */
    public AnnotationImporter(QuPathGUI quPath) {
        this.quPath = quPath;
    }

    @Override
    public String getMenuTitle() {
        return resources.getString("DataTransporters.AnnotationsImporter.importAnnotations");
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return true;
    }

    @Override
    public void transportData() {
        QuPathViewer viewer = quPath.getViewer();

        if (!(viewer.getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsImporter.importAnnotations"),
                    resources.getString("DataTransporters.AnnotationsImporter.notFromOMERO")
            );
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.AnnotationsImporter.retrievingServer")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window");
            return;
        }
        waitingWindow.show();

        omeroImageServer.getClient().getServer()
                .exceptionally(error -> {
                    logger.error("Cannot get server information on {}", omeroImageServer.getURIs(), error);
                    return null;
                })
                .thenAccept(server -> Platform.runLater(() -> {
                    waitingWindow.close();

                    if (server == null) {
                        Dialogs.showErrorMessage(
                                resources.getString("DataTransporters.AnnotationsImporter.serverError"),
                                MessageFormat.format(
                                        resources.getString("DataTransporters.AnnotationsImporter.cannotGetServerInformation"),
                                        omeroImageServer.getURIs()
                                )
                        );
                    } else {
                        importAnnotations(omeroImageServer, server, viewer);
                    }
                }));
    }

    private void importAnnotations(OmeroImageServer omeroImageServer, Server server, QuPathViewer viewer) {
        ImportAnnotationForm annotationForm;
        try {
            annotationForm = new ImportAnnotationForm(server.getOwners());
        } catch (IOException e) {
            logger.error("Error when creating the annotation form", e);
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsImporter.importAnnotations"),
                    e.getLocalizedMessage()
            );
            return;
        }

        boolean confirmed = Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.AnnotationsImporter.dataToSend"),
                annotationForm
        );
        if (!confirmed) {
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.AnnotationsImporter.importingAnnotations")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window");
            return;
        }
        waitingWindow.show();

        omeroImageServer.getClient().getApisHandler().getShapes(omeroImageServer.getId(), annotationForm.getSelectedOwner().id()).thenAccept(shapes -> Platform.runLater(() -> {
            waitingWindow.close();

            if (shapes.isEmpty()) {
                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.AnnotationsImporter.noAnnotations"),
                        resources.getString("DataTransporters.AnnotationsImporter.noAnnotationsFound")
                );
                return;
            }
            List<PathObject> pathObjects = Shape.createPathObjects(shapes);

            PathObjectHierarchy hierarchy = viewer.getImageData().getHierarchy();
            StringBuilder message = new StringBuilder();

            if (annotationForm.deleteCurrentAnnotations()) {
                hierarchy.removeObjects(hierarchy.getAnnotationObjects(),true);
                message
                        .append(resources.getString("DataTransporters.AnnotationsImporter.currentAnnotationsDeleted"))
                        .append("\n");
            }

            if (annotationForm.deleteCurrentDetections()) {
                hierarchy.removeObjects(hierarchy.getDetectionObjects(), false);
                message
                        .append(resources.getString("DataTransporters.AnnotationsImporter.currentDetectionsDeleted"))
                        .append("\n");
            }

            hierarchy.addObjects(pathObjects);
            hierarchy.resolveHierarchy();

            String title;
            if (pathObjects.size() == 1) {
                title = resources.getString("DataTransporters.AnnotationsImporter.1WrittenSuccessfully");
                message.append(resources.getString("DataTransporters.AnnotationsImporter.1AnnotationImported"));
            } else {
                title = resources.getString("DataTransporters.AnnotationsImporter.1WrittenSuccessfully");
                message.append(MessageFormat.format(resources.getString("DataTransporters.AnnotationsImporter.XAnnotationImported"), pathObjects.size()));
            }

            Dialogs.showInfoNotification(
                    title,
                    message.toString()
            );
        }));
    }
}
