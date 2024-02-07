package qupath.ext.omero.gui.datatransporters.importers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.ImportAnnotationForm;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.ResourceBundle;

/**
 * <p>
 *     Import QuPath annotations from an OMERO server to the currently opened image.
 * </p>
 * <p>
 *     Here, an annotation refers to a QuPath annotation (a path object)
 *     and <b>not</b> an OMERO annotation (some metadata attached to images for example).
 * </p>
 * <p>
 *     This class uses an {@link ImportAnnotationForm} to prompt the user for parameters.
 * </p>
 */
public class AnnotationImporter implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationImporter.class);
    private static final ResourceBundle resources = UiUtilities.getResources();

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
        var viewer = QuPathGUI.getInstance().getViewer();

        if (viewer.getServer() instanceof OmeroImageServer omeroImageServer) {
            ImportAnnotationForm annotationForm;
            try {
                annotationForm = new ImportAnnotationForm();
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

            if (confirmed) {
                Collection<PathObject> pathObjects = omeroImageServer.readPathObjects();
                if (pathObjects.isEmpty()) {
                    Dialogs.showErrorMessage(
                            resources.getString("DataTransporters.AnnotationsImporter.noAnnotations"),
                            resources.getString("DataTransporters.AnnotationsImporter.noAnnotationsFound")
                    );
                } else {
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
                }
            }
        } else {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsImporter.importAnnotations"),
                    resources.getString("DataTransporters.AnnotationsImporter.notFromOMERO")
            );
        }
    }
}
