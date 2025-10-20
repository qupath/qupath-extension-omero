package qupath.ext.omero.gui.datatransporters.importers;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.commonentities.ChannelSettings;
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
        logger.debug("Creating image settings importer for {}", quPath);
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
        logger.debug("Attempting to import image settings from OMERO");

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
            logger.debug("Importing image settings dialog not confirmed or no choice selected. Not importing image settings");
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

        logger.debug("Getting image settings from image with ID {}", omeroImageServer.getId());
        omeroImageServer.getClient().getApisHandler().getImageData(omeroImageServer.getId()).whenComplete((imageData, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (imageData == null) {
                logger.error("Error while retrieving image settings. Cannot import image settings", error);

                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.ImageSettingsImporter.importImageSettings"),
                        resources.getString("DataTransporters.ImageSettingsImporter.couldNotGetImage")
                );
                return;
            }
            logger.debug("Got image settings {} for image with ID {}", imageData, omeroImageServer.getId());

            StringBuilder successMessage = new StringBuilder();
            StringBuilder errorMessage = new StringBuilder();

            if (selectedChoices.contains(ImageSettingsForm.Choice.IMAGE_NAME)) {
                if (changeImageName(quPath, viewer.getImageData(), imageData.getName())) {
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
                if (changeChannelNames(omeroImageServer, viewer, imageData.getChannelSettings())) {
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
                if (changeChannelColors(omeroImageServer, viewer, imageData.getChannelSettings())) {
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
                if (changeChannelDisplayRanges(viewer, imageData.getChannelSettings())) {
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

    @Override
    public String toString() {
        return String.format("Image settings importer for %s", quPath);
    }

    private static boolean changeImageName(QuPathGUI quPath, ImageData<BufferedImage> imageData, String imageName) {
        Project<BufferedImage> project = quPath.getProject();

        if (project == null || project.getEntry(imageData) == null) {
            logger.warn("Current project null or project entry of {} null. Cannot change image name", imageData);

            return false;
        } else {
            logger.debug("Changing image name of {} to {}", project.getEntry(imageData), imageName);

            project.getEntry(imageData).setImageName(imageName);
            return true;
        }
    }

    private static boolean changeChannelNames(OmeroImageServer omeroImageServer, QuPathViewer viewer, List<ChannelSettings> channelSettings) {
        List<ImageChannel> channels = omeroImageServer.getMetadata().getChannels();

        if (channels.size() == channelSettings.size()) {
            List<ImageChannel> newChannels = IntStream.range(0, channels.size())
                    .mapToObj(i -> ImageChannel.getInstance(channelSettings.get(i).name(), channels.get(i).getColor()))
                    .toList();
            logger.debug("Updating channel names from {} to {}", channels, newChannels);

            viewer.getImageData().updateServerMetadata(new ImageServerMetadata.Builder(omeroImageServer.getMetadata())
                    .channels(newChannels)
                    .build()
            );
            return true;
        } else {
            logger.warn(
                    "The number of channels returned by OMERO {} doesn't match the number of channels of the image {}. Cannot change channel names",
                    channelSettings,
                    channels
            );
            return false;
        }
    }

    private static boolean changeChannelColors(OmeroImageServer omeroImageServer, QuPathViewer viewer, List<ChannelSettings> channelSettings) {
        List<ImageChannel> channels = omeroImageServer.getMetadata().getChannels();

        if (channels.size() == channelSettings.size()) {
            List<ImageChannel> newChannels = IntStream.range(0, channels.size())
                    .mapToObj(i -> ImageChannel.getInstance(channels.get(i).getName(), channelSettings.get(i).rgbColor()))
                    .toList();
            logger.debug("Updating channel colors from {} to {}", channels, newChannels);

            viewer.getImageData().updateServerMetadata(new ImageServerMetadata.Builder(omeroImageServer.getMetadata())
                    .channels(newChannels)
                    .build()
            );
            return true;
        } else {
            logger.warn(
                    "The number of channels returned by OMERO {} doesn't match the number of channels of the image {}. Cannot change channel colors",
                    channelSettings,
                    channels
            );
            return false;
        }
    }

    private static boolean changeChannelDisplayRanges(QuPathViewer viewer, List<ChannelSettings> channelSettings) {
        ImageDisplay display = viewer.getImageDisplay();
        List<ChannelDisplayInfo> channels = display.availableChannels();

        if (channels.size() == channelSettings.size()) {
            for (int i=0; i<channels.size(); i++) {
                logger.debug(
                        "Setting min display of {} to {} and max to {}",
                        channels.get(i),
                        channelSettings.get(i).minDisplayRange(),
                        channelSettings.get(i).maxDisplayRange()
                );

                display.setMinMaxDisplay(
                        channels.get(i),
                        (float) channelSettings.get(i).minDisplayRange(),
                        (float) channelSettings.get(i).maxDisplayRange()
                );
            }
            return true;
        } else {
            logger.warn(
                    "The number of channels returned by OMERO {} doesn't match the number of channels of the image {}. Cannot change channel display ranges",
                    channelSettings,
                    channels
            );
            return false;
        }
    }
}
