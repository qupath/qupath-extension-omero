package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;

/**
 * Form that lets the user choose some parameters when importing annotations from an OMERO server.
 */
public class ImportAnnotationForm extends VBox {

    @FXML
    private CheckBox deleteAnnotations;
    @FXML
    private CheckBox deleteDetections;

    /**
     * Creates the annotation form.
     *
     * @throws IOException if an error occurs while creating the form
     */
    public ImportAnnotationForm() throws IOException {
        UiUtilities.loadFXML(this, ImportAnnotationForm.class.getResource("import_annotation_form.fxml"));
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
