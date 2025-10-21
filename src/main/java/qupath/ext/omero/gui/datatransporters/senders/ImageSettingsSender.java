package qupath.ext.omero.gui.datatransporters.senders;

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
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageChannel;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Send image settings from the currently opened image to an OMERO server.
 * <p>
 * This class uses an {@link ImageSettingsForm} to prompt the user for parameters.
 */
public class ImageSettingsSender implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(ImageSettingsSender.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final QuPathGUI quPath;

    /**
     * Create the image settings sender.
     *
     * @param quPath the quPath window
     */
    public ImageSettingsSender(QuPathGUI quPath) {
        logger.debug("Creating image settings sender for {}", quPath);
        this.quPath = quPath;
    }

    @Override
    public String getMenuTitle() {
        return resources.getString("DataTransporters.ImageSettingsSender.sendImageSettings");
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return projectOpened || !isRGB;
    }

    @Override
    public void transportData() {
        logger.debug("Attempting to send image settings to OMERO");

        QuPathViewer viewer = quPath.getViewer();
        if (viewer == null || !(viewer.getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.ImageSettingsSender.sendImageSettings"),
                    resources.getString("DataTransporters.ImageSettingsSender.notFromOMERO")
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
                resources.getString("DataTransporters.ImageSettingsSender.dataToSend"),
                imageSettingsForm
        );
        if (!confirmed || imageSettingsForm.getSelectedChoices().isEmpty()) {
            logger.debug("Sending image settings dialog not confirmed or no selected option. Not sending image settings");
            return;
        }

        Map<ImageSettingsForm.Choice, CompletableFuture<Void>> requests = createRequests(
                imageSettingsForm.getSelectedChoices(),
                omeroImageServer,
                viewer
        );

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.ImageSettingsSender.sendingImageSettings")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window");
            return;
        }
        waitingWindow.show();

        CompletableFuture.supplyAsync(() -> {
            Map<ImageSettingsForm.Choice, Throwable> requestToErrors = new HashMap<>();
            for (var entry: requests.entrySet()) {
                try {
                    requestToErrors.put(
                            entry.getKey(),
                            entry.getValue().handle((v, error) -> error).get()
                    );
                } catch (ExecutionException | InterruptedException e) {
                    requestToErrors.put(entry.getKey(), e);
                }
            }
            return requestToErrors;
        }).exceptionally(error -> {
            logger.error("Unexpected error while sending image settings", error);
            return Map.of();
        }).thenAccept(errors -> Platform.runLater(() -> {
            waitingWindow.close();

            logErrors(errors);

            String successMessage = createMessageFromResponses(errors, true);
            String errorMessage = createMessageFromResponses(errors, false);

            if (!errorMessage.isEmpty()) {
                errorMessage += String.format("\n\n%s", resources.getString("DataTransporters.ImageSettingsSender.notAllParametersSent"));
                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.ImageSettingsSender.sendImageSettings"),
                        errorMessage
                );
            }

            if (!successMessage.isEmpty()) {
                Dialogs.showInfoNotification(
                        resources.getString("DataTransporters.ImageSettingsSender.sendImageSettings"),
                        successMessage
                );
            }
        }));
    }

    @Override
    public String toString() {
        return String.format("Image settings sender for %s", quPath);
    }

    private static Map<ImageSettingsForm.Choice, CompletableFuture<Void>> createRequests(
            List<ImageSettingsForm.Choice> selectedChoices,
            OmeroImageServer omeroImageServer,
            QuPathViewer viewer
    ) {
        return selectedChoices.stream().collect(Collectors.toMap(Function.identity(), choice -> switch (choice) {
            case IMAGE_NAME -> {
                logger.debug("Changing image name of image with ID {} to {}", omeroImageServer.getId(), omeroImageServer.getMetadata().getName());

                yield omeroImageServer.getClient().getApisHandler().changeImageName(
                    omeroImageServer.getId(),
                    omeroImageServer.getMetadata().getName()
            );
            }
            case CHANNEL_NAMES -> {
                logger.debug("Changing channel names of image with ID {} to {}", omeroImageServer.getId(), omeroImageServer.getMetadata().getChannels());

                yield omeroImageServer.getClient().getApisHandler().changeChannelNames(
                    omeroImageServer.getId(),
                    omeroImageServer.getMetadata().getChannels().stream().map(ImageChannel::getName).toList()
            );
            }
            case CHANNEL_COLORS -> {
                logger.debug("Changing channel colors of image with ID {} to {}", omeroImageServer.getId(), omeroImageServer.getMetadata().getChannels());

                yield omeroImageServer.getClient().getApisHandler().changeChannelColors(
                    omeroImageServer.getId(),
                    omeroImageServer.getMetadata().getChannels().stream()
                            .map(ImageChannel::getColor)
                            .toList()
            );
            }
            case CHANNEL_DISPLAY_RANGES -> {
                logger.debug("Changing channel display ranges of image with ID {} to {}", omeroImageServer.getId(), viewer.getImageDisplay().availableChannels());

                yield omeroImageServer.getClient().getApisHandler().changeChannelDisplayRanges(
                    omeroImageServer.getId(),
                    viewer.getImageDisplay().availableChannels().stream()
                            .map(channel -> new ChannelSettings(channel.getMinDisplay(), channel.getMaxDisplay()))
                            .toList()
            );
            }
        }));
    }

    private static void logErrors(Map<ImageSettingsForm.Choice, Throwable> errors) {
        for (Map.Entry<ImageSettingsForm.Choice, Throwable> error: errors.entrySet()) {
            if (error.getValue() == null) {
                logger.debug(
                        "The operation sending {} succeeded",
                        switch (error.getKey()) {
                            case IMAGE_NAME -> "image name";
                            case CHANNEL_NAMES -> "channel names";
                            case CHANNEL_COLORS -> "channel colors";
                            case CHANNEL_DISPLAY_RANGES -> "channel display ranges";
                        }
                );
            } else {
                logger.error(
                        "Error while sending {}",
                        switch (error.getKey()) {
                            case IMAGE_NAME -> "image name";
                            case CHANNEL_NAMES -> "channel names";
                            case CHANNEL_COLORS -> "channel colors";
                            case CHANNEL_DISPLAY_RANGES -> "channel display ranges";
                        },
                        error.getValue()
                );
            }
        }
    }

    private static String createMessageFromResponses(Map<ImageSettingsForm.Choice, Throwable> errors, boolean isSuccess) {
        return errors.entrySet().stream()
                .filter(entry -> {
                    if (isSuccess) {
                        return entry.getValue() == null;
                    } else {
                        return entry.getValue() != null;
                    }
                })
                .map(entry -> MessageFormat.format(
                        resources.getString(isSuccess ?
                                "DataTransporters.ImageSettingsSender.parameterSent" :
                                "DataTransporters.ImageSettingsSender.parameterNotSent"
                        ),
                        resources.getString(switch (entry.getKey()) {
                            case IMAGE_NAME -> "DataTransporters.ImageSettingsSender.imageName";
                            case CHANNEL_NAMES -> "DataTransporters.ImageSettingsSender.channelNames";
                            case CHANNEL_COLORS -> "DataTransporters.ImageSettingsSender.channelColors";
                            case CHANNEL_DISPLAY_RANGES -> "DataTransporters.ImageSettingsSender.channelDisplayRanges";
                        })
                ))
                .collect(Collectors.joining("\n"));
    }
}
