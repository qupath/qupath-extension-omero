package qupath.ext.omero.core.pixelapis.ice;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import omero.gateway.LoginCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.ClientsPreferencesManager;
import qupath.ext.omero.core.Credentials;
import qupath.ext.omero.core.apis.ApisHandler2;
import qupath.ext.omero.core.pixelapis.PixelAPI;
import qupath.ext.omero.core.pixelapis.PixelAPIReader;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IceApi2 implements PixelAPI {

    static final String NAME = "Ice";
    private static final String ADDRESS_PARAMETER = "--serverAddress";
    private static final String PORT_PARAMETER = "--serverPort";
    private static final Logger logger = LoggerFactory.getLogger(IceApi2.class);
    private static final boolean gatewayAvailable;
    private final ApisHandler2 apisHandler;
    private final StringProperty serverAddress;
    private final IntegerProperty serverPort;
    private final BooleanProperty isAvailable;

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

    public IceApi2(ApisHandler2 apisHandler, Credentials.UserType userType) {
        this.apisHandler = apisHandler;
        this.serverAddress = new SimpleStringProperty(
                ClientsPreferencesManager.getIceAddress(apisHandler.getWebServerURI()).orElse("")
        );
        this.serverPort = new SimpleIntegerProperty(
                ClientsPreferencesManager.getIcePort(apisHandler.getWebServerURI()).orElse(0)
        );
        this.isAvailable = new SimpleBooleanProperty(userType.equals(Credentials.UserType.REGULAR_USER) && gatewayAvailable);
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
                    logger.warn("Can't convert {} to integer", args[i + 1], e);
                }
            }
        }
    }

    @Override
    public ObservableBooleanValue isAvailable() {
        return isAvailable;
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
        String sessionUuid = apisHandler.getSessionUuid();
        if (!isAvailable().get() || sessionUuid == null) {
            throw new IllegalStateException("This API is not available and cannot be used");
        }
        if (!canReadImage(metadata.getPixelType(), metadata.getSizeC())) {
            throw new IllegalArgumentException("The provided image cannot be read by this API");
        }

        synchronized (this) {
            if (gatewayWrapper == null) {
                gatewayWrapper = new GatewayWrapper();
            }
        }

        if (gatewayWrapper.isConnected()) {
            return new IceReader(gatewayWrapper, id, metadata.getChannels());
        } else {
            List<LoginCredentials> credentials = new ArrayList<>();
            if (serverAddress.get() != null && !serverAddress.get().isEmpty()) {
                credentials.add(new LoginCredentials(sessionUuid, sessionUuid, serverAddress.get(), serverPort.get()));
            }
            credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getWebServerURI().getHost(), apisHandler.getServerPort()));
            credentials.add(new LoginCredentials(sessionUuid, sessionUuid, apisHandler.getServerURI(), apisHandler.getServerPort()));

            if (gatewayWrapper.connect(credentials)) {
                return new IceReader(gatewayWrapper, id, metadata.getChannels());
            } else {
                throw new IOException("Could not connect to Ice server");
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof IceApi2 iceApi))
            return false;
        return iceApi.apisHandler.equals(apisHandler);
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
        if (gatewayWrapper != null) {
            gatewayWrapper.close();
        }
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
     * @param serverAddress the URL of the OMERO server
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
     * @param serverPort the port of the OMERO server
     */
    public void setServerPort(int serverPort) {
        this.serverPort.set(serverPort);

        ClientsPreferencesManager.setIcePort(
                apisHandler.getWebServerURI(),
                serverPort
        );
    }
}
