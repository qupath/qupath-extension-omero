package qupath.ext.omero.gui.browser;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.core.apis.json.permissions.ExperimenterGroup;
import qupath.ext.omero.core.apis.json.repositoryentities.Server;
import qupath.ext.omero.core.pixelapis.PixelApi;
import qupath.ext.omero.gui.UiUtils;

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

    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty();
    private final IntegerProperty numberOfThumbnailsLoading = new SimpleIntegerProperty(0);
    private final ObjectProperty<PixelApi> selectedPixelAPI = new SimpleObjectProperty<>();
    private final ObservableList<PixelApi> availablePixelApis = FXCollections.observableArrayList();
    private final ObservableList<PixelApi> availablePixelAPIsImmutable = FXCollections.unmodifiableObservableList(availablePixelApis);
    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);
    private final Client client;
    private final ChangeListener<? super Number> numberOfEntitiesLoadingListener;
    private final ChangeListener<? super Number> numberOfThumbnailsLoadingListener;
    private final ChangeListener<? super PixelApi> selectedPixelAPIListener;
    private final ListChangeListener<? super PixelApi> availablePixelApisListener;
    private final SetChangeListener<? super URI> openedImagesURIsListener;
    private final ObjectProperty<Experimenter> selectedOwner;
    private final ObjectProperty<ExperimenterGroup> selectedGroup;

    /**
     * Creates a new browser model
     *
     * @param client the client whose properties and lists should be listened
     * @param server the server of the client
     */
    public BrowserModel(Client client, Server server) {
        this.client = client;

        this.numberOfEntitiesLoadingListener = UiUtils.bindPropertyInUIThread(
                numberOfEntitiesLoading,
                client.getApisHandler().getNumberOfEntitiesLoading()
        );
        this.numberOfThumbnailsLoadingListener = UiUtils.bindPropertyInUIThread(
                numberOfThumbnailsLoading,
                client.getApisHandler().getNumberOfThumbnailsLoading()
        );
        this.selectedPixelAPIListener = UiUtils.bindPropertyInUIThread(
                selectedPixelAPI,
                client.getSelectedPixelApi()
        );

        this.availablePixelApisListener = UiUtils.bindListInUIThread(availablePixelApis, client.getAvailablePixelAPIs());

        this.openedImagesURIsListener = UiUtils.bindSetInUIThread(openedImagesURIs, client.getOpenedImagesURIs());

        this.selectedOwner = new SimpleObjectProperty<>(server.getConnectedExperimenter());
        this.selectedGroup = new SimpleObjectProperty<>(server.getDefaultGroup());
    }

    @Override
    public void close() {
        client.getApisHandler().getNumberOfEntitiesLoading().removeListener(numberOfEntitiesLoadingListener);
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
    public ObjectProperty<Experimenter> getSelectedOwner() {
        return selectedOwner;
    }

    /**
     * @return the currently selected group of the browser
     */
    public ObjectProperty<ExperimenterGroup> getSelectedGroup() {
        return selectedGroup;
    }
}
