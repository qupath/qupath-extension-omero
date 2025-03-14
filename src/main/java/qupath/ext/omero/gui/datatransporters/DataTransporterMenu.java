package qupath.ext.omero.gui.datatransporters;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import qupath.lib.gui.QuPathGUI;

import java.util.List;

/**
 * A menu whose items represent a list of {@link DataTransporter}.
 */
public class DataTransporterMenu extends Menu {

    /**
     * Create the data transporter menu.
     *
     * @param title the text displayed by this menu
     * @param quPath the currently used QuPath GUI
     * @param transporters  the list of {@link DataTransporter} to represent
     */
    public DataTransporterMenu(String title, QuPathGUI quPath, List<DataTransporter> transporters) {
        setText(title);

        disableProperty().bind(quPath.imageDataProperty().isNull());

        setItems(quPath, transporters);
    }

    private void setItems(QuPathGUI quPath, List<DataTransporter> transporters) {
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
