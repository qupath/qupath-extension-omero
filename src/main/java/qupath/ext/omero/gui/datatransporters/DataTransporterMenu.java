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
     * @param title  the text displqyed by this menu
     * @param qupath  the currently used QuPath GUI
     * @param transporters  the list of {@link DataTransporter} to represent
     */
    public DataTransporterMenu(String title, QuPathGUI qupath, List<DataTransporter> transporters) {
        setText(title);

        disableProperty().bind(qupath.imageDataProperty().isNull());

        setItems(qupath, transporters);
    }

    private void setItems(QuPathGUI qupath, List<DataTransporter> transporters) {
        getItems().addAll(transporters.stream()
                .map(dataTransporter -> {
                    MenuItem menuItem = new MenuItem(dataTransporter.getMenuTitle());
                    menuItem.setOnAction(ignored -> dataTransporter.transportData());

                    menuItem.disableProperty().bind(Bindings.createBooleanBinding(
                            () -> !dataTransporter.canTransportData(
                                    qupath.getProject() != null,
                                    qupath.getImageData() != null && qupath.getViewer().getServer().getMetadata().isRGB()
                            ),
                            qupath.projectProperty(), qupath.imageDataProperty()
                    ));

                    return menuItem;
                })
                .toList());
    }
}
