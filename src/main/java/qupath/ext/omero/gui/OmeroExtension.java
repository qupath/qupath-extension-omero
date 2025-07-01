package qupath.ext.omero.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.SeparatorMenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.gui.browser.BrowseMenu;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.DataTransporterMenu;
import qupath.ext.omero.gui.datatransporters.importers.AnnotationImporter;
import qupath.ext.omero.gui.datatransporters.importers.ImageSettingsImporter;
import qupath.ext.omero.gui.datatransporters.importers.KeyValuesImporter;
import qupath.ext.omero.gui.datatransporters.scripts.ScriptLauncher;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

	private static final LinkedHashMap<String, String> SCRIPTS = new LinkedHashMap<>() {{
		put("Import annotations", "sample-scripts/import_annotations.groovy");
		put("Import image settings", "sample-scripts/import_image_settings.groovy");
		put("Import key value pairs", "sample-scripts/import_key_value_pairs.groovy");
		put("Open image from command line", "sample-scripts/open_image_from_command_line.groovy");
		put("Open images of project", "sample-scripts/open_images_of_project.groovy");
		put("Open server", "sample-scripts/open_server.groovy");
		put("Send annotations", "sample-scripts/send_annotations.groovy");
		put("Send image settings", "sample-scripts/send_image_settings.groovy");
		put("Send key value pairs", "sample-scripts/send_key_value_pairs.groovy");
		put("Send measurements", "sample-scripts/send_measurements.groovy");
	}};

	@Override
	public synchronized void installExtension(QuPathGUI quPath) {
		if (browseMenu == null) {
			logger.debug("Installing OMERO extension");
			browseMenu = new BrowseMenu(quPath.getStage());
			List<DataTransporter> scriptMenuItems = new ArrayList<>();

			SCRIPTS.forEach((title, path) -> {
				try (InputStream stream = OmeroExtension.class.getClassLoader().getResourceAsStream(path)) {
					if (stream != null) {
						String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
						if (!script.isEmpty()) {
							scriptMenuItems.add(new ScriptLauncher(quPath, title, script));
						}
					}
				} catch (Exception e) {
					logger.error(e.getLocalizedMessage(), e);
				}
			});

			MenuTools.addMenuItems(quPath.getMenu("Extensions", false),
					MenuTools.createMenu("OMERO",
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
									resources.getString("Extension.sampleScripts"),
									quPath,
									scriptMenuItems,
									true
							),
							new DataTransporterMenu(
									resources.getString("Extension.sendToOMERO"),
									quPath,
									List.of(new AnnotationSender(quPath), new KeyValuesSender(quPath), new ImageSettingsSender(quPath)),
									false
							),
							new DataTransporterMenu(
									resources.getString("Extension.importFromOMERO"),
									quPath,
									List.of(new AnnotationImporter(quPath), new KeyValuesImporter(quPath), new ImageSettingsImporter(quPath)),
									false
							)
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
