package qupath.ext.omero.gui.datatransporters.importers;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.annotations.Annotation;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.ImportKeyValuePairsForm;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Import key-value pairs from an OMERO server to the currently opened image.
 * <p>
 * Since key-value pairs are only defined in projects, a project must be opened.
 * <p>
 * This class uses a {@link ImportKeyValuePairsForm} to prompt the user for parameters.
 */
public class KeyValuesImporter implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(KeyValuesImporter.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final QuPathGUI quPath;

    /**
     * Create the key-value importer.
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
        Project<BufferedImage> project = quPath.getProject();

        if (quPath.getViewer() == null || !(quPath.getViewer().getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                    resources.getString("DataTransporters.KeyValuesImporter.notFromOMERO")
            );
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.KeyValuesImporter.gettingKeyValuePairs")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window");
            return;
        }
        waitingWindow.show();

        omeroImageServer.getClient().getApisHandler().getAnnotations(omeroImageServer.getId(), Image.class)
                .exceptionally(error -> {
                    logger.error("Cannot get annotations of image with ID {}", omeroImageServer.getId(), error);
                    return null;
                }).thenAccept(annotationGroup -> Platform.runLater(() -> {
                    waitingWindow.close();

                    if (annotationGroup == null) {
                        new Dialogs.Builder()
                                .alertType(Alert.AlertType.ERROR)
                                .title(resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"))
                                .content(new Label(resources.getString("DataTransporters.KeyValuesImporter.couldNotRetrieveKeyValues")))
                                .owner(quPath.getStage())
                                .show();
                        return;
                    }

                    logger.debug("Got annotations {} for image with ID {}", annotationGroup, omeroImageServer.getId());

                    ImportKeyValuePairsForm importKeyValuePairsForm;
                    try {
                        importKeyValuePairsForm = new ImportKeyValuePairsForm(annotationGroup.getAnnotationsOfClass(MapAnnotation.class)
                                .stream()
                                .map(Annotation::getNamespace)
                                .flatMap(Optional::stream)
                                .toList()
                        );
                    } catch (IOException e) {
                        logger.error("Error when creating the import key-values form", e);
                        return;
                    }

                    boolean confirmed = Dialogs.showConfirmDialog(
                            resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                            importKeyValuePairsForm
                    );
                    if (!confirmed) {
                        return;
                    }

                    setKeyValuePairs(annotationGroup, importKeyValuePairsForm.getSelectedNamespaces(), importKeyValuePairsForm.getSelectedChoice(), project);
                }));
    }

    private void setKeyValuePairs(AnnotationGroup annotationGroup, List<String> selectedNamespaces, ImportKeyValuePairsForm.Choice selectedChoice, Project<BufferedImage> project) {
        List<MapAnnotation.Pair> keyValues = annotationGroup.getAnnotationsOfClass(MapAnnotation.class).stream()
                .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() &&
                        selectedNamespaces.contains(mapAnnotation.getNamespace().get())
                )
                .map(MapAnnotation::getPairs)
                .flatMap(List::stream)
                .toList();
        logger.debug("{} were filtered to {} to only contain the following namespaces: {}", annotationGroup, keyValues, selectedNamespaces);

        if (keyValues.isEmpty()) {
            new Dialogs.Builder()
                    .alertType(Alert.AlertType.WARNING)
                    .title(resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"))
                    .content(new Label(resources.getString("DataTransporters.KeyValuesImporter.noKeyValuePairFound")))
                    .owner(quPath.getStage())
                    .show();
            return;
        }

        ProjectImageEntry<BufferedImage> projectEntry = project.getEntry(quPath.getImageData());
        if (selectedChoice.equals(ImportKeyValuePairsForm.Choice.DELETE_ALL)) {
            logger.debug("Deleting metadata of current image");
            projectEntry.getMetadata().clear();
        }

        List<MapAnnotation.Pair> keyValuesWritten = new ArrayList<>();
        List<MapAnnotation.Pair> keyValuesNotWrittenBecauseDuplicate = new ArrayList<>();
        for (MapAnnotation.Pair pair : keyValues) {
            if (selectedChoice.equals(ImportKeyValuePairsForm.Choice.KEEP_EXISTING) && projectEntry.getMetadata().containsKey(pair.key())) {
                keyValuesNotWrittenBecauseDuplicate.add(pair);
                logger.debug("Key-value pair {} not added to metadata of current image because it already exists", pair);
            } else if (keyValuesWritten.stream().map(MapAnnotation.Pair::key).anyMatch(key -> pair.key().equals(key))) {
                keyValuesNotWrittenBecauseDuplicate.add(pair);
                logger.debug("Key-value pair {} not added to metadata of current image because it is a duplicate of an already added pair", pair);
            } else {
                keyValuesWritten.add(pair);
                projectEntry.getMetadata().put(pair.key(), pair.value());
                logger.debug("Key-value pair {} added to metadata of current image", pair);
            }
        }

        if (keyValuesNotWrittenBecauseDuplicate.isEmpty()) {
            Dialogs.showInfoNotification(
                    resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"),
                    resources.getString("DataTransporters.KeyValuesImporter.keyValuesImported")
            );
        } else {
            new Dialogs.Builder()
                    .alertType(Alert.AlertType.INFORMATION)
                    .title(resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"))
                    .content(new Label(String.format(
                            "%s\n\n%s",
                            keyValuesWritten.isEmpty() ?
                                    resources.getString("DataTransporters.KeyValuesImporter.nokeyValuesAdded") :
                                    MessageFormat.format(
                                            resources.getString("DataTransporters.KeyValuesImporter.followingKeyValuesAdded"),
                                            keyValuesWritten.stream()
                                                    .map(pair -> String.format("[%s: %s]", pair.key(), pair.value()))
                                                    .collect(Collectors.joining(", "))
                                    ),
                            MessageFormat.format(
                                    resources.getString("DataTransporters.KeyValuesImporter.followingKeyValuesNotAdded"),
                                    keyValuesNotWrittenBecauseDuplicate.stream()
                                            .map(pair -> String.format("[%s: %s])", pair.key(), pair.value()))
                                            .collect(Collectors.joining(", "))
                            )
                    )))
                    .owner(quPath.getStage())
                    .show();
        }
    }
}
