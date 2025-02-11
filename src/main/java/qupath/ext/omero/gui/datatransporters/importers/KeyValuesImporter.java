package qupath.ext.omero.gui.datatransporters.importers;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
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
 * Import key value pairs from an OMERO server to the currently opened image.
 * <p>
 * Since key value pairs are only defined in projects, a project must be opened.
 * <p>
 * This class uses a {@link KeyValuesForm} to prompt the user for parameters.
 */
public class KeyValuesImporter implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(KeyValuesImporter.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private final QuPathGUI quPath;

    /**
     * Create the key value importer.
     *
     * @param quPath the quPath window
     */
    public KeyValuesImporter(QuPathGUI quPath) {
        this.quPath = quPath;
    }

    @Override
    public String getMenuTitle() {
        return resources.getString("DataTransporters.KeyValuesImporter.importKeyValues");
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return projectOpened;
    }

    @Override
    public void transportData() {
        if (quPath.getProject() == null) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                    resources.getString("DataTransporters.KeyValuesImporter.projectNotOpened")
            );
            return;
        }

        if (quPath.getViewer() == null || !(quPath.getViewer().getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                    resources.getString("DataTransporters.KeyValuesImporter.notFromOMERO")
            );
            return;
        }

        KeyValuesForm keyValuesForm;
        try {
            keyValuesForm = new KeyValuesForm();
        } catch (IOException e) {
            logger.error("Error when creating the key values form", e);
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                    e.getLocalizedMessage()
            );
            return;
        }

        boolean confirmed = Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                keyValuesForm
        );
        if (!confirmed) {
            return;
        }

        omeroImageServer.getClient().getApisHandler().getAnnotations(omeroImageServer.getId(), Image.class)
                .exceptionally(error -> {
                    logger.error("Error while retrieving annotations", error);

                    Platform.runLater(() -> Dialogs.showErrorMessage(
                            resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                            resources.getString("DataTransporters.KeyValuesImporter.couldNotRetrieveAnnotations")
                    ));
                    return null;
                })
                .thenAccept(annotationGroup -> Platform.runLater(() -> {
                    if (annotationGroup == null) {
                        return;
                    }

                    Map<String,String> keyValues = MapAnnotation.getCombinedValues(
                            annotationGroup.getAnnotationsOfClass(MapAnnotation.class)
                    );

                    ProjectImageEntry<BufferedImage> projectEntry = quPath.getProject().getEntry(quPath.getImageData());

                    if (keyValuesForm.getChoice().equals(KeyValuesForm.Choice.DELETE_ALL)) {
                        projectEntry.getMetadata().clear();
                    }
                    for (Map.Entry<String, String> entry : keyValues.entrySet()) {
                        if (!keyValuesForm.getChoice().equals(KeyValuesForm.Choice.KEEP_EXISTING) || !projectEntry.getMetadata().containsKey(entry.getKey())) {
                            projectEntry.getMetadata().put(entry.getKey(), entry.getValue());
                        }
                    }

                    Dialogs.showInfoNotification(
                            resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                            resources.getString("DataTransporters.KeyValuesImporter.keyValuesImported")
                    );
                }));
    }
}
