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
 *     This is needed because if a {@link Gateway} is directly used in the {@link IceApi}
 *     class when the Ice dependencies are not available, the program will crash.
 * </p>
 * <p>
 *     This class needs to be {@link #close() closed} once no longer used.
 * </p>
 */
class GatewayWrapper implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GatewayWrapper.class);
    private final Gateway gateway = new Gateway(new IceLogger());

    /**
     * Create a gateway and attempt to connect the gateway with the provided credentials.
     *
     * @param loginCredentials the credentials to use when connecting. If some credentials work,
     *                         the remaining will not be tested.
     * @throws Exception if the connection failed with all provided credentials
     */
    public GatewayWrapper(List<LoginCredentials> loginCredentials) throws Exception {
        logger.debug("Attempting to create gateway with the following credentials: {}", loginCredentials);

        for (int i=0; i<loginCredentials.size(); i++) {
            try {
                ExperimenterData experimenterData = gateway.connect(loginCredentials.get(i));
                if (experimenterData != null) {
                    logger.info(
                            "Connected to the OMERO.server instance at {} with user {}",
                            loginCredentials.get(i).getServer(),
                            experimenterData.getUserName()
                    );
                    return;
                }
            } catch (Exception e) {
                if (i < loginCredentials.size()-1) {
                    logger.debug(
                            "Ice can't connect to {}:{}. Trying {}:{}...",
                            loginCredentials.get(i).getServer().getHost(),
                            loginCredentials.get(i).getServer().getPort(),
                            loginCredentials.get(i + 1).getServer().getHost(),
                            loginCredentials.get(i + 1).getServer().getPort(),
                            e
                    );
                } else {
                    logger.error(
                            "Ice can't connect to {}:{}. No more credentials available",
                            loginCredentials.get(i).getServer().getHost(),
                            loginCredentials.get(i).getServer().getPort(),
                            e
                    );
                    throw e;
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        gateway.close();
    }

    /**
     * @return the gateway of this wrapper
     */
    public Gateway getGateway() {
        return gateway;
    }
}
