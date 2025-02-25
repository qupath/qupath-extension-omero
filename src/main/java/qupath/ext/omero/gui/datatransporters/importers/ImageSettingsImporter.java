package qupath.ext.omero.gui.datatransporters.importers;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.image.ChannelSettings;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.ImageSettingsForm;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.gui.login.WaitingWindow;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.projects.Project;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

/**
 * Import image settings from an OMERO server to the currently opened image.
 * <p>
 * This class uses an {@link ImageSettingsForm} to prompt the user for parameters.
 */
public class ImageSettingsImporter implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(ImageSettingsImporter.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final QuPathGUI quPath;

    /**
     * Create the image settings importer.
     *
     * @param quPath the quPath window
     */
    public ImageSettingsImporter(QuPathGUI quPath) {
        this.quPath = quPath;
    }

    @Override
    public String getMenuTitle() {
        return resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings");
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return projectOpened || !isRGB;
    }

    @Override
    public void transportData() {
        QuPathViewer viewer = quPath.getViewer();

        if (!(viewer.getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings"),
                    resources.getString("DataTransporters.ImageSettingsImporter.notFromOMERO")
            );
            return;
        }

        ImageSettingsForm imageSettingsForm;
        try {
            imageSettingsForm = new ImageSettingsForm(
                    quPath.getProject() == null,
                    omeroImageServer.getMetadata().isRGB()
            );
        } catch (IOException e) {
            logger.error("Error when creating the image settings form", e);
            return;
        }

        boolean confirmed = Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings"),
                imageSettingsForm
        );
        List<ImageSettingsForm.Choice> selectedChoices = imageSettingsForm.getSelectedChoices();
        if (!confirmed || selectedChoices.isEmpty()) {
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.ImageSettingsImporter.importingAnnotations")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        omeroImageServer.getClient().getApisHandler().getImageSettings(omeroImageServer.getId()).exceptionally(error -> {
            logger.error("Error while retrieving image settings", error);
            return null;
        }).thenAccept(imageSettings -> Platform.runLater(() -> {
            waitingWindow.close();

            if (imageSettings == null) {
                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings"),
                        resources.getString("DataTransporters.ImageSettingsImporter.couldNotGetImage")
                );
                return;
            }

            StringBuilder successMessage = new StringBuilder();
            StringBuilder errorMessage = new StringBuilder();

            if (selectedChoices.contains(ImageSettingsForm.Choice.IMAGE_NAME)) {
                if (changeImageName(quPath, viewer.getImageData(), imageSettings.getName())) {
                    successMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.imageNameUpdated"))
                            .append("\n");
                } else {
                    errorMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.imageNameNotUpdated"))
                            .append("\n");
                }
            }

            if (selectedChoices.contains(ImageSettingsForm.Choice.CHANNEL_NAMES)) {
                if (changeChannelNames(omeroImageServer, viewer, imageSettings.getChannelSettings())) {
                    successMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.channelNamesUpdated"))
                            .append("\n");
                } else {
                    errorMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.channelNamesNotUpdated"))
                            .append("\n");
                }
            }

            if (selectedChoices.contains(ImageSettingsForm.Choice.CHANNEL_COLORS)) {
                if (changeChannelColors(omeroImageServer, viewer, imageSettings.getChannelSettings())) {
                    successMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.channelColorsUpdated"))
                            .append("\n");
                } else {
                    errorMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.channelColorsNotUpdated"))
                            .append("\n");
                }
            }

            if (selectedChoices.contains(ImageSettingsForm.Choice.CHANNEL_DISPLAY_RANGES)) {
                if (changeChannelDisplayRanges(viewer, imageSettings.getChannelSettings())) {
                    successMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.channelDisplayRangesUpdated"))
                            .append("\n");
                } else {
                    errorMessage
                            .append(resources.getString("DataTransporters.ImageSettingsImporter.channelDisplayRangesNotUpdated"))
                            .append("\n");
                }
            }

            if (!errorMessage.isEmpty()) {
                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings"),
                        errorMessage.toString()
                );
            }

            if (!successMessage.isEmpty()) {
                Dialogs.showInfoNotification(
                        resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings"),
                        successMessage.toString()
                );
            }
        }));
    }

    private static boolean changeImageName(QuPathGUI quPath, ImageData<BufferedImage> imageData, String imageName) {
        Project<BufferedImage> project = quPath.getProject();

        if (project != null && project.getEntry(imageData) != null) {
            project.getEntry(imageData).setImageName(imageName);
            return true;
        } else {
            return false;
        }
    }

    private static boolean changeChannelNames(OmeroImageServer omeroImageServer, QuPathViewer viewer, List<ChannelSettings> channelSettings) {
        List<ImageChannel> channels = omeroImageServer.getMetadata().getChannels();
        List<String> newChannelNames = channelSettings.stream().map(ChannelSettings::name).toList();

        if (channels.size() == newChannelNames.size()) {
            viewer.getImageData().updateServerMetadata(new ImageServerMetadata.Builder(omeroImageServer.getMetadata())
                    .channels(IntStream.range(0, channels.size())
                            .mapToObj(i -> ImageChannel.getInstance(newChannelNames.get(i), channels.get(i).getColor()))
                            .toList()
                    )
                    .build()
            );
            return true;
        } else {
            return false;
        }
    }

    private static boolean changeChannelColors(OmeroImageServer omeroImageServer, QuPathViewer viewer, List<ChannelSettings> channelSettings) {
        List<ImageChannel> channels = omeroImageServer.getMetadata().getChannels();
        List<Integer> newChannelColors = channelSettings.stream()
                .map(ChannelSettings::rgbColor)
                .toList();

        if (channels.size() == newChannelColors.size()) {
            viewer.getImageData().updateServerMetadata(new ImageServerMetadata.Builder(omeroImageServer.getMetadata())
                    .channels(IntStream.range(0, channels.size())
                            .mapToObj(i -> ImageChannel.getInstance(channels.get(i).getName(), newChannelColors.get(i)))
                            .toList()
                    )
                    .build()
            );
            return true;
        } else {
            return false;
        }
    }

    private static boolean changeChannelDisplayRanges(QuPathViewer viewer, List<ChannelSettings> channelSettings) {
        ImageDisplay display = viewer.getImageDisplay();
        List<ChannelDisplayInfo> channels = display.availableChannels();

        if (channels.size() == channelSettings.size()) {
            for (int i=0; i<channels.size(); i++) {
                display.setMinMaxDisplay(
                        channels.get(i),
                        (float) channelSettings.get(i).minDisplayRange(),
                        (float) channelSettings.get(i).maxDisplayRange()
                );
            }
            return true;
        } else {
            return false;
        }
    }
}
