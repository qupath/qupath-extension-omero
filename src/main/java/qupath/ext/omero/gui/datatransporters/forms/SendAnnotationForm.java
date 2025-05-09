package qupath.ext.omero.gui.datatransporters.forms;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Form that lets the user choose some parameters when sending annotations to an OMERO server.
 */
public class SendAnnotationForm extends VBox {

    private static final ResourceBundle resources = Utils.getResources();
    private static final String ONLY_SELECTED_ANNOTATIONS = resources.getString("DataTransporters.Forms.SendAnnotations.onlySelectedAnnotations");
    private static final String ALL_ANNOTATIONS = resources.getString("DataTransporters.Forms.SendAnnotations.allAnnotations");
    private final List<Owner> owners;
    @FXML
    private ChoiceBox<String> selectedAnnotationChoice;
    @FXML
    private CheckBox deleteExistingAnnotations;
    @FXML
    private ChoiceBox<Owner> ownerAnnotations;
    @FXML
    private CheckBox deleteExistingMeasurements;
    @FXML
    private ChoiceBox<Owner> ownerMeasurements;
    @FXML
    private CheckBox sendAnnotationMeasurements;
    @FXML
    private CheckBox sendDetectionMeasurements;

    /**
     * Creates the annotation form.
     *
     * @param owners the owners that may own some annotations to delete
     * @param projectOpened whether a project is currently open
     * @param annotationsExist whether annotations exist on the current image
     * @param detectionExist whether detections exist on the current image
     * @throws IOException when an error occurs while creating the form
     */
    public SendAnnotationForm(List<Owner> owners, boolean projectOpened, boolean annotationsExist, boolean detectionExist) throws IOException {
        this.owners = owners;

        UiUtilities.loadFXML(this, SendAnnotationForm.class.getResource("send_annotation_form.fxml"));

        selectedAnnotationChoice.getItems().setAll(ONLY_SELECTED_ANNOTATIONS, ALL_ANNOTATIONS);
        selectedAnnotationChoice.getSelectionModel().select(ALL_ANNOTATIONS);

        setUpOwnerChoiceBox(ownerAnnotations, deleteExistingAnnotations.selectedProperty());
        setUpOwnerChoiceBox(ownerMeasurements, deleteExistingMeasurements.selectedProperty());

        deleteExistingMeasurements.setSelected(projectOpened);

        sendAnnotationMeasurements.setSelected(projectOpened && annotationsExist);
        sendAnnotationMeasurements.setDisable(!projectOpened || !annotationsExist);

        sendDetectionMeasurements.setSelected(projectOpened && detectionExist);
        sendDetectionMeasurements.setDisable(!projectOpened || !detectionExist);
    }

    /**
     * @return whether only selected annotations should be sent to the server
     */
    public boolean sendOnlySelectedAnnotations() {
        return selectedAnnotationChoice.getSelectionModel().getSelectedItem().equals(ONLY_SELECTED_ANNOTATIONS);
    }

    /**
     * @return whether existing annotations on the OMERO server should be deleted
     */
    public boolean deleteExistingAnnotations() {
        return deleteExistingAnnotations.isSelected();
    }

    /**
     * @return the list of owners whose OMERO annotations should be deleted (if {@link #deleteExistingAnnotations()} is
     * true). Can be empty if no owners were provided to this form
     */
    public List<Owner> getSelectedOwnersOfDeletedAnnotations() {
        return getSelectedOwners(ownerAnnotations);
    }

    /**
     * @return whether existing measurements should be deleted
     */
    public boolean deleteExistingMeasurements() {
        return deleteExistingMeasurements.isSelected();
    }

    /**
     * @return the list of owners whose OMERO measurements should be deleted (if {@link #deleteExistingMeasurements()} is
     * true). Can be empty if no owners were provided to this form
     */
    public List<Owner> getSelectedOwnersOfDeletedMeasurements() {
        return getSelectedOwners(ownerMeasurements);
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

    private void setUpOwnerChoiceBox(ChoiceBox<Owner> choiceBox, ReadOnlyBooleanProperty enableProperty) {
        choiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Owner object) {
                return object == null ? "" : object.getFullName();
            }

            @Override
            public Owner fromString(String string) {
                return null;
            }
        });

        if (owners.size() > 1) {
            choiceBox.getItems().add(Owner.getAllMembersOwner());
        }
        choiceBox.getItems().addAll(owners);
        choiceBox.getSelectionModel().selectFirst();

        choiceBox.disableProperty().bind(Bindings.not(enableProperty));
    }

    private List<Owner> getSelectedOwners(ChoiceBox<Owner> choiceBox) {
        if (owners.size() > 1 && choiceBox.getSelectionModel().getSelectedIndex() == 0) {
            return owners;
        } else {
            if (choiceBox.getValue() == null) {
                return List.of();
            } else {
                return List.of(choiceBox.getValue());
            }
        }
    }
}
