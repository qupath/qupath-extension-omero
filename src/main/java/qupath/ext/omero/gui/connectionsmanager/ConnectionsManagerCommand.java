package qupath.ext.omero.gui.connectionsmanager;

import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Command that starts a {@link ConnectionsManager}.
 */
public class ConnectionsManagerCommand implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ConnectionsManagerCommand.class);
	private static final ResourceBundle resources = Utils.getResources();
	private final Stage owner;
	private final Consumer<Client> openClientBrowser;
	private ConnectionsManager connectionsManager;

	/**
	 * Creates a new connection manager command.
	 *
	 * @param owner the stage that should own the connection manager window
	 * @param openClientBrowser a function that will be called to request opening the browser of a client
	 */
	public ConnectionsManagerCommand(Stage owner, Consumer<Client> openClientBrowser) {
		this.owner = owner;
		this.openClientBrowser = openClientBrowser;
	}

	@Override
	public void run() {
		if (connectionsManager == null) {
			try {
				connectionsManager = new ConnectionsManager(owner, openClientBrowser);
			} catch (IOException e) {
				logger.error("Error while creating the connection manager window", e);
			}
		} else {
			connectionsManager.show();
			connectionsManager.requestFocus();
		}
	}

	/**
	 * @return the text that should appear on the menu starting this command
	 */
	public static String getMenuTitle() {
		return resources.getString("ConnectionsManager.Command.manageServerConnections");
	}
}
