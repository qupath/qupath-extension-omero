package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.json.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;
import qupath.ext.omero.core.apis.webclient.EntityType;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtilities;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Cell of the hierarchy of a {@link TreeView TreeView} containing
 * {@link RepositoryEntity RepositoryEntity} elements .</p>
 * <p>
 * It displays the name of each OMERO entity, a corresponding icon, the number of children (if any),
 * and additional information with a tooltip.
 * <p>
 * If the entity is an {@link Image Image}, a complex tooltip described in {@link ImageTooltip} is used.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
public class HierarchyCell extends TreeCell<RepositoryEntity> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyCell.class);
    private static final double SUPPORTED_IMAGE_OPACITY = 1;
    private static final double UNSUPPORTED_IMAGE_OPACITY = 0.5;
    private final Client client;
    private ChangeListener<? super PixelApi> selectedPixelApiListener;

    /**
     * Creates the cell.
     *
     * @param client the client to use when making requests
     */
    public HierarchyCell(Client client) {
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
            setIcon(repositoryEntity, iconCanvas);

            setText(repositoryEntity.getLabel());

            Tooltip tooltip = new Tooltip();
            tooltip.setText(repositoryEntity.getLabel());

            if (repositoryEntity instanceof Image image) {
                selectedPixelApiListener = (p, o, n) -> Platform.runLater(() ->
                        setOpacity(image.isSupported(n) ? SUPPORTED_IMAGE_OPACITY : UNSUPPORTED_IMAGE_OPACITY)
                );
                selectedPixelApiListener.changed(client.getSelectedPixelApi(), null, client.getSelectedPixelApi().getValue());
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

    @Override
    public void close() {
        if (selectedPixelApiListener != null) {
            client.getSelectedPixelApi().removeListener(selectedPixelApiListener);
        }

        if (getTooltip() != null && getTooltip().getGraphic() != null && getTooltip().getGraphic() instanceof ImageTooltip imageTooltip) {
            imageTooltip.close();
        }
    }

    private void setIcon(RepositoryEntity repositoryEntity, Canvas iconCanvas) {
        CompletableFuture<BufferedImage> request = null;
        if (repositoryEntity instanceof OrphanedFolder) {
            request = client.getApisHandler().getOrphanedFolderIcon();
        } else {
            EntityType type = switch (repositoryEntity) {
                case Image ignored -> EntityType.IMAGE;
                case Dataset ignored -> EntityType.DATASET;
                case Project ignored -> EntityType.PROJECT;
                case Well ignored -> EntityType.WELL;
                case PlateAcquisition ignored -> EntityType.PLATE_ACQUISITION;
                case Plate ignored -> EntityType.PLATE;
                case Screen ignored -> EntityType.SCREEN;
                default -> null;
            };

            if (type != null) {
                request = client.getApisHandler().getOmeroIcon(type);
            }
        }

        if (request == null) {
            logger.warn("Can set icon for {} because it is not supported", repositoryEntity);
        } else {
            request.whenComplete((icon, error) -> Platform.runLater(() -> {
                if (icon == null) {
                    logger.error("Error while retrieving icon of {}. Cannot set graphic of hierarchy cell", repositoryEntity, error);
                } else {
                    logger.trace("Got icon {} for {}. Setting it to graphic", icon, repositoryEntity);
                    UiUtilities.paintBufferedImageOnCanvas(icon, iconCanvas);
                }
            }));
        }
    }
}
