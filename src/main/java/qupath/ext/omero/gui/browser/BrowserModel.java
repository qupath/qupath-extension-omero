package qupath.ext.omero.gui.browser;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.entities.repositoryentities2.Server;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.net.URI;

/**
 * The model of the browser. It contains properties and list which determine parts of the UI rendered by the browser.
 * <p>
 * Part of the goal of this class is to act as an intermediate between a browser and a {@link Client}. Properties and
 * lists of a {@link Client} can be updated from any thread but the browser can only be accessed from the UI thread,
 * so this class propagates changes made to these elements from any thread to the UI thread.
 * <p>
 * An instance of this class must be {@link #close() closed} once no longer used.
 */
class BrowserModel implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(BrowserModel.class);
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty();
    private final BooleanProperty areOrphanedImagesLoading = new SimpleBooleanProperty(false);
    private final IntegerProperty numberOfOrphanedImages = new SimpleIntegerProperty();
    private final IntegerProperty numberOfOrphanedImagesLoaded = new SimpleIntegerProperty(0);
    private final IntegerProperty numberOfThumbnailsLoading = new SimpleIntegerProperty(0);
    private final ObjectProperty<PixelApi> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableList<PixelApi> availablePixelApis = FXCollections.observableArrayList();
    private final ObservableList<PixelApi> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelApis);
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final Client client;
    private final ChangeListener<? super Number> numberOfEntitiesLoadingListener;
    private final ChangeListener<? super Boolean> areOrphanedImagesLoadingListener;
    private final ChangeListener<? super Number> numberOfOrphanedImagesLoadedListener;
    private final ChangeListener<? super Number> numberOfThumbnailsLoadingListener;
    private final ChangeListener<? super PixelApi> selectedPixelAPIListener;
    private final ListChangeListener<? super PixelApi> availablePixelApisListener;
    private final SetChangeListener<? super URI> openedImagesURIsListener;
    private final ObjectProperty<Owner> selectedOwner;
    private final ObjectProperty<Group> selectedGroup;

    /**
     * Creates a new browser model
     *
     * @param client the client whose properties and lists should be listened
     * @param server the server of the client
     */
    public BrowserModel(Client client, Server server) {
        this.client = client;

        this.numberOfEntitiesLoadingListener = UiUtilities.bindPropertyInUIThread(
                numberOfEntitiesLoading,
                client.getApisHandler().getNumberOfEntitiesLoading()
        );
        this.areOrphanedImagesLoadingListener = UiUtilities.bindPropertyInUIThread(
                areOrphanedImagesLoading,
                client.getApisHandler().areOrphanedImagesLoading()
        );
        this.numberOfOrphanedImagesLoadedListener = UiUtilities.bindPropertyInUIThread(
                numberOfOrphanedImagesLoaded,
                client.getApisHandler().getNumberOfOrphanedImagesLoaded()
        );
        this.numberOfThumbnailsLoadingListener = UiUtilities.bindPropertyInUIThread(
                numberOfThumbnailsLoading,
                client.getApisHandler().getNumberOfThumbnailsLoading()
        );
        this.selectedPixelAPIListener = UiUtilities.bindPropertyInUIThread(
                selectedPixelAPI,
                client.getSelectedPixelApi()
        );

        this.availablePixelApisListener = UiUtilities.bindListInUIThread(availablePixelApis, client.getAvailablePixelAPIs());

        this.openedImagesURIsListener = UiUtilities.bindSetInUIThread(openedImagesURIs, client.getOpenedImagesURIs());

        this.selectedOwner = new SimpleObjectProperty<>(server.getConnectedOwner());
        this.selectedGroup = new SimpleObjectProperty<>(server.getDefaultGroup());

        client.getApisHandler().getOrphanedImagesIds().handle((orphanedImageIds, error) -> {
            if (error == null) {
                return orphanedImageIds.size();
            } else {
                logger.error("Error when retrieving orphanedImages ids", error);
                return 0;
            }
        }).thenAccept(numberOfOrphanedImages -> Platform.runLater(() ->
                this.numberOfOrphanedImages.set(numberOfOrphanedImages)
        ));
    }

    @Override
    public void close() {
        client.getApisHandler().getNumberOfEntitiesLoading().removeListener(numberOfEntitiesLoadingListener);
        client.getApisHandler().areOrphanedImagesLoading().removeListener(areOrphanedImagesLoadingListener);
        client.getApisHandler().getNumberOfOrphanedImagesLoaded().removeListener(numberOfOrphanedImagesLoadedListener);
        client.getApisHandler().getNumberOfThumbnailsLoading().removeListener(numberOfThumbnailsLoadingListener);
        client.getSelectedPixelApi().removeListener(selectedPixelAPIListener);

        client.getAvailablePixelAPIs().removeListener(availablePixelApisListener);

        client.getOpenedImagesURIs().removeListener(openedImagesURIsListener);
    }

    /**
     * See {@link ApisHandler#getNumberOfEntitiesLoading()}.
     */
    public ObservableIntegerValue getNumberOfEntitiesLoading() {
        return numberOfEntitiesLoading;
    }

    /**
     * See {@link ApisHandler#areOrphanedImagesLoading()}.
     */
    public ObservableBooleanValue areOrphanedImagesLoading() {
        return areOrphanedImagesLoading;
    }

    /**
     * Size of {@link ApisHandler#getOrphanedImagesIds()}.
     */
    public ObservableIntegerValue getNumberOfOrphanedImages() {
        return numberOfOrphanedImages;
    }

    /**
     * See {@link ApisHandler#getNumberOfOrphanedImagesLoaded()}.
     */
    public ObservableIntegerValue getNumberOfOrphanedImagesLoaded() {
        return numberOfOrphanedImagesLoaded;
    }

    /**
     * See {@link ApisHandler#getNumberOfThumbnailsLoading()}.
     */
    public ObservableIntegerValue getNumberOfThumbnailsLoading() {
        return numberOfThumbnailsLoading;
    }

    /**
     * See {@link Client#getSelectedPixelApi()}.
     */
    public ObservableValue<PixelApi> getSelectedPixelAPI() {
        return selectedPixelAPI;
    }

    /**
     * See {@link Client#getAvailablePixelAPIs()}.
     */
    public ObservableList<PixelApi> getAvailablePixelAPIs() {
        return availablePixelAPIsImmutable;
    }

    /**
     * See {@link Client#getOpenedImagesURIs()}.
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
}
