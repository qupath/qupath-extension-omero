package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.control.CheckComboBox;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.Namespace;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Form that lets the user choose how to import key-value pairs.
 */
public class ImportKeyValuePairsForm extends VBox {

    private static final ResourceBundle resources = Utils.getResources();
    @FXML
    private HBox namespaceContainer;
    @FXML
    private CheckComboBox<Namespace> namespace;
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
    public ImportKeyValuePairsForm(List<Namespace> namespaces) throws IOException {
        UiUtilities.loadFXML(this, ImportKeyValuePairsForm.class.getResource("import_key_value_pairs_form.fxml"));

        if (namespaces.isEmpty()) {
            namespaceContainer.setVisible(false);
            namespaceContainer.setManaged(false);
        } else {
            namespace.setConverter(new StringConverter<>() {
                @Override
                public String toString(Namespace object) {
                    if (object == null) {
                        return null;
                    } else if (object.equals(Namespace.getDefaultNamespace())) {
                        return resources.getString("DataTransporters.Forms.ImportKeyValuePairs.default");
                    } else {
                        return object.name();
                    }
                }

                @Override
                public Namespace fromString(String string) {
                    return new Namespace(string);
                }
            });
            namespace.getItems().addAll(namespaces);
            namespace.getCheckModel().checkAll();
        }

        ToggleGroup group = new ToggleGroup();
        keepExisting.setToggleGroup(group);
        replaceExisting.setToggleGroup(group);
        deleteAll.setToggleGroup(group);
    }

    /**
     * @return the selected namespaces to import
     */
    public List<Namespace> getSelectedNamespaces() {
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
