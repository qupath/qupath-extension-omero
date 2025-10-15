package qupath.ext.omero.gui.datatransporters.forms;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import qupath.ext.omero.gui.UiUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Form that lets the user choose what image settings parameters
 * to change.
 */
public class ImageSettingsForm extends VBox {

    @FXML
    private CheckBox imageName;
    @FXML
    private CheckBox channelNames;
    @FXML
    private CheckBox channelColors;
    @FXML
    private CheckBox channelDisplayRanges;

    /**
     * Describes which parameters to change.
     */
    public enum Choice {
        IMAGE_NAME,
        CHANNEL_NAMES,
        CHANNEL_COLORS,
        CHANNEL_DISPLAY_RANGES
    }
    private final Map<CheckBox, Choice> checkBoxChoiceMap;

    /**
     * Creates the image settings form.
     *
     * @param noCurrentProject whether there is no QuPath project currently opened. This is
     *                         needed to determine if the image name can be changed
     * @param isRGB whether the current image uses the RGB format. This is needed to determine if the channel
     *              settings can be changed
     * @throws IOException if an error occurs while creating the form
     */
    public ImageSettingsForm(boolean noCurrentProject, boolean isRGB) throws IOException {
        UiUtils.loadFXML(this, ImageSettingsForm.class.getResource("image_settings_form.fxml"));

        checkBoxChoiceMap = Map.of(
                imageName, Choice.IMAGE_NAME,
                channelNames, Choice.CHANNEL_NAMES,
                channelColors, Choice.CHANNEL_COLORS,
                channelDisplayRanges, Choice.CHANNEL_DISPLAY_RANGES
        );

        if (noCurrentProject) {
            imageName.setSelected(false);
            imageName.setDisable(true);
        }

        if (isRGB) {
            channelNames.setSelected(false);
            channelNames.setDisable(true);

            channelColors.setSelected(false);
            channelColors.setDisable(true);

            channelDisplayRanges.setSelected(false);
            channelDisplayRanges.setDisable(true);
        }
    }

    /**
     * @return the selected choices
     */
    public List<Choice> getSelectedChoices() {
        return checkBoxChoiceMap.entrySet().stream()
                .filter(entry -> entry.getKey().isSelected())
                .map(Map.Entry::getValue)
                .toList();
    }
}
