package qupath.ext.omero.gui.browser.hierarchy;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
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
    private static final List<Class<? extends RepositoryEntity>> ACCEPTED_ICONS_TYPES = List.of(
            OrphanedFolder.class,
            Project.class,
            Dataset.class,
            Image.class,
            Screen.class,
            Plate.class,
            PlateAcquisition.class
    );
    private final DoubleProperty opacityProperty = new SimpleDoubleProperty(1);
    private final ApisHandler apisHandler;

    /**
     * Creates the cell factory.
     *
     * @param apisHandler the apis handler to use when making requests
     */
    public HierarchyCellFactory(ApisHandler apisHandler) {
        this.apisHandler = apisHandler;

        opacityProperty.addListener((p, o, n) -> Platform.runLater(() -> setOpacity(n.doubleValue())));
        setOpacity(opacityProperty.get());
    }

    @Override
    public void updateItem(RepositoryEntity repositoryEntity, boolean empty) {
        super.updateItem(repositoryEntity, empty);

        setGraphic(null);
        setText(null);
        setTooltip(null);
        opacityProperty.unbind();
        opacityProperty.set(1);

        if (!empty && repositoryEntity != null) {
            Canvas iconCanvas = new Canvas(15, 15);
            setGraphic(iconCanvas);
            setIcon(repositoryEntity.getClass(), iconCanvas);

            setText(repositoryEntity.getLabel());

            Tooltip tooltip = new Tooltip();
            tooltip.setText(repositoryEntity.getLabel());

            if (repositoryEntity instanceof Image image) {
                opacityProperty.bind(Bindings.when(image.isSupported()).then(1).otherwise(0.5));

                try {
                    tooltip.setGraphic(new ImageTooltip(image, apisHandler));
                } catch (IOException e) {
                    logger.error("Error while creating image tooltip", e);
                }
            }

            setTooltip(tooltip);
        }
    }

    private void setIcon(Class<? extends RepositoryEntity> type, Canvas iconCanvas) {
        if (ACCEPTED_ICONS_TYPES.contains(type)) {
            apisHandler.getOmeroIcon(type).whenComplete((icon, error) -> Platform.runLater(() -> {
                if (icon == null) {
                    logger.error("Error while retrieving icon {}", type, error);
                } else {
                    logger.debug("Got icon {} for {}. Setting it to graphic", icon, type);
                    UiUtilities.paintBufferedImageOnCanvas(icon, iconCanvas);
                }
            }));
        } else {
            logger.warn("Can set icon for {} because it doesn't exist", type);
        }
    }
}
