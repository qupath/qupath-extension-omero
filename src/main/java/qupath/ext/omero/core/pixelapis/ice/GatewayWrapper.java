package qupath.ext.omero.core.pixelapis.ice;

import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.model.ExperimenterData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * <p>
 *     A wrapper around an Ice {@link Gateway}.
 *     This is needed because if a {@link Gateway} is directly used in the {@link IceAPI}
 *     class when the Ice dependencies are not available, the program will freeze.
 * </p>
 * <p>
 *     This class needs to be {@link #close() closed} once no longer used.
 * </p>
 */
class GatewayWrapper implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GatewayWrapper.class);
    private final Gateway gateway = new Gateway(new IceLogger());

    @Override
    public void close() throws Exception {
        gateway.close();
    }

    /**
     * @return whether the gateway is connected and authenticated
     */
    public synchronized boolean isConnected() {
        return gateway.isConnected();
    }

    /**
     * Attempt to connect the gateway with the provided credentials.
     * If a connection is already established, nothing will happen.
     *
     * @param loginCredentials  the credentials to use when connecting. If some credentials work,
     *                          the remaining will not be tested.
     * @return whether the gateway is connected after the connection attempts
     */
    public synchronized boolean connect(List<LoginCredentials> loginCredentials) {
        if (gateway.isConnected()) {
            return true;
        } else {
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

    /**
     * @return the gateway of this wrapper
     */
    public Gateway getGateway() {
        return gateway;
    }
}
