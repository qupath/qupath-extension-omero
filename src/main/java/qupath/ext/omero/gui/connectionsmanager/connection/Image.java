package qupath.ext.omero.gui.connectionsmanager.connection;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.net.URI;
import java.util.ResourceBundle;

/**
 * Label showing whether an image is accessible.
 */
class Image extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(Image.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    @FXML
    private Label name;
    @FXML
    private Canvas thumbnail;

    /**
     * Creates an Image label.
     *
     * @param client the client owning this image
     * @param imageUri the URI of the image
     * @throws IOException if an error occurs while creating the window
     */
    public Image(WebClient client, URI imageUri) throws IOException {
        UiUtilities.loadFXML(this, Image.class.getResource("image.fxml"));

        var imageID = WebUtilities.parseEntityId(imageUri);
        if (imageID.isPresent()) {
            client.getApisHandler().getImage(imageID.getAsLong())
                    .exceptionally(error -> {
                        logger.error(String.format("Error when retrieving image of ID %d", imageID.getAsLong()), error);
                        return null;
                    })
                    .thenAccept(image -> Platform.runLater(() ->
                        name.setText(image.getLabel())
                    ));

            client.getApisHandler().getThumbnail(imageID.getAsLong(), (int) thumbnail.getWidth())
                    .exceptionally(error -> {
                        logger.error("Error when retrieving thumbnail", error);
                        return null;
                    }).thenAccept(thumbnail -> Platform.runLater(() -> {
                        if (thumbnail != null) {
                            UiUtilities.paintBufferedImageOnCanvas(thumbnail, this.thumbnail);
                        }
                    }));
        }

        RequestSender.isLinkReachableWithGet(imageUri, 403).handle((v, error) -> {
            if (error == null) {
                setStatus(imageUri.toString(), true);
            } else {
                logger.debug(String.format("Cannot reach %s", imageUri), error);
                setStatus(resources.getString("ConnectionsManager.Image.unreachableImage"), false);
            }
            return null;
        });
    }

    private void setStatus(String text, boolean isActive) {
        Platform.runLater(() -> {
            name.setTooltip(new Tooltip(text));
            name.setGraphic(UiUtilities.createStateNode(isActive));
        });
    }
}
