package qupath.ext.omero.gui.datatransporters.importers;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.annotations.MapAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.Pair;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.commonentities.SimpleServerEntity;
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
        logger.debug("Creating KVP importer for {}", quPath);
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
        logger.debug("Attempting to import KVP from OMERO");

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
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        logger.debug("Getting annotations for image with ID {}", omeroImageServer.getId());
        omeroImageServer.getClient().getApisHandler().getAnnotations(new SimpleServerEntity(
                SimpleServerEntity.EntityType.IMAGE,
                omeroImageServer.getId()
        )).whenComplete((annotations, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (annotations == null) {
                logger.error("Cannot get annotations of image with ID {}. Cannot import KVP", omeroImageServer.getId(), error);

                new Dialogs.Builder()
                        .alertType(Alert.AlertType.ERROR)
                        .title(resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"))
                        .content(new Label(resources.getString("DataTransporters.KeyValuesImporter.couldNotRetrieveKeyValues")))
                        .owner(quPath.getStage())
                        .show();
                return;
            }
            logger.debug("Got annotations {} for image with ID {}", annotations, omeroImageServer.getId());

            List<MapAnnotation> mapAnnotations = annotations.stream()
                    .filter(MapAnnotation.class::isInstance)
                    .map(MapAnnotation.class::cast)
                    .toList();

            if (mapAnnotations.isEmpty()) {
                logger.debug("No annotations of {} are map annotations. Not importing KVP", annotations);
                new Dialogs.Builder()
                        .alertType(Alert.AlertType.WARNING)
                        .title(resources.getString("DataTransporters.KeyValuesImporter.importKeyValues"))
                        .content(new Label(resources.getString("DataTransporters.KeyValuesImporter.noKeyValuePairFound")))
                        .owner(quPath.getStage())
                        .show();
                return;
            }

            ImportKeyValuePairsForm importKeyValuePairsForm;
            try {
                importKeyValuePairsForm = new ImportKeyValuePairsForm(mapAnnotations.stream()
                        .map(Annotation::getNamespace)
                        .flatMap(Optional::stream)
                        .distinct()
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
                logger.debug("Importing KVP dialog not confirmed. Not importing KVP");
                return;
            }

            setKeyValuePairs(mapAnnotations, importKeyValuePairsForm.getSelectedNamespaces(), importKeyValuePairsForm.getSelectedChoice(), project);
        }));
    }

    @Override
    public String toString() {
        return String.format("KVP importer for %s", quPath);
    }

    private void setKeyValuePairs(List<MapAnnotation> annotations, List<Namespace> selectedNamespaces, ImportKeyValuePairsForm.Choice selectedChoice, Project<BufferedImage> project) {
        List<Pair> keyValues = annotations.stream()
                .filter(mapAnnotation -> mapAnnotation.getNamespace().isPresent() &&
                        selectedNamespaces.contains(mapAnnotation.getNamespace().get())
                )
                .map(MapAnnotation::getPairs)
                .flatMap(List::stream)
                .toList();
        logger.debug("{} were filtered to {} to only contain the following namespaces: {}", annotations, keyValues, selectedNamespaces);

        if (keyValues.isEmpty()) {
            logger.debug("No KVP found. Not importing KVP");
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
            logger.debug("Deleting metadata of current image {}", projectEntry);
            projectEntry.getMetadata().clear();
        }

        logger.debug("Adding {} to {}", keyValues, projectEntry);
        List<Pair> keyValuesWritten = new ArrayList<>();
        List<Pair> keyValuesNotWrittenBecauseDuplicate = new ArrayList<>();
        for (Pair pair : keyValues) {
            if (selectedChoice.equals(ImportKeyValuePairsForm.Choice.KEEP_EXISTING) && projectEntry.getMetadata().containsKey(pair.key())) {
                keyValuesNotWrittenBecauseDuplicate.add(pair);
                logger.debug("KVP {} not added to metadata of current image because it already exists", pair);
            } else if (keyValuesWritten.stream().map(Pair::key).anyMatch(key -> pair.key().equals(key))) {
                keyValuesNotWrittenBecauseDuplicate.add(pair);
                logger.debug("KVP {} not added to metadata of current image because it is a duplicate of an already added pair", pair);
            } else {
                keyValuesWritten.add(pair);
                projectEntry.getMetadata().put(pair.key(), pair.value());
                logger.debug("KVP {} added to metadata of current image", pair);
            }
        }
        logger.debug("KVP {} added to {}, and {} not added because they are duplicates", keyValuesWritten, projectEntry, keyValuesNotWrittenBecauseDuplicate);

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
                                    resources.getString("DataTransporters.KeyValuesImporter.noKeyValuesAdded") :
                                    MessageFormat.format(
                                            resources.getString("DataTransporters.KeyValuesImporter.followingKeyValuesAdded"),
                                            keyValuesWritten.stream()
                                                    .map(pair -> String.format("[%s: %s]", pair.key(), pair.value()))
                                                    .collect(Collectors.joining(", "))
                                    ),
                            MessageFormat.format(
                                    resources.getString("DataTransporters.KeyValuesImporter.followingKeyValuesNotAdded"),
                                    keyValuesNotWrittenBecauseDuplicate.stream()
                                            .map(pair -> String.format("[%s: %s]", pair.key(), pair.value()))
                                            .collect(Collectors.joining(", "))
                            )
                    )))
                    .owner(quPath.getStage())
                    .show();
        }
    }
}
