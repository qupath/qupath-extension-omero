package qupath.ext.omero.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.SeparatorMenuItem;

import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.Client;
import qupath.ext.omero.gui.browser.BrowseMenu;
import qupath.ext.omero.gui.datatransporters.DataTransporterMenu;
import qupath.ext.omero.gui.datatransporters.importers.AnnotationImporter;
import qupath.ext.omero.gui.datatransporters.importers.ImageSettingsImporter;
import qupath.ext.omero.gui.datatransporters.importers.KeyValuesImporter;
import qupath.ext.omero.gui.datatransporters.senders.AnnotationSender;
import qupath.ext.omero.gui.datatransporters.senders.ImageSettingsSender;
import qupath.ext.omero.gui.datatransporters.senders.KeyValuesSender;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MenuTools;
import qupath.ext.omero.gui.connectionsmanager.ConnectionsManagerCommand;

import java.util.List;
import java.util.ResourceBundle;

/**
 * Install the OMERO extension to a QuPath GUI.
 * <p>
 * It adds menus and actions to the Extensions menu:
 * <ul>
 *     <li>A browse menu, described in {@link qupath.ext.omero.gui.browser browser}.</li>
 *     <li>A connection manager action, described in {@link qupath.ext.omero.gui.connectionsmanager connection manager}.</li>
 *     <li>A menu to send and a menu to import entities to OMERO, described in {@link qupath.ext.omero.gui.datatransporters data transporters}.</li>
 * </ul>
 */
public class OmeroExtension implements QuPathExtension {

	private static final Logger logger = LoggerFactory.getLogger(OmeroExtension.class);
	private static final ResourceBundle resources = Utils.getResources();
	private static final BooleanProperty autoKvpImportProperty = PathPrefs.createPersistentPreference(
			"omero_ext.auto_kvp_import",
			true
	);
	private BrowseMenu browseMenu;

	@Override
	public synchronized void installExtension(QuPathGUI quPath) {
		if (browseMenu == null) {
			logger.debug("Installing OMERO extension");
			browseMenu = new BrowseMenu(quPath.getStage());

			MenuTools.addMenuItems(
					quPath.getMenu("Extensions", false),
					MenuTools.createMenu(
							"OMERO",
							browseMenu,
							ActionTools.createAction(
									new ConnectionsManagerCommand(
											quPath.getStage(),
											client -> browseMenu.openBrowserOfClient(client)
									),
									ConnectionsManagerCommand.getMenuTitle()
							),
							new SeparatorMenuItem(),
							new DataTransporterMenu(
									resources.getString("Extension.sendToOMERO"),
									quPath,
									List.of(new AnnotationSender(quPath), new KeyValuesSender(quPath), new ImageSettingsSender(quPath))
							),
							new DataTransporterMenu(
									resources.getString("Extension.importFromOMERO"),
									quPath,
									List.of(new AnnotationImporter(quPath), new KeyValuesImporter(quPath), new ImageSettingsImporter(quPath))
							),
							new SeparatorMenuItem(),
							new SampleScriptMenu(quPath)
					)
			);

			quPath.getPreferencePane()
					.getPropertySheet()
					.getItems()
					.add(new PropertyItemBuilder<>(autoKvpImportProperty, Boolean.class)
							.name(resources.getString("Extension.automaticallyImport"))
							.category(resources.getString("Extension.name"))
							.description(resources.getString("Extension.automaticallyImportWhenAddingImage"))
							.build()
					);

			quPath.addOnCloseRunnable(() -> {
				for (Client client: Client.getClients()) {
					try {
						client.close();
					} catch (Exception e) {
						logger.error("Error while closing {}", client, e);
					}
				}
			});
		}
	}

	@Override
	public String getName() {
		return resources.getString("Extension.name");
	}

	@Override
	public String getDescription() {
		return resources.getString("Extension.description");
	}

	/**
	 * @return whether key-value pairs and parent entities ID and name should be imported when an image of an OMERO
	 * server is added to a QuPath project
	 */
	public static ReadOnlyBooleanProperty getAutoKvpImportProperty() {
		return autoKvpImportProperty;
	}
}
