package qupath.ext.omero.gui.browser.serverbrowser.hierarchy;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;

/**
 * <p>
 *     Cell factory of the hierarchy of a {@link javafx.scene.control.TreeView TreeView} containing
 *     {@link RepositoryEntity RepositoryEntity} elements .</p>
 * <p>
 *     It displays the name of each OMERO entity, a corresponding icon, the number of children (if any),
 *     and additional information with a tooltip.
 * </p>
 * <p>
 *     If the entity is an {@link Image Image},
 *     a complex tooltip described in {@link ImageTooltip} is used.
 * </p>
 */
public class HierarchyCellFactory extends TreeCell<RepositoryEntity> {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyCellFactory.class);
    private final DoubleProperty opacityProperty = new SimpleDoubleProperty(1);
    private final WebClient client;

    /**
     * Creates the cell factory.
     *
     * @param client  the client from which icons and additional information will be retrieved
     */
    public HierarchyCellFactory(WebClient client) {
        this.client = client;

        // opacityProperty is used because image.isSupported() may be updated from any thread
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
            setIcon(repositoryEntity.getClass());

            Tooltip tooltip = new Tooltip();

            setText(repositoryEntity.getLabel());
            tooltip.setText(repositoryEntity.getLabel());

            if (repositoryEntity instanceof Image image) {
                opacityProperty.bind(Bindings.when(image.isSupported()).then(1).otherwise(0.5));

                try {
                    tooltip.setGraphic(new ImageTooltip(image, client));
                } catch (IOException e) {
                    logger.error("Error while creating image tooltip", e);
                }
            }

            setTooltip(tooltip);
        }
    }

    private void setIcon(Class<? extends RepositoryEntity> type) {
        client.getApisHandler().getOmeroIcon(type).thenAccept(icon -> Platform.runLater(() -> {
            if (icon.isPresent()) {
                Canvas iconCanvas = new Canvas(15, 15);
                UiUtilities.paintBufferedImageOnCanvas(icon.get(), iconCanvas);
                setGraphic(iconCanvas);
            }
        }));
    }
}
