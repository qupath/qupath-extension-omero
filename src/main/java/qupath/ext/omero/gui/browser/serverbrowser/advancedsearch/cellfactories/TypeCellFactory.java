package qupath.ext.omero.gui.browser.serverbrowser.advancedsearch.cellfactories;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.entities.repositoryentities.OrphanedFolder;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchResult;
import qupath.ext.omero.gui.UiUtilities;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * Cell factory that displays an image representing the search result in the cell and in a tooltip.
 */
public class TypeCellFactory extends TableCell<SearchResult, SearchResult> {

    private static final Logger logger = LoggerFactory.getLogger(TypeCellFactory.class);
    private static final List<Class<? extends RepositoryEntity>> ACCEPTED_ICONS_TYPES = List.of(
            OrphanedFolder.class,
            Project.class,
            Dataset.class,
            Image.class,
            Screen.class,
            Plate.class,
            PlateAcquisition.class
    );
    private final WebClient client;

    public TypeCellFactory(WebClient client) {
        this.client = client;
    }

    @Override
    protected void updateItem(SearchResult item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            hide();
        } else {
            client.getApisHandler().getThumbnail(item.getId())
                    .exceptionally(error -> {
                        logger.error("Error when retrieving thumbnail", error);
                        return null;
                    })
                    .thenAccept(thumbnail -> Platform.runLater(() -> {
                        if (thumbnail == null) {
                            setIcon(item);
                        } else {
                            show(item, thumbnail);
                        }
                    }));
        }
    }

    private void show(SearchResult item, BufferedImage icon) {
        Canvas canvas = new Canvas(icon.getWidth(), icon.getHeight());
        var writableImage = UiUtilities.paintBufferedImageOnCanvas(icon, canvas);

        Tooltip tooltip = new Tooltip();
        if (item.getType().isPresent() && item.getType().get().equals(Image.class)) {
            ImageView imageView = new ImageView(writableImage);
            imageView.setFitHeight(250);
            imageView.setPreserveRatio(true);
            tooltip.setGraphic(imageView);
        } else {
            tooltip.setText(item.getName());
        }

        setTooltip(tooltip);
        setGraphic(canvas);
    }

    private void setIcon(SearchResult item) {
        Optional<Class<? extends RepositoryEntity>> type = item.getType();

        if (type.isPresent() && ACCEPTED_ICONS_TYPES.contains(type.get())) {
            client.getApisHandler().getOmeroIcon(type.get())
                    .exceptionally(error -> {
                        logger.error("Error while retrieving icon", error);
                        return null;
                    })
                    .thenAccept(icon -> Platform.runLater(() -> {
                        if (icon == null) {
                            hide();
                        } else {
                            show(item, icon);
                        }
                    }));
        } else {
            hide();
        }
    }

    private void hide() {
        setTooltip(null);
        setGraphic(null);
    }
}
