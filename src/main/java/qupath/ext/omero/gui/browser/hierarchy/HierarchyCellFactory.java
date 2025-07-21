package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.List;

/**
 * Cell factory of the hierarchy of a {@link javafx.scene.control.TreeView TreeView} containing
 * {@link RepositoryEntity RepositoryEntity} elements .</p>
 * <p>
 * It displays the name of each OMERO entity, a corresponding icon, the number of children (if any),
 * and additional information with a tooltip.
 * <p>
 * If the entity is an {@link Image Image}, a complex tooltip described in {@link ImageTooltip} is used.
 */
public class HierarchyCellFactory extends TreeCell<RepositoryEntity> {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyCellFactory.class);
    private static final double SUPPORTED_IMAGE_OPACITY = 1;
    private static final double UNSUPPORTED_IMAGE_OPACITY = 0.5;
    private static final List<Class<? extends RepositoryEntity>> ACCEPTED_ICONS_TYPES = List.of(
            OrphanedFolder.class,
            Project.class,
            Dataset.class,
            Image.class,
            Screen.class,
            Plate.class,
            PlateAcquisition.class,
            Well.class
    );
    private final Client client;
    private ChangeListener<? super PixelApi> selectedPixelApiListener;

    /**
     * Creates the cell factory.
     *
     * @param client the client to use when making requests
     */
    public HierarchyCellFactory(Client client) {
        this.client = client;
    }

    @Override
    public void updateItem(RepositoryEntity repositoryEntity, boolean empty) {
        super.updateItem(repositoryEntity, empty);

        if (selectedPixelApiListener != null) {
            client.getSelectedPixelApi().removeListener(selectedPixelApiListener);
            selectedPixelApiListener = null;
        }
        if (getTooltip() != null && getTooltip().getGraphic() != null && getTooltip().getGraphic() instanceof ImageTooltip imageTooltip) {
            imageTooltip.close();
        }
        setGraphic(null);
        setText(null);
        setTooltip(null);
        setOpacity(1);

        if (!empty && repositoryEntity != null) {
            Canvas iconCanvas = new Canvas(15, 15);
            setGraphic(iconCanvas);
            setIcon(repositoryEntity.getClass(), iconCanvas);

            setText(repositoryEntity.getLabel());

            Tooltip tooltip = new Tooltip();
            tooltip.setText(repositoryEntity.getLabel());

            if (repositoryEntity instanceof Image image) {
                selectedPixelApiListener = (p, o, n) -> Platform.runLater(() ->
                        setOpacity(image.isSupported(n) ? SUPPORTED_IMAGE_OPACITY : UNSUPPORTED_IMAGE_OPACITY)
                );
                selectedPixelApiListener.changed(client.getSelectedPixelApi(), null, client.getSelectedPixelApi().get());
                client.getSelectedPixelApi().addListener(selectedPixelApiListener);

                try {
                    tooltip.setGraphic(new ImageTooltip(image, client));
                } catch (IOException e) {
                    logger.error("Error while creating image tooltip of {}", repositoryEntity, e);
                }
            }

            setTooltip(tooltip);
        }
    }

    private void setIcon(Class<? extends RepositoryEntity> type, Canvas iconCanvas) {
        if (ACCEPTED_ICONS_TYPES.contains(type)) {
            client.getApisHandler().getOmeroIcon(type).whenComplete((icon, error) -> Platform.runLater(() -> {
                if (icon == null) {
                    logger.error("Error while retrieving icon of {}. Cannot set graphic of hierarchy cell", type, error);
                } else {
                    logger.trace("Got icon {} for {}. Setting it to graphic", icon, type);
                    UiUtilities.paintBufferedImageOnCanvas(icon, iconCanvas);
                }
            }));
        } else {
            logger.warn("Can set icon for {} because it is not supported", type);
        }
    }
}
