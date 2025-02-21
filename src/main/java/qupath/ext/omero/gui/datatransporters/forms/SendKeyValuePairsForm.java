package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ListSelectionView;
import qupath.ext.omero.Utils;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Form that lets the user choose how to send key-value pairs.
 */
public class SendKeyValuePairsForm extends VBox {

    private static final ResourceBundle resources = Utils.getResources();
    @FXML
    private ListSelectionView<Map.Entry<String, String>> keyValuesToSend;
    @FXML
    private ChoiceBox<String> namespace;
    @FXML
    private TextField newNamespace;
    @FXML
    private RadioButton keepExisting;
    @FXML
    private RadioButton replaceExisting;
    /**
     * Describes how to deal with existing key value pairs.
     */
    public enum Choice {
        KEEP_EXISTING,
        REPLACE_EXITING
    }

    /**
     * Create the form.
     *
     * @throws IOException if an error occurs while creating the form
     */
    public SendKeyValuePairsForm(Map<String, String> keyValues, List<String> existingNamespaces) throws IOException {
        UiUtilities.loadFXML(this, SendKeyValuePairsForm.class.getResource("send_key_value_pairs_form.fxml"));

        Label sourceHeader = new Label(resources.getString("DataTransporters.Forms.SendKeyValuePairs.available"));
        sourceHeader.getStyleClass().add("header");
        keyValuesToSend.setSourceHeader(sourceHeader);
        Label targetHeader = new Label(resources.getString("DataTransporters.Forms.SendKeyValuePairs.selected"));
        targetHeader.getStyleClass().add("header");
        keyValuesToSend.setTargetHeader(targetHeader);
        keyValuesToSend.getSourceItems().addAll(keyValues.entrySet());
        keyValuesToSend.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Map.Entry<String, String> item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("{%s: %s}", item.getKey(), item.getValue()));
                }
            }
        });

        namespace.getItems().addAll(existingNamespaces);
        namespace.getItems().add(resources.getString("DataTransporters.Forms.SendKeyValuePairs.new"));
        namespace.getSelectionModel().selectFirst();

        newNamespace.visibleProperty().bind(namespace.getSelectionModel().selectedIndexProperty().isEqualTo(namespace.getItems().size()-1));
        newNamespace.managedProperty().bind(newNamespace.visibleProperty());

        ToggleGroup group = new ToggleGroup();
        keepExisting.setToggleGroup(group);
        replaceExisting.setToggleGroup(group);
    }

    /**
     * @return the choice of the user regarding existing key value pairs
     */
    public SendKeyValuePairsForm.Choice getSelectedChoice() {
        if (keepExisting.isSelected()) {
            return Choice.KEEP_EXISTING;
        } else {
            return Choice.REPLACE_EXITING;
        }
    }

    /**
     * @return a map of all key value pairs to send
     */
    public Map<String, String> getKeyValuePairsToSend() {
        return keyValuesToSend.getTargetItems().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
        ));
    }

    /**
     * @return the selected namespace. It can be empty
     */
    public String getSelectedNamespace() {
        if (newNamespace.isVisible()) {
            return newNamespace.getText();
        } else {
            return namespace.getValue();
        }
    }
}
