package qupath.ext.omero.core.preferences;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.Credentials;
import qupath.lib.gui.prefs.PathPrefs;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A static class to store {@link ServerPreference ServerPreferences}.
 */
public class PreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);
    private static final Gson gson = new Gson();
    private static final StringProperty preference = PathPrefs.createPersistentPreference(
            "omero_ext.servers-information",
            "[]"
    );
    private static final ObservableList<ServerPreference> serverPreferences;
    private static final ObservableList<ServerPreference> serverPreferencesImmutable;

    static {
        List<ServerPreference> existingPreferences = List.of();
        try {
            existingPreferences = gson.fromJson(preference.get(), new TypeToken<>() {});
        } catch (JsonSyntaxException ignored) {
            logger.debug("Cannot retrieve server preferences from {}. Considering it to be empty", preference.get());
        }

        serverPreferences = FXCollections.observableArrayList();
        serverPreferences.addAll(existingPreferences);
        serverPreferencesImmutable = FXCollections.unmodifiableObservableList(serverPreferences);
    }

    private PreferencesManager() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Save a new server. If a preference with the same {@link ServerPreference#webServerUri()} is already stored,
     * its credentials will be modified.
     *
     * @param webServerUri the URI of the OMERO server to save
     * @param credentials the credentials used to connect to the OMERO server
     */
    public synchronized static void addServer(URI webServerUri, Credentials credentials) {
        List<ServerPreference> existingPreferences = serverPreferences.stream()
                .filter(preference -> preference.webServerUri().equals(webServerUri))
                .toList();

        if (existingPreferences.isEmpty()) {
            serverPreferences.add(new ServerPreference(webServerUri, credentials, 0, null, 0, 0));
            logger.debug("Preference for {} added with the following credentials: {}", webServerUri, credentials);
        } else {
            serverPreferences.removeAll(existingPreferences);
            serverPreferences.add(new ServerPreference(
                    webServerUri,
                    credentials,
                    existingPreferences.getFirst().webJpegQuality(),
                    existingPreferences.getFirst().iceAddress(),
                    existingPreferences.getFirst().icePort(),
                    existingPreferences.getFirst().msPixelBufferPort()
            ));
            logger.debug("Preference for {} modified with the following credentials: {}", webServerUri, credentials);
        }

        preference.set(gson.toJson(serverPreferences));
    }

    /**
     * Remove a server.
     *
     * @param webServerUri the URI of the OMERO server to remove
     */
    public synchronized static void removeServer(URI webServerUri) {
        serverPreferences.removeAll(serverPreferences.stream()
                .filter(serverPreference -> serverPreference.webServerUri().equals(webServerUri))
                .toList()
        );

        preference.set(gson.toJson(serverPreferences));
    }

    /**
     * Get the currently saved server preferences. This list is immutable.
     *
     * @return the currently saved server preferences
     */
    public static ObservableList<ServerPreference> getServerPreferences() {
        return serverPreferencesImmutable;
    }

    /**
     * Get the last saved credentials used to connect to the provided OMERO web server.
     *
     * @param webServerUri the URI of the OMERO web server to whose last credentials should be retrieved
     * @return the last saved credentials of the provided OMERO server
     */
    public static Optional<Credentials> getCredentials(URI webServerUri) {
        return getProperty(
                webServerUri,
                serverPreference -> Optional.of(serverPreference.credentials())
        );
    }

    /**
     * Set the saved JPEG quality used by the web pixel API of the provided web server. This will only
     * happen if {@link #getServerPreferences()} contains an entry with the specified web server URI.
     *
     * @param webServerUri the URI of the OMERO web server to whose JPEG quality should be set
     * @param webJpegQuality the JPEG quality to set
     */
    public static void setWebJpegQuality(URI webServerUri, float webJpegQuality) {
        setProperty(
                webServerUri,
                "web JPEG quality",
                webJpegQuality,
                serverPreference -> new ServerPreference(
                        webServerUri,
                        serverPreference.credentials(),
                        webJpegQuality,
                        serverPreference.iceAddress(),
                        serverPreference.icePort(),
                        serverPreference.msPixelBufferPort()
                )
        );
    }

    /**
     * Get the saved JPEG quality used by the web pixel API of the provided web server.
     *
     * @param webServerUri the URI of the OMERO web server to whose JPEG quality should be retrieved
     * @return the JPEG quality, or an empty optional if not found
     */
    public static Optional<Float> getWebJpegQuality(URI webServerUri) {
        return getProperty(
                webServerUri,
                serverPreference -> serverPreference.webJpegQuality() == 0 ? Optional.empty() : Optional.of(serverPreference.webJpegQuality())
        );
    }

    /**
     * Set the saved address of the OMERO ICE server corresponding to the provided web server. This will only
     * happen if {@link #getServerPreferences()} contains an entry with the specified web server URI.
     *
     * @param webServerUri the URI of the OMERO web server to whose OMERO ICE server address should be set
     * @param iceAddress the OMERO ICE server address to set
     */
    public static void setIceAddress(URI webServerUri, String iceAddress) {
        setProperty(
                webServerUri,
                "ICE address",
                iceAddress,
                serverPreference -> new ServerPreference(
                        webServerUri,
                        serverPreference.credentials(),
                        serverPreference.webJpegQuality(),
                        iceAddress,
                        serverPreference.icePort(),
                        serverPreference.msPixelBufferPort()
                )
        );
    }

    /**
     * Get the saved address of the OMERO ICE server corresponding to the provided web server.
     *
     * @param webServerUri the URI of the OMERO web server to whose OMERO ICE server address should be retrieved
     * @return the OMERO ICE server address, or an empty optional if not found
     */
    public static Optional<String> getIceAddress(URI webServerUri) {
        return getProperty(
                webServerUri,
                serverPreference -> Optional.ofNullable(serverPreference.iceAddress())
        );
    }

    /**
     * Set the saved port of the OMERO ICE server corresponding to the provided web server. This will only
     * happen if {@link #getServerPreferences()} contains an entry with the specified web server URI.
     *
     * @param webServerUri the URI of the OMERO web server to whose OMERO ICE server port should be set
     * @param icePort the OMERO ICE server port to set
     */
    public static void setIcePort(URI webServerUri, int icePort) {
        setProperty(
                webServerUri,
                "ICE port",
                icePort,
                serverPreference -> new ServerPreference(
                        webServerUri,
                        serverPreference.credentials(),
                        serverPreference.webJpegQuality(),
                        serverPreference.iceAddress(),
                        icePort,
                        serverPreference.msPixelBufferPort()
                )
        );
    }

    /**
     * Get the saved port of the OMERO ICE server corresponding to the provided web server.
     *
     * @param webServerUri the URI of the OMERO web server to whose OMERO ICE server port should be retrieved
     * @return the OMERO ICE server port, or an empty optional if not found
     */
    public static Optional<Integer> getIcePort(URI webServerUri) {
        return getProperty(
                webServerUri,
                serverPreference -> serverPreference.icePort() == 0 ? Optional.empty() : Optional.of(serverPreference.icePort())
        );
    }

    /**
     * Set the saved port used by the pixel buffer microservice of the provided OMERO server. This will only
     * happen if {@link #getServerPreferences()} contains an entry with the specified web server URI.
     *
     * @param webServerUri the URI of the OMERO web server to whose pixel buffer microservice port should be set
     * @param msPixelBufferPort the pixel buffer microservice port to set
     */
    public static void setMsPixelBufferPort(URI webServerUri, int msPixelBufferPort) {
        setProperty(
                webServerUri,
                "microservice pixel buffer port",
                msPixelBufferPort,
                serverPreference -> new ServerPreference(
                        webServerUri,
                        serverPreference.credentials(),
                        serverPreference.webJpegQuality(),
                        serverPreference.iceAddress(),
                        serverPreference.icePort(),
                        msPixelBufferPort
                )
        );
    }

    /**
     * Get the saved port used by the pixel buffer microservice of the provided OMERO server.
     *
     * @param webServerUri the URI of the OMERO web server to whose pixel buffer microservice port should be retrieved
     * @return the pixel buffer microservice port, or an empty optional if not found
     */
    public static Optional<Integer> getMsPixelBufferPort(URI webServerUri) {
        return getProperty(
                webServerUri,
                serverPreference -> serverPreference.msPixelBufferPort() == 0 ? Optional.empty() : Optional.of(serverPreference.msPixelBufferPort())
        );
    }

    private synchronized static <T> Optional<T> getProperty(URI webServerUri, Function<ServerPreference, Optional<T>> preferenceGetter) {
        return serverPreferences.stream()
                .filter(preference -> preference.webServerUri().equals(webServerUri))
                .findAny()
                .flatMap(preferenceGetter);
    }

    private synchronized static <T> void setProperty(URI webServerUri, String propertyName, T propertyValue, UnaryOperator<ServerPreference> preferenceCreator) {
        List<ServerPreference> existingPreferences = serverPreferences.stream()
                .filter(preference -> preference.webServerUri().equals(webServerUri))
                .toList();

        if (existingPreferences.isEmpty()) {
            logger.warn("No preference for {} exists. Cannot set {}", webServerUri, propertyName);
        } else {
            serverPreferences.removeAll(existingPreferences);
            serverPreferences.add(preferenceCreator.apply(existingPreferences.getFirst()));
            logger.debug("Preference for {} modified with the following {}: {}", webServerUri, propertyName, propertyValue);
        }

        preference.set(gson.toJson(serverPreferences));
    }
}
