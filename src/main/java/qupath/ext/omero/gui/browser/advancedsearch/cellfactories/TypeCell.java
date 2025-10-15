package qupath.ext.omero.gui.browser.advancedsearch.cellfactories;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.apis.webclient.EntityType;
import qupath.ext.omero.core.apis.webclient.search.SearchResult;
import qupath.ext.omero.core.apis.webclient.search.SearchResultWithParentInfo;
import qupath.ext.omero.gui.UiUtils;

import java.util.ResourceBundle;

/**
 * Cell that displays an image representing the search result in the cell and in a tooltip.
 */
public class TypeCell extends TableCell<SearchResultWithParentInfo, SearchResult> {

    private static final Logger logger = LoggerFactory.getLogger(TypeCell.class);
    private static final ResourceBundle resources = Utils.getResources();
    private static final int ICON_SIZE = 20;
    private static final int TOOLTIP_IMAGE_SIZE = 256;
    private final ApisHandler apisHandler;

    /**
     * Create the cell.
     *
     * @param apisHandler the apis handler to use when making requests
     */
    public TypeCell(ApisHandler apisHandler) {
        this.apisHandler = apisHandler;
    }

    @Override
    protected void updateItem(SearchResult item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty || item.getType().isEmpty()) {
            setTooltip(null);
            setGraphic(null);
            return;
        }

        Canvas canvas = new Canvas(ICON_SIZE, ICON_SIZE);
        setGraphic(canvas);

        Tooltip tooltip = new Tooltip();
        setTooltip(tooltip);

        EntityType type = item.getType().get();
        if (type.equals(EntityType.IMAGE)) {
            apisHandler.getThumbnail(item.id()).whenComplete((thumbnail, error) -> Platform.runLater(() -> {
                if (error == null) {
                    logger.debug("Got thumbnail {} for image with ID {}. Setting it to type cell", thumbnail, item.id());

                    WritableImage image = UiUtils.paintBufferedImageOnCanvas(thumbnail, canvas);

                    ImageView imageView = new ImageView(image);
                    imageView.setFitHeight(TOOLTIP_IMAGE_SIZE);
                    imageView.setPreserveRatio(true);
                    tooltip.setGraphic(imageView);
                } else {
                    logger.error("Error when retrieving thumbnail of {}. Cannot set graphic of type cell", item, error);

                    tooltip.setText(resources.getString("Browser.ServerBrowser.AdvancedSearch.TypeCellFactory.couldNotRetrieveIcon"));
                }
            }));
        } else {
            apisHandler.getOmeroIcon(type).whenComplete((icon, error) -> Platform.runLater(() -> {
                if (error == null) {
                    logger.debug("Got OMERO icon {} for {}. Setting it to type cell", icon, type);

                    UiUtils.paintBufferedImageOnCanvas(icon, canvas);
                    tooltip.setText(item.name());
                } else {
                    logger.error("Error while retrieving icon for {}. Cannot set graphic of type cell", type, error);

                    tooltip.setText(resources.getString("Browser.ServerBrowser.AdvancedSearch.TypeCellFactory.couldNotRetrieveIcon"));
                }
            }));
        }
    }
}
