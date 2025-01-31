/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.ext.omero.gui;

import javafx.scene.control.SeparatorMenuItem;

import qupath.ext.omero.gui.browser.BrowseMenu;
import qupath.ext.omero.gui.datatransporters.DataTransporterMenu;
import qupath.ext.omero.gui.datatransporters.importers.AnnotationImporter;
import qupath.ext.omero.gui.datatransporters.importers.ImageSettingsImporter;
import qupath.ext.omero.gui.datatransporters.importers.KeyValuesImporter;
import qupath.ext.omero.gui.datatransporters.senders.AnnotationSender;
import qupath.ext.omero.gui.datatransporters.senders.ImageSettingsSender;
import qupath.ext.omero.gui.datatransporters.senders.KeyValuesSender;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
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

	private static final ResourceBundle resources = UiUtilities.getResources();
	private BrowseMenu browseMenu;

	@Override
	public synchronized void installExtension(QuPathGUI quPath) {
		if (browseMenu == null) {
			browseMenu = new BrowseMenu(quPath.getStage());

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
									resources.getString("Extension.sendToOMERO"),
									quPath,
									List.of(new AnnotationSender(quPath), new KeyValuesSender(quPath), new ImageSettingsSender(quPath))
							),
							new DataTransporterMenu(
									resources.getString("Extension.importFromOMERO"),
									quPath,
									List.of(new AnnotationImporter(quPath), new KeyValuesImporter(quPath), new ImageSettingsImporter(quPath))
							)
					)
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
}
