package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.List;

/**
 * Form that lets the user choose some parameters when importing annotations from an OMERO server.
 */
public class ImportAnnotationForm extends VBox {

    @FXML
    private ChoiceBox<Owner> owner;
    @FXML
    private CheckBox deleteAnnotations;
    @FXML
    private CheckBox deleteDetections;

    /**
     * Creates the annotation form.
     *
     * @param owners the owners that may own some annotations to import
     * @throws IOException if an error occurs while creating the form
     */
    public ImportAnnotationForm(List<Owner> owners) throws IOException {
        UiUtilities.loadFXML(this, ImportAnnotationForm.class.getResource("import_annotation_form.fxml"));

        owner.setConverter(new StringConverter<>() {
            @Override
            public String toString(Owner object) {
                return object == null ? "" : object.getFullName();
            }

            @Override
            public Owner fromString(String string) {
                return null;
            }
        });
        owner.getItems().add(Owner.getAllMembersOwner());
        owner.getItems().addAll(owners);
        owner.getSelectionModel().selectFirst();
    }

    /**
     * @return the owner that should own the annotations to retrieve. It can be
     * {@link Owner#getAllMembersOwner()}
     */
    public Owner getSelectedOwner() {
        return owner.getValue();
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
