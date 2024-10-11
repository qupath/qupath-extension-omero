package qupath.ext.omero.gui.browser.serverbrowser;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.net.URI;

/**
 * <p>
 *     The model of the browser. It contains properties and list which determine
 *     parts of the UI rendered by the browser.
 * </p>
 * <p>
 *     Part of the goal of this class is to act as an intermediate between a browser and a
 *     {@link WebClient WebClient}.
 *     Properties and lists of a WebClient can be updated from any thread but the browser can
 *     only be accessed from the UI thread, so this class propagates changes made to these elements
 *     from any thread to the UI thread.
 * </p>
 */
public class BrowserModel {

    private static final Logger logger = LoggerFactory.getLogger(BrowserModel.class);
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty();
    private final BooleanProperty areOrphanedImagesLoading = new SimpleBooleanProperty(false);
    private final IntegerProperty numberOfOrphanedImages = new SimpleIntegerProperty();
    private final IntegerProperty numberOfOrphanedImagesLoaded = new SimpleIntegerProperty(0);
    private final IntegerProperty numberOfThumbnailsLoading = new SimpleIntegerProperty(0);
    private final ObjectProperty<PixelAPI> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableList<PixelAPI> availablePixelAPIs = FXCollections.observableArrayList();
    private final ObservableList<PixelAPI> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelAPIs);
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final ObjectProperty<Owner> selectedOwner;
    private final ObjectProperty<Group> selectedGroup;
    private static final ObservableList<WebClient> clients = FXCollections.observableArrayList();
    private static final ObservableList<WebClient> clientsImmutable = FXCollections.unmodifiableObservableList(clients);

    static {
        UiUtilities.bindListInUIThread(clients, WebClients.getClients());
    }

    /**
     * Creates a new browser model
     *
     * @param client the client whose properties and lists should be listened
     */
    public BrowserModel(WebClient client) {
        UiUtilities.bindPropertyInUIThread(numberOfEntitiesLoading, client.getApisHandler().getNumberOfEntitiesLoading());
        UiUtilities.bindPropertyInUIThread(areOrphanedImagesLoading, client.getApisHandler().areOrphanedImagesLoading());
        UiUtilities.bindPropertyInUIThread(numberOfOrphanedImagesLoaded, client.getApisHandler().getNumberOfOrphanedImagesLoaded());
        UiUtilities.bindPropertyInUIThread(numberOfThumbnailsLoading, client.getApisHandler().getNumberOfThumbnailsLoading());
        UiUtilities.bindPropertyInUIThread(selectedPixelAPI, client.getSelectedPixelAPI());

        UiUtilities.bindListInUIThread(availablePixelAPIs, client.getAvailablePixelAPIs());

        UiUtilities.bindSetInUIThread(openedImagesURIs, client.getOpenedImagesURIs());

        selectedOwner = new SimpleObjectProperty<>(client.getServer().getConnectedOwner());
        selectedGroup = new SimpleObjectProperty<>(client.getServer().getDefaultGroup());

        client.getApisHandler().getOrphanedImagesIds()
                .handle((orphanedImageIds, error) -> {
                    if (error == null) {
                        return orphanedImageIds.size();
                    } else {
                        logger.error("Error when retrieving orphanedImages ids", error);
                        return 0;
                    }
                }).thenAccept(numberOfOrphanedImages -> Platform.runLater(() ->
                    this.numberOfOrphanedImages.set(numberOfOrphanedImages))
                );
    }

    /**
     * See {@link ApisHandler#getNumberOfEntitiesLoading()}.
     */
    public ReadOnlyIntegerProperty getNumberOfEntitiesLoading() {
        return numberOfEntitiesLoading;
    }

    /**
     * See {@link ApisHandler#areOrphanedImagesLoading()}.
     */
    public ReadOnlyBooleanProperty areOrphanedImagesLoading() {
        return areOrphanedImagesLoading;
    }

    /**
     * See {@link ApisHandler#getOrphanedImagesIds()} ()}.
     */
    public ReadOnlyIntegerProperty getNumberOfOrphanedImages() {
        return numberOfOrphanedImages;
    }

    /**
     * See {@link ApisHandler#getNumberOfOrphanedImagesLoaded()}.
     */
    public ReadOnlyIntegerProperty getNumberOfOrphanedImagesLoaded() {
        return numberOfOrphanedImagesLoaded;
    }

    /**
     * See {@link ApisHandler#getNumberOfThumbnailsLoading()}.
     */
    public ReadOnlyIntegerProperty getNumberOfThumbnailsLoading() {
        return numberOfThumbnailsLoading;
    }

    /**
     * See {@link WebClient#getSelectedPixelAPI()}.
     */
    public ReadOnlyObjectProperty<PixelAPI> getSelectedPixelAPI() {
        return selectedPixelAPI;
    }

    /**
     * See {@link WebClient#getAvailablePixelAPIs()}.
     */
    public ObservableList<PixelAPI> getAvailablePixelAPIs() {
        return availablePixelAPIsImmutable;
    }

    /**
     * See {@link WebClient#getOpenedImagesURIs()}.
     */
    public ObservableSet<URI> getOpenedImagesURIs() {
        return openedImagesURIsImmutable;
    }

    /**
     * @return the currently selected owner of the browser
     */
    public ObjectProperty<Owner> getSelectedOwner() {
        return selectedOwner;
    }

    /**
     * @return the currently selected group of the browser
     */
    public ObjectProperty<Group> getSelectedGroup() {
        return selectedGroup;
    }

    /**
     * See {@link WebClients#getClients()}.
     */
    public static ObservableList<WebClient> getClients() {
        return clientsImmutable;
    }
}
