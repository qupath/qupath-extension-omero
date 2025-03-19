package qupath.ext.omero.gui;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.core.entities.annotations.MapAnnotation;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.ext.omero.core.imageserver.OmeroImageServerBuilder;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

/**
 * Utility methods related to the user interface.
 */
public class UiUtilities {

    private static final Logger logger = LoggerFactory.getLogger(UiUtilities.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final String DATASET_ID_LABEL = "dataset-id";
    private static final String DATASET_NAME_LABEL = "dataset-name";

    private UiUtilities() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * @return whether the graphical user interface is used
     */
    public static boolean usingGUI() {
        return QuPathGUI.getInstance() != null;
    }

    /**
     * Creates a Label whose text is selectable with the cursor.
     *
     * @param text the text to display in the label
     * @return a Label that can be selected
     */
    public static Label createSelectableLabel(String text) {
        Label label = new Label(text);

        StackPane textStack = new StackPane();
        TextField textField = new TextField(text);
        textField.setEditable(false);
        textField.setStyle(
                "-fx-background-color: transparent; -fx-background-insets: 0; -fx-background-radius: 0; -fx-padding: 0;"
        );

        // the invisible label is a hack to get the textField to size like a label.
        Label invisibleLabel = new Label();
        invisibleLabel.textProperty().bind(label.textProperty());
        invisibleLabel.setVisible(false);
        textStack.getChildren().addAll(invisibleLabel, textField);
        label.textProperty().bindBidirectional(textField.textProperty());
        label.setGraphic(textStack);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        return label;
    }

    /**
     * Loads the FXML file located at the URL and set its controller.
     *
     * @param controller the controller of the FXML file to load
     * @param url the path of the FXML file to load
     * @throws IOException if an error occurs while loading the FXML file
     */
    public static void loadFXML(Object controller, URL url) throws IOException {
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(controller);
        loader.setController(controller);
        loader.load();
    }

    /**
     * @return a node with a dot, either filled with green if {@code active} or red otherwise
     */
    public static Node createStateNode(boolean active) {
        Circle circle = new Circle(5);
        circle.getStyleClass().add(active ? "connected" : "disconnected");
        return circle;
    }

    /**
     * Paint the specified image onto the specified canvas.
     * Additionally, it returns the {@code WritableImage} for further use.
     *
     * @param image the image to paint on the canvas
     * @param canvas the canvas to paint
     * @return a copy of the input image
     */
    public static WritableImage paintBufferedImageOnCanvas(BufferedImage image, Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Color the canvas in black, in case no new image can be painted
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        WritableImage wi = SwingFXUtils.toFXImage(image, null);

        GuiTools.paintImage(canvas, wi);
        return wi;
    }

    /**
     * Attempt to open images in the QuPath viewer from the provided URIs. If a QuPath
     * project is currently opened, the images are added to the project.
     * <p>
     * If impossible (no URI provided or attempt to open multiple images
     * without using a project), an error message will appear.
     * <p>
     * If the images are added to a QuPath project, an attempt will be made to automatically
     * import the key-value pairs of the OMERO image to the metadata of the project entry, as
     * well as the ID and the name of the parent dataset.
     *
     * @param uris the URIs of the images to open
     */
    public static void openImages(List<String> uris) {
        if (uris.isEmpty()) {
            Dialogs.showErrorMessage(
                    resources.getString("UiUtilities.noImages"),
                    resources.getString("UiUtilities.noValidImagesInSelected")
            );
        } else {
            if (QuPathGUI.getInstance().getProject() == null) {
                if (uris.size() == 1) {
                    try {
                        QuPathGUI.getInstance().openImage(QuPathGUI.getInstance().getViewer(), uris.getFirst(), true, true);
                    } catch (IOException e) {
                        logger.error("Could not open image", e);
                    }
                } else {
                    Dialogs.showErrorMessage(
                            resources.getString("UiUtilities.openImages"),
                            resources.getString("UiUtilities.createProjectFirst")
                    );
                }
            } else {
                List<ProjectImageEntry<BufferedImage>> entries = ProjectCommands.promptToImportImages(
                        QuPathGUI.getInstance(),
                        ImageServerProvider.getInstalledImageServerBuilders(BufferedImage.class).stream().filter(b -> b instanceof OmeroImageServerBuilder).findAny().orElse(null),
                        uris.toArray(String[]::new)
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
        }
    }

    /**
     * Propagates changes made to a property to another property.
     * <p>
     * The listening property is updated in the UI thread.
     *
     * @param propertyToUpdate the property to update
     * @param propertyToListen the property to listen
     * @param <T> the type of the property
     */
    public static <T> void bindPropertyInUIThread(WritableValue<T> propertyToUpdate, ObservableValue<T> propertyToListen) {
        propertyToUpdate.setValue(propertyToListen.getValue());
        propertyToListen.addListener((p, o, n) -> {
            if (Platform.isFxApplicationThread()) {
                propertyToUpdate.setValue(n);
            } else {
                Platform.runLater(() -> propertyToUpdate.setValue(n));
            }
        });
    }

    /**
     * Propagates changes made to an observable set to another observable set.
     * <p>
     * The listening set is updated in the UI thread.
     *
     * @param setToUpdate the set to update
     * @param setToListen the set to listen
     * @param <T> the type of the elements of the sets
     */
    public static <T> void bindSetInUIThread(ObservableSet<T> setToUpdate, ObservableSet<T> setToListen) {
        setToUpdate.addAll(setToListen);

        setToListen.addListener((SetChangeListener<? super T>) change -> {
            if (Platform.isFxApplicationThread()) {
                if (change.wasAdded()) {
                    setToUpdate.add(change.getElementAdded());
                }
                if (change.wasRemoved()) {
                    setToUpdate.remove(change.getElementRemoved());
                }
            } else {
                Platform.runLater(() -> {
                    if (change.wasAdded()) {
                        setToUpdate.add(change.getElementAdded());
                    }
                    if (change.wasRemoved()) {
                        setToUpdate.remove(change.getElementRemoved());
                    }
                });
            }
        });
    }

    /**
     * Propagates changes made to an observable list to another observable list.
     * <p>
     * The listening list is updated in the UI thread.
     *
     * @param listToUpdate the list to update
     * @param listToListen the list to listen
     * @param <T> the type of the elements of the lists
     */
    public static <T> void bindListInUIThread(ObservableList<T> listToUpdate, ObservableList<T> listToListen) {
        listToUpdate.addAll(listToListen);

        listToListen.addListener((ListChangeListener<? super T>) change -> Platform.runLater(() -> {
            if (Platform.isFxApplicationThread()) {
                while (change.next()) {
                    if (change.wasAdded()) {
                        listToUpdate.addAll(change.getAddedSubList());
                    } else {
                        listToUpdate.removeAll(change.getRemoved());
                    }
                }
                // Change needs to be reset, otherwise calling this function several times
                // with the same listToListen parameter will work for only one of them
                change.reset();
            } else {
                Platform.runLater(() -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            listToUpdate.addAll(change.getAddedSubList());
                        } else {
                            listToUpdate.removeAll(change.getRemoved());
                        }
                    }
                    change.reset();
                });
            }
        }));
    }

