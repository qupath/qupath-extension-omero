package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.VBox;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.commonentities.SimpleEntity;
import qupath.ext.omero.core.apis.json.permissions.Experimenter;
import qupath.ext.omero.gui.UiUtils;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Form that lets the user choose some parameters when importing annotations from an OMERO server.
 */
public class ImportAnnotationForm extends VBox {

    private static final ResourceBundle resources = Utils.getResources();
    private static final SimpleEntity ALL_EXPERIMENTERS = new SimpleEntity(
            Experimenter.getAllExperimenters().getId(),
            Experimenter.getAllExperimenters().getFullName()
    );
    private final List<SimpleEntity> owners;
    @FXML
    private ChoiceBox<SimpleEntity> owner;
    @FXML
    private CheckBox deleteAnnotations;
    @FXML
    private CheckBox deleteDetections;

    /**
     * Creates the annotation form.
     *
     * @param owners the ID and the names of the owners that own some annotations to import. Must be non-empty
     * @throws IOException if an error occurs while creating the form
     * @throws IllegalArgumentException if the provided list of owner names is empty
     */
    public ImportAnnotationForm(List<SimpleEntity> owners) throws IOException {
        if (owners.isEmpty()) {
            throw new IllegalArgumentException("The provided list of owner names is empty");
        }

        this.owners = owners;

        UiUtils.loadFXML(this, ImportAnnotationForm.class.getResource("import_annotation_form.fxml"));

        if (owners.size() > 1) {
            owner.getItems().add(ALL_EXPERIMENTERS);
        }
        owner.getItems().addAll(owners);
        owner.getSelectionModel().selectFirst();
    }

    /**
     * @return the owners that should own the annotations to import. This is guaranteed not to be empty
     */
    public List<SimpleEntity> getSelectedOwner() {
        if (ALL_EXPERIMENTERS.equals(owner.getSelectionModel().getSelectedItem())) {
            return owners;
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
