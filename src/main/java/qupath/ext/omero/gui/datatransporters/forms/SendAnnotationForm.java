package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.VBox;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.ResourceBundle;

/**
 * Form that lets the user choose some parameters when sending annotations to an OMERO server.
 */
public class SendAnnotationForm extends VBox {

    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final String ONLY_SELECTED_ANNOTATIONS = resources.getString("DataTransporters.Forms.SendAnnotations.onlySelectedAnnotations");
    private static final String ALL_ANNOTATIONS = resources.getString("DataTransporters.Forms.SendAnnotations.allAnnotations");
    @FXML
    private CheckBox deleteExistingAnnotations;
    @FXML
    private ChoiceBox<String> selectedAnnotationChoice;
    @FXML
    private CheckBox deleteExistingMeasurements;
    @FXML
    private CheckBox sendAnnotationMeasurements;
    @FXML
    private CheckBox sendDetectionMeasurements;

    /**
     * Creates the annotation form.
     *
     * @throws IOException if an error occurs while creating the form
     */
    public SendAnnotationForm() throws IOException {
        UiUtilities.loadFXML(this, SendAnnotationForm.class.getResource("send_annotation_form.fxml"));

        selectedAnnotationChoice.getItems().setAll(ONLY_SELECTED_ANNOTATIONS, ALL_ANNOTATIONS);
        selectedAnnotationChoice.getSelectionModel().select(ALL_ANNOTATIONS);
    }

    /**
     * @return whether existing annotations on the OMERO server should be deleted
     */
    public boolean deleteExistingAnnotations() {
        return deleteExistingAnnotations.isSelected();
    }

    /**
     * @return whether only selected annotations should be sent to the server
     */
    public boolean sendOnlySelectedAnnotations() {
        return selectedAnnotationChoice.getSelectionModel().getSelectedItem().equals(ONLY_SELECTED_ANNOTATIONS);
    }

    /**
     * @return whether existing measurements should be deleted
     */
    public boolean deleteExistingMeasurements() {
        return deleteExistingMeasurements.isSelected();
    }

    /**
     * @return whether annotation measurements should be sent
     */
    public boolean sendAnnotationMeasurements() {
        return sendAnnotationMeasurements.isSelected();
    }

    /**
     * @return whether detection measurements should be sent
     */
    public boolean sendDetectionMeasurements() {
        return sendDetectionMeasurements.isSelected();
    }
}
