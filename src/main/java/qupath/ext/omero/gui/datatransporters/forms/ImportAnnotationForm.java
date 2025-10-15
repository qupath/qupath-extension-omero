package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.VBox;
import qupath.ext.omero.Utils;
import qupath.ext.omero.gui.UiUtils;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Form that lets the user choose some parameters when importing annotations from an OMERO server.
 */
public class ImportAnnotationForm extends VBox {

    private static final ResourceBundle resources = Utils.getResources();
    private final List<String> ownerNames;
    @FXML
    private ChoiceBox<String> owner;
    @FXML
    private CheckBox deleteAnnotations;
    @FXML
    private CheckBox deleteDetections;

    /**
     * Creates the annotation form.
     *
     * @param ownerNames the names of the owners that own some annotations to import. Must be non-empty
     * @throws IOException if an error occurs while creating the form
     * @throws IllegalArgumentException if the provided list of owner names is empty
     */
    public ImportAnnotationForm(List<String> ownerNames) throws IOException {
        if (ownerNames.isEmpty()) {
            throw new IllegalArgumentException("The provided list of owner names is empty");
        }

        this.ownerNames = ownerNames;

        UiUtils.loadFXML(this, ImportAnnotationForm.class.getResource("import_annotation_form.fxml"));

        if (ownerNames.size() > 1) {
            owner.getItems().add(resources.getString("DataTransporters.Forms.ImportAnnotations.all"));
        }
        owner.getItems().addAll(ownerNames);
        owner.getSelectionModel().selectFirst();
    }

    /**
     * @return the full names of the owners that should own the annotations to import. This is
     * guaranteed not to be empty
     */
    public List<String> getSelectedOwner() {
        if (ownerNames.size() > 1 && owner.getSelectionModel().getSelectedIndex() == 0) {
            return ownerNames;
        } else {
            return List.of(owner.getValue());
        }
    }

    /**
     * @return whether existing QuPath annotations should be deleted
     */
    public boolean deleteCurrentAnnotations() {
        return deleteAnnotations.isSelected();
    }

    /**
     * @return whether existing QuPath detections should be deleted
     */
    public boolean deleteCurrentDetections() {
        return deleteDetections.isSelected();
    }
}
