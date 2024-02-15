package qupath.ext.omero.gui.connectionsmanager.connection;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.gui.UiUtilities;

import java.net.URI;

/**
 * <p>
 *     The model of a connection. It contains properties and list which determine
 *     parts of the UI rendered by the connection.
 * </p>
 * <p>
 *     In effect, this class acts as an intermediate between a connection and a
 *     {@link WebClient WebClient}.
 *     Properties and lists of a WebClient can be updated from any thread but the connection can
 *     only be accessed from the UI thread, so this class propagates changes made to these elements
 *     from any thread to the UI thread.
 * </p>
 */
class ConnectionModel {

    private final ObservableSet<URI> openedImagesURIs = FXCollections.observableSet();
    private final ObservableSet<URI> openedImagesURIsImmutable = FXCollections.unmodifiableObservableSet(openedImagesURIs);

    public ConnectionModel(WebClient client) {
        UiUtilities.bindSetInUIThread(openedImagesURIs, client.getOpenedImagesURIs());
    }

    /**
     * See {@link WebClient#getOpenedImagesURIs()}.
     */
    public ObservableSet<URI> getOpenedImagesURIs() {
        return openedImagesURIsImmutable;
    }
}
