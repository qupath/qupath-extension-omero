package qupath.ext.omero.gui.datatransporters;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

import java.util.List;

/**
 * A menu whose items represent a list of {@link DataTransporter}.
 */
public class DataTransporterMenu extends Menu {

    private static final Logger logger = LoggerFactory.getLogger(DataTransporterMenu.class);
    /**
     * Create the data transporter menu.
     *
     * @param title the text displayed by this menu
     * @param quPath the currently used QuPath GUI
     * @param transporters  the list of {@link DataTransporter} to represent
     */
    public DataTransporterMenu(String title, QuPathGUI quPath, List<DataTransporter> transporters) {
        logger.debug("Creating data transporter menu with {}", transporters);

        setText(title);

        disableProperty().bind(quPath.imageDataProperty().isNull());

        getItems().addAll(transporters.stream()
                .map(dataTransporter -> {
                    MenuItem menuItem = new MenuItem(dataTransporter.getMenuTitle());
                    menuItem.setOnAction(ignored -> dataTransporter.transportData());

                    menuItem.disableProperty().bind(Bindings.createBooleanBinding(
                            () -> !dataTransporter.canTransportData(
                                    quPath.getProject() != null,
                                    quPath.getImageData() != null && quPath.getViewer().getServer().getMetadata().isRGB()
                            ),
                            quPath.projectProperty(), quPath.imageDataProperty()
                    ));

                    return menuItem;
                })
                .toList());
    }
}
