package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.CheckComboBox;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.List;

/**
 * Form that lets the user choose how to import key-value pairs.
 */
public class ImportKeyValuePairsForm extends VBox {

    private final CheckComboBox<String> namespace = new CheckComboBox<>();
    @FXML
    private HBox namespaceContainer;
    @FXML
    private RadioButton keepExisting;
    @FXML
    private RadioButton replaceExisting;
    @FXML
    private RadioButton deleteAll;

    /**
     * Describes how to deal with existing key value pairs.
     */
    public enum Choice {
        KEEP_EXISTING,
        REPLACE_EXITING,
        DELETE_ALL
    }

    /**
     * Create the form.
     *
     * @throws IOException if an error occurs while creating the form
     */
    public ImportKeyValuePairsForm(List<String> namespaces) throws IOException {
        UiUtilities.loadFXML(this, ImportKeyValuePairsForm.class.getResource("import_key_value_pairs_form.fxml"));

        namespace.getItems().addAll(namespaces);
        namespace.getCheckModel().checkAll();
        namespace.setMaxWidth(200);
        namespaceContainer.getChildren().add(namespace);

        ToggleGroup group = new ToggleGroup();
        keepExisting.setToggleGroup(group);
        replaceExisting.setToggleGroup(group);
        deleteAll.setToggleGroup(group);
    }

    /**
     * @return the selected namespaces to import
     */
    public List<String> getSelectedNamespaces() {
        return namespace.getCheckModel().getCheckedIndices().stream()
                .map(index -> namespace.getCheckModel().getItem(index))
                .toList();
    }

    /**
     * @return the choice of the user regarding existing key value pairs
     */
    public Choice getSelectedChoice() {
        if (keepExisting.isSelected()) {
            return Choice.KEEP_EXISTING;
        } else if (replaceExisting.isSelected()) {
            return Choice.REPLACE_EXITING;
        } else {
            return Choice.DELETE_ALL;
        }
    }
}
