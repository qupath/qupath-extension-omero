package qupath.ext.omero.gui.browser;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import qupath.ext.omero.core.ClientsPreferencesManager;
import qupath.ext.omero.gui.UiUtilities;

import java.net.URI;

/**
 * <p>
 *     The model of the browse menu. It contains a list which determines
 *     parts of the UI rendered by the browse menu.
 * </p>
 * <p>
 *     In effect, this class acts as an intermediate between a browser menu and
 *     {@link ClientsPreferencesManager}.
 *     URIs can be updated from any thread but the browse menu can only be accessed from the UI thread,
 *     so this class propagates changes made to these elements from any thread to the UI thread.
 * </p>
 * <p>This class is not instantiable as it only contains a static method.</p>
 */
class BrowseMenuModel {

    private static final ObservableList<URI> uris = FXCollections.observableArrayList();
    private static final ObservableList<URI> urisImmutable = FXCollections.unmodifiableObservableList(uris);

    static {
        UiUtilities.bindListInUIThread(uris, ClientsPreferencesManager.getURIs());
    }

    private BrowseMenuModel() {
        throw new RuntimeException("This class is not instantiable.");
    }

    /**
     * See {@link ClientsPreferencesManager#getURIs()}.
     */
    public static ObservableList<URI> getURIs() {
        return urisImmutable;
    }
}
