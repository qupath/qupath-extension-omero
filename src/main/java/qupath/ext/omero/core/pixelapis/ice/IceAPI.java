package qupath.ext.omero.core.pixelapis.ice;

import com.drew.lang.annotations.Nullable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.model.ExperimenterData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ClientsPreferencesManager;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.core.pixelapis.PixelAPIReader;
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *     This API uses the <a href="https://omero.readthedocs.io/en/v5.6.7/developers/Java.html">OMERO gateway</a>
 *     to access pixel values of an image. Any image can be used, and pixel values are accurate.
 * </p>
 */
public class IceAPI implements PixelAPI {

    static final String NAME = "Ice";
    private static final Logger logger = LoggerFactory.getLogger(IceAPI.class);
    private static final String ADDRESS_PARAMETER = "--serverAddress";
    private static final String PORT_PARAMETER = "--serverPort";
    private static final boolean gatewayAvailable;
    private final Gateway gateway = new Gateway(new IceLogger());
    private final ApisHandler apisHandler;
    private final boolean isAuthenticated;
    private final String sessionUuid;
    private final StringProperty serverAddress;
    private final IntegerProperty serverPort;

    static {
        boolean available = false;

        try {
            Class.forName("omero.gateway.Gateway");
            available = true;
        } catch (ClassNotFoundException e) {
            logger.debug("OMERO Ice gateway is unavailable ('omero.gateway.Gateway' not found).");
        } finally {
            gatewayAvailable = available;
        }
    }

    /**
     * Creates a new IceAPI.
     *
     * @param apisHandler  the apis handler owning this API
     * @param isAuthenticated  whether the user is currently authenticated to the OMERO server
     * @param sessionUuid  the session UUID of the client connection. Can be null if the user is not authenticated
     */
    public IceAPI(ApisHandler apisHandler, boolean isAuthenticated, @Nullable String sessionUuid) {
        this.apisHandler = apisHandler;
        this.isAuthenticated = isAuthenticated;
        this.sessionUuid = sessionUuid;
        this.serverAddress = new SimpleStringProperty(
                ClientsPreferencesManager.getIceAddress(apisHandler.getWebServerURI()).orElse("")
        );
        this.serverPort = new SimpleIntegerProperty(
                ClientsPreferencesManager.getIcePort(apisHandler.getWebServerURI()).orElse(0)
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String[] getArgs() {
        return new String[] {
                ADDRESS_PARAMETER, serverAddress.get(),
                PORT_PARAMETER, String.valueOf(serverPort.get())
        };
    }

    @Override
    public void setParametersFromArgs(String... args) {
        for (int i=0; i<args.length-1; ++i) {
            if (args[i].equals(ADDRESS_PARAMETER)) {
                setServerAddress(args[i+1]);
            }
            if (args[i].equals(PORT_PARAMETER)) {
                try {
                    setServerPort(Integer.parseInt(args[i + 1]));
                } catch (NumberFormatException e) {
                    logger.warn(String.format("Can't convert %s to integer", args[i+1]), e);
                }
            }
        }
    }

    @Override
    public ObservableBooleanValue isAvailable() {
        return new SimpleBooleanProperty(isAuthenticated && gatewayAvailable);
    }

    @Override
    public boolean canAccessRawPixels() {
        return true;
    }

    @Override
    public boolean canReadImage(PixelType pixelType) {
        return !pixelType.equals(PixelType.INT8) && !pixelType.equals(PixelType.UINT32);
    }

    @Override
    public boolean canReadImage(int numberOfChannels) {
        return true;
    }

    @Override
    public PixelAPIReader createReader(long id, ImageServerMetadata metadata) throws IOException {
        if (!isAvailable().get() || sessionUuid == null) {
            throw new IllegalStateException("This API is not available and cannot be used");
        }
        if (!canReadImage(metadata.getPixelType(), metadata.getSizeC())) {
            throw new IllegalArgumentException("The provided image cannot be read by this API");
        }

        if (gateway.isConnected()) {
            return new IceReader(gateway, id, metadata.getChannels());
        } else {
            List<LoginCredentials> credentials = new ArrayList<>();
            if (serverAddress.get() != null && !serverAddress.get().isEmpty()) {
                credentials.add(new LoginCredentials(sessionUuid, sessionUuid, serverAddress.get(), serverPort.get()));
            }
            credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getWebServerURI().getHost(), apisHandler.getServerPort()));
            credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getServerURI(), apisHandler.getServerPort()));

            if (connect(credentials)) {
                return new IceReader(gateway, id, metadata.getChannels());
            } else {
                throw new IOException("Could not connect to Ice server");
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof IceAPI iceAPI))
            return false;
        return iceAPI.apisHandler.equals(apisHandler);
    }

    @Override
    public int hashCode() {
        return apisHandler.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Ice API of %s", apisHandler.getWebServerURI());
    }

    @Override
    public void close() throws Exception {
        gateway.close();
    }

    /**
     * @return the address used to communicate with the OMERO server.
     * This property may be updated from any thread
     */
    public ReadOnlyStringProperty getServerAddress() {
        return serverAddress;
    }

    /**
     * Set the address used to communicate with the OMERO server.
     *
     * @param serverAddress  the URL of the OMERO server
     */
    public void setServerAddress(String serverAddress) {
        this.serverAddress.set(serverAddress);

        ClientsPreferencesManager.setIceAddress(
                apisHandler.getWebServerURI(),
                serverAddress
        );
    }

    /**
     * @return the port used to communicate with the OMERO server.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getServerPort() {
        return serverPort;
    }

    /**
     * Set the port used to communicate with the OMERO server.
     *
     * @param serverPort  the port of the OMERO server
     */
    public void setServerPort(int serverPort) {
        this.serverPort.set(serverPort);

        ClientsPreferencesManager.setIcePort(
                apisHandler.getWebServerURI(),
                serverPort
        );
    }

    private boolean connect(List<LoginCredentials> loginCredentials) {
        for (int i=0; i<loginCredentials.size(); i++) {
            try {
                ExperimenterData experimenterData = gateway.connect(loginCredentials.get(i));
                if (experimenterData != null) {
                    logger.info(String.format(
                            "Connected to the OMERO.server instance at %s with user %s",
                            loginCredentials.get(i).getServer(),
                            experimenterData.getUserName())
                    );
                    return true;
                }
            } catch (Exception e) {
                if (i < loginCredentials.size()-1) {
                    logger.warn(String.format(
                            "Ice can't connect to %s:%d. Trying %s:%d...",
                            loginCredentials.get(i).getServer().getHost(),
                            loginCredentials.get(i).getServer().getPort(),
                            loginCredentials.get(i+1).getServer().getHost(),
                            loginCredentials.get(i+1).getServer().getPort()
                    ), e);
                } else {
                    logger.warn(String.format(
                            "Ice can't connect to %s:%d. No more credentials available",
                            loginCredentials.get(i).getServer().getHost(),
                            loginCredentials.get(i).getServer().getPort()
                    ), e);
                }
            }
        }
        return false;
    }
}
