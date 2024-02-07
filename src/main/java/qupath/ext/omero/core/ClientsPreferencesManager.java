package qupath.ext.omero.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class to handle client information stored in the preferences of the application.
 */
public class ClientsPreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientsPreferencesManager.class);
    private static final StringProperty serverListPreference = PathPrefs.createPersistentPreference(
            "omero_ext.server_list",
            ""
    );
    private static final StringProperty latestServerPreference = PathPrefs.createPersistentPreference(
            "omero_ext.last_server",
            ""
    );
    private static final StringProperty latestUsernamePreference = PathPrefs.createPersistentPreference(
            "omero_ext.last_username",
            ""
    );
    private static final StringProperty msPixelBufferPortPreference = PathPrefs.createPersistentPreference(
            "omero_ext.ms_pixel_buffer_port",
            ""
    );
    private static final StringProperty webJpegQualityPreference = PathPrefs.createPersistentPreference(
            "omero_ext.web_jpeg_quality",
            ""
    );
    private static final ObservableList<URI> uris;
    private static final ObservableList<URI> urisImmutable;

    static {
        Gson gson = new Gson();
        List<URI> existingURIs = null;
        try {
            existingURIs = gson.fromJson(serverListPreference.get(), new TypeToken<>() {});
        } catch (JsonSyntaxException ignored) {}

        if (existingURIs == null) {
            existingURIs = List.of();
        }

        uris = FXCollections.observableArrayList(existingURIs);
        uris.addListener((ListChangeListener<? super URI>) c -> setServerListPreference());

        urisImmutable = FXCollections.unmodifiableObservableList(uris);
    }

    private ClientsPreferencesManager() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Reset all preferences used by this extension.
     */
    public static void clearAllPreferences() {
        uris.clear();
        latestServerPreference.set("");
        latestUsernamePreference.set("");
        msPixelBufferPortPreference.set("");
        webJpegQualityPreference.set("");
    }

    /**
     * <p>
     *     Returns the list of server URI stored by the preferences.
     *     This list is unmodifiable, use the {@link #addURI(URI) addURI}
     *     or the {@link #removeURI(URI) removeURI} methods to update its state.
     * </p>
     * <p>This list may be updated from any thread.</p>
     *
     * @return a list of server URI.
     */
    public static ObservableList<URI> getURIs() {
        return urisImmutable;
    }

    /**
     * Adds a server URI to the list of server URI stored by the preferences
     * (if it's not already there).
     *
     * @param uri  the URI to add
     */
    public static synchronized void addURI(URI uri) {
        latestServerPreference.set(uri.toString());

        if (!uris.contains(uri)) {
            uris.add(uri);
        }
    }

    /**
     * Remove a server URI from the list of server URI stored by the preferences
     * (if it exists).
     *
     * @param uri  the URI to remove
     */
    public static synchronized void removeURI(URI uri) {
        if (latestServerPreference.get().equals(uri.toString())) {
            latestServerPreference.set("");
        }

        uris.remove(uri);
    }

    /**
     * Returns the last URI given to {@link #addURI(URI) addURI}.
     *
     * @return the URI of the last server, or an empty String if there is no last URI
     */
    public static synchronized String getLastServerURI() {
        return latestServerPreference.get();
    }

    /**
     * Returns the last username set by {@link #setLastUsername(String) setLastUsername}.
     *
     * @return the last username
     */
    public static synchronized String getLastUsername() {
        return latestUsernamePreference.get();
    }

    /**
     * Set the last username value.
     *
     * @param username  the username to set
     */
    public static synchronized void setLastUsername(String username) {
        latestUsernamePreference.set(username);
    }

    /**
     * Get the saved port used by the pixel buffer microservice of the OMERO server
     * corresponding to the provided URI.
     *
     * @param serverURI  the URI of the OMERO server to whose port should be retrieved
     * @return the port, or an empty optional if not found
     */
    public static Optional<Integer> getMsPixelBufferPort(URI serverURI) {
        return getProperty(msPixelBufferPortPreference, serverURI).map(Double::intValue);
    }

    /**
     * Set the saved port used by the pixel buffer microservice of the OMERO server
     * corresponding to the provided URI.
     *
     * @param serverURI  the URI of the OMERO server to whose port should be set
     * @param port  the pixel buffer microservice port
     */
    public static void setMsPixelBufferPort(URI serverURI, int port) {
        setProperty(msPixelBufferPortPreference, serverURI, port);
    }

    /**
     * Get the saved JPEG quality used by the pixel web API corresponding to the provided URI.
     *
     * @param serverURI  the URI of the OMERO server to whose JPEG quality should be retrieved
     * @return the JPEG quality, or an empty optional if not found
     */
    public static Optional<Float> getWebJpegQuality(URI serverURI) {
        return getProperty(webJpegQualityPreference, serverURI).map(Double::floatValue);
    }

    /**
     * Set the saved JPEG quality used by the pixel web API corresponding to the provided URI.
     *
     * @param serverURI  the URI of the OMERO server to whose port should be set
     * @param jpegQuality  the JPEG quality
     */
    public static void setWebJpegQuality(URI serverURI, float jpegQuality) {
        setProperty(webJpegQualityPreference, serverURI, jpegQuality);
    }

    private static synchronized void setServerListPreference() {
        Gson gson = new Gson();
        ClientsPreferencesManager.serverListPreference.set(gson.toJson(uris));
    }

    private static synchronized Optional<Double> getProperty(StringProperty preference, URI serverURI) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<URI, Double>>() {}.getType();


        try {
            Map<URI, Double> uriProperties = gson.fromJson(preference.get(), type);

            if (uriProperties == null) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(uriProperties.get(serverURI));
            }
        } catch (JsonSyntaxException e) {
            logger.warn(String.format("Could not parse %s to JSON", preference.get()));
            return Optional.empty();
        }
    }

    public static synchronized <T> void setProperty(StringProperty preference, URI serverURI, T property) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<URI, T>>() {}.getType();

        try {
            Map<URI, T> uriProperties = gson.fromJson(preference.get(), type);
            if (uriProperties == null) {
                uriProperties = Map.of(serverURI, property);
            } else {
                uriProperties.put(serverURI, property);
            }
            preference.set(gson.toJson(uriProperties, type));
        } catch (JsonSyntaxException e) {
            logger.warn(String.format("Could not parse %s to JSON", preference.get()));
        }
    }
}