    private static void importKeyValuePairsAndParentContainer(ProjectImageEntry<BufferedImage> projectEntry) {
        CompletableFuture.runAsync(() -> {
            try (ImageServer<BufferedImage> server = projectEntry.getServerBuilder().build()) {
                if (!(server instanceof OmeroImageServer omeroImageServer)) {
                    logger.debug("{} is not an OMERO image server. Skipping KVP and parent container info import", server);
                    return;
                }

                omeroImageServer.getClient().getApisHandler().getAnnotations(omeroImageServer.getId(), Image.class)
                        .whenComplete(((annotationGroup, error) -> {
                            if (error != null) {
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
                                        logger.debug("Cannot add {} to image entry metadata because the same key already exists", pair);
                                    } else {
                                        projectEntry.getMetadata().put(pair.key(), pair.value());
                                    }
                                }
                            });
                        }));

                omeroImageServer.getClient().getApisHandler().getDatasetOwningImage(omeroImageServer.getId())
                        .whenComplete((dataset, error) -> {
                            if (error != null) {
                                logger.debug(
                                        "Cannot retrieve dataset owning image with ID {}. Skipping parent container info import",
                                        omeroImageServer.getId()
                                );
                                return;
                            }

                            logger.debug("Adding dataset {} to {} metadata", dataset, projectEntry);
                            Platform.runLater(() -> {
                                projectEntry.getMetadata().put(DATASET_ID_LABEL, String.valueOf(dataset.getId()));
                                projectEntry.getMetadata().put(DATASET_NAME_LABEL, dataset.getAttributeValue(0));
                            });
                        });
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                logger.debug("Cannot create image server. Skipping KVP and parent container info import", e);
            }
        });
    }
}
