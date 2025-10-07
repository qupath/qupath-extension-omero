package qupath.ext.omero.gui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.apis.webclient.Namespace;
import qupath.ext.omero.core.apis.webclient.annotations.MapAnnotation;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.core.imageserver.OmeroImageServerBuilder;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.panes.ServerSelector;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for opening images from the user interface.
 */
public class ImageOpener {

    private static final Logger logger = LoggerFactory.getLogger(ImageOpener.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String ID_LABEL = "%s-id";
    private static final String NAME_LABEL = "%s-name";

    private ImageOpener() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Attempt to open images from the provided URIs in the current QuPath viewer or current QuPath project.
     * <ul>
     *     <li>If a QuPath project is currently opened, the provided images are added to the project.</li>
     *     <li>
     *         If no QuPath project is currently opened and only one image is provided, the provided image is opened
     *         in the current QuPath viewer.
     *     </li>
     *     <li>
     *         If no QuPath project is currently opened and several images are provided, the user is prompted to select
     *         one image, and it is opened in the current QuPath viewer.
     *     </li>
     * </ul>
     * Note that the provided URIs don't have to point to images, they can point to datasets for example, in which case all
     * children images of the dataset will be imported (same for other entities, see
     * {@link ApisHandler#getImageUrisFromEntityURI(URI)})
     * <p>
     * If the images are added to a QuPath project, an attempt will be made to automatically
     * import the key-value pairs of the OMERO image to the metadata of the project entry, as
     * well as the ID and the name of the parent dataset.
     * <p>
     * Part of this function is asynchronous, so it might return before the images are actually open.
     *
     * @param uris the URIs of the images to open (that can point to other entities, see above)
     * @param apisHandler the APIs handler to use when making requests
     */
    public static void openImageFromUris(List<String> uris, ApisHandler apisHandler) {
        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    QuPathGUI.getInstance().getStage(),
                    MessageFormat.format(resources.getString("ImageOpener.opening"), uris)
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        logger.debug("Getting image URIs contained in {}", uris);
        CompletableFuture.supplyAsync(() -> uris.stream()
                .map(URI::create)
                .map(apisHandler::getImageUrisFromEntityURI)
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .distinct()
                .toList()
        ).whenComplete((imageUris, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (imageUris == null) {
                logger.error("Cannot retrieve image URIs contained in {}", uris, error);
                Dialogs.showErrorMessage(
                        resources.getString("ImageOpener.imageOpening"),
                        MessageFormat.format(resources.getString("ImageOpener.errorWhileRetrievingImage"), uris)
                );
                return;
            }

            if (imageUris.isEmpty()) {
                logger.debug("No image found in {}", uris);

                Dialogs.showErrorMessage(
                        resources.getString("ImageOpener.imageOpening"),
                        MessageFormat.format(resources.getString("ImageOpener.noImageFound"), uris)
                );
                return;
            }

            logger.debug("Got image URIs {} contained in {}", imageUris, uris);

            if (QuPathGUI.getInstance().getProject() == null) {
                openImagesInCurrentViewer(imageUris);
            } else {
                openImagesInCurrentProject(imageUris);
            }
        }));
    }

    private static void openImagesInCurrentViewer(List<URI> uris) {
        if (uris.size() == 1) {
            logger.debug("No project detected and one URI {} provided: opening image in current viewer", uris);
            try {
                QuPathGUI.getInstance().openImage(QuPathGUI.getInstance().getViewer(), uris.getFirst().toString(), true, true);
            } catch (IOException e) {
                logger.error("Could not open image {}", uris.getFirst(), e);
            }
            return;
        }

        logger.debug("No project detected and more than one URI {} provided: prompting which image to open", uris);

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    QuPathGUI.getInstance().getStage(),
                    MessageFormat.format(resources.getString("ImageOpener.fetchingInformation"), uris)
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        Optional<OmeroImageServerBuilder> omeroImageServerBuilder = ImageServerProvider.getInstalledImageServerBuilders(BufferedImage.class).stream()
                .filter(serverBuilder -> serverBuilder instanceof OmeroImageServerBuilder)
                .map(serverBuilder -> (OmeroImageServerBuilder) serverBuilder)
                .findAny();
        if (omeroImageServerBuilder.isEmpty()) {
            logger.error("Cannot find OMERO image server builder");
            showErrorMessage(uris);
            return;
        }

        logger.debug("Creating builders for {}", uris);
        CompletableFuture.supplyAsync(() -> uris.stream()
                .map(uri -> omeroImageServerBuilder.get().buildServer(uri))
                .map(ImageServer::getBuilder)
                .toList()
        ).whenComplete((builders, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (builders == null) {
                logger.error("Cannot create builders of {}", uris, error);
                showErrorMessage(uris);
                return;
            }

            try (ImageServer<BufferedImage> server = ServerSelector.createFromBuilders(builders).promptToSelectImage("Open", false)) {
                if (server == null) {
                    logger.debug("No image selected. Don't opening anything");
                    return;
                }

                Collection<URI> serverUris = server.getURIs();
                if (serverUris.isEmpty()) {
                    logger.error("Image {} selected but no URI present. Don't opening anything", server);
                    showErrorMessage(uris);
                    return;
                }

                String uriToOpen = server.getURIs().iterator().next().toString();
                logger.debug("Image {} selected. Opening {}", server, uriToOpen);
                QuPathGUI.getInstance().openImage(QuPathGUI.getInstance().getViewer(), uriToOpen, true, true);
            } catch (Exception e) {
                logger.error("Cannot create image server from the one of {} selected by user", uris, e);

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                showErrorMessage(uris);
            }
        }));
    }

    private static void showErrorMessage(List<URI> uris) {
        Dialogs.showErrorMessage(
                resources.getString("ImageOpener.imageOpening"),
                MessageFormat.format(resources.getString("ImageOpener.errorWhileRetrievingImage"), uris)
        );
    }

    private static void openImagesInCurrentProject(List<URI> uris) {
        logger.debug("Project currently open: adding {} to it", uris);

        List<ProjectImageEntry<BufferedImage>> entries = ProjectCommands.promptToImportImages(
                QuPathGUI.getInstance(),
                ImageServerProvider.getInstalledImageServerBuilders(BufferedImage.class).stream()
                        .filter(b -> b instanceof OmeroImageServerBuilder)
                        .findAny()
                        .orElse(null),
                uris.stream().map(URI::toString).toArray(String[]::new)
        );

        if (OmeroExtension.getAutoKvpImportProperty().get()) {
            logger.debug("Automatically importing key-value pairs and parent dataset information");

            for (ProjectImageEntry<BufferedImage> entry: entries) {
                importKeyValuePairsAndParentContainer(entry);
            }
        } else {
            logger.debug("Skipping automatic import of key-value pairs and parent dataset information");
        }
    }

    private static void importKeyValuePairsAndParentContainer(ProjectImageEntry<BufferedImage> projectEntry) {
        CompletableFuture.runAsync(() -> {
            logger.debug("Importing KVP and parent container ID and name to {}", projectEntry);

            try (ImageServer<BufferedImage> server = projectEntry.getServerBuilder().build()) {
                if (!(server instanceof OmeroImageServer omeroImageServer)) {
                    logger.debug("{} is not an OMERO image server. Skipping KVP and parent container info import", server);
                    return;
                }

                importKvp(omeroImageServer, projectEntry);
                importParentIdsAndNames(omeroImageServer, projectEntry);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                logger.debug("Cannot create image server. Skipping KVP and parent container info import for {}", projectEntry, e);
            }
        });
    }

    private static void importKvp(OmeroImageServer omeroImageServer, ProjectImageEntry<BufferedImage> projectEntry) {
        logger.debug("Getting annotations of image with ID {}", omeroImageServer.getId());
        omeroImageServer.getClient().getApisHandler().getAnnotations(omeroImageServer.getId(), Image.class).whenComplete(((annotationGroup, error) -> {
            if (annotationGroup == null) {
                logger.debug(
                        "Cannot retrieve annotations of image with ID {}. Skipping key-value pairs import",
                        omeroImageServer.getId()
                );
                return;
            }

            List<MapAnnotation.Pair> keyValues = annotationGroup.getAnnotationsOfClass(MapAnnotation.class).stream()
                    .filter(mapAnnotation ->
                            mapAnnotation.getNamespace().isPresent() && mapAnnotation.getNamespace().get().equals(Namespace.getDefaultNamespace())
                    )
                    .map(MapAnnotation::getPairs)
                    .flatMap(List::stream)
                    .toList();
            Platform.runLater(() -> {
                logger.debug("Adding key-value pairs {} to {} metadata", keyValues, projectEntry);

                for (MapAnnotation.Pair pair : keyValues) {
                    if (projectEntry.getMetadata().containsKey(pair.key())) {
                        logger.debug("Cannot add {} to {} because the same key already exists", pair, projectEntry);
                    } else {
                        projectEntry.getMetadata().put(pair.key(), pair.value());
                        logger.debug("{} added to {}", pair, projectEntry);
                    }
                }
            });
        }));
    }

    private static void importParentIdsAndNames(OmeroImageServer omeroImageServer, ProjectImageEntry<BufferedImage> projectEntry) {
        logger.debug("Getting parent information of image with ID {}", omeroImageServer.getId());
        omeroImageServer.getClient().getApisHandler().getParentsOfImage(omeroImageServer.getId()).whenComplete((parents, error) -> {
            if (parents == null) {
                logger.debug(
                        "Cannot retrieve parents of image with ID {}. Skipping parent container info import",
                        omeroImageServer.getId()
                );
                return;
            }

            Platform.runLater(() -> {
                logger.debug("Adding IDs and names of {} to {} metadata", parents, projectEntry);

                for (ServerEntity parent: parents) {
                    String type;
                    if (parent.getClass().equals(Screen.class)) {
                        type = "screen";
                    } else if (parent.getClass().equals(Plate.class)) {
                        type = "plate";
                    } else if (parent.getClass().equals(PlateAcquisition.class)) {
                        type = "run";
                    } else if (parent.getClass().equals(Well.class)) {
                        type = "well";
                    } else if (parent.getClass().equals(Project.class)) {
                        type = "project";
                    } else if (parent.getClass().equals(Dataset.class)) {
                        type = "dataset";
                    } else {
                        throw new IllegalArgumentException(String.format(
                                "The provided parent %s was not recognized",
                                parent
                        ));
                    }

                    projectEntry.getMetadata().put(String.format(ID_LABEL, type), String.valueOf(parent.getId()));
                    projectEntry.getMetadata().put(String.format(NAME_LABEL, type), parent.getAttributeValue(0));
                }
            });
        });
    }
}
