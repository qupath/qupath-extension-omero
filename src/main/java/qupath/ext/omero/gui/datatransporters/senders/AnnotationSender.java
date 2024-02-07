package qupath.ext.omero.gui.datatransporters.senders;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.SendAnnotationForm;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 *     Send QuPath annotations from the currently opened image to an OMERO server.
 * </p>
 * <p>
 *     Here, an annotation refers to a QuPath annotation (a path object)
 *     and <b>not</b> an OMERO annotation (some metadata attached to images for example).
 * </p>
 * <p>
 *     This class uses an {@link SendAnnotationForm} to prompt the user for parameters.
 * </p>
 */
public class AnnotationSender implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationSender.class);
    private static final ResourceBundle resources = UiUtilities.getResources();
    private enum MessageType {
        SUCCESS,
        ERROR
    }
    private enum Request {
        SEND_ANNOTATIONS,
        DELETE_EXISTING_MEASUREMENTS,
        SEND_ANNOTATION_MEASUREMENTS,
        SEND_DETECTION_MEASUREMENTS
    }

    @Override
    public String getMenuTitle() {
        return resources.getString("DataTransporters.AnnotationsSender.sendAnnotations");
    }

    @Override
    public boolean canTransportData(boolean projectOpened, boolean isRGB) {
        return true;
    }

    @Override
    public void transportData() {
        QuPathViewer viewer = QuPathGUI.getInstance().getViewer();

        if (viewer != null && viewer.getServer() instanceof OmeroImageServer omeroImageServer) {
            SendAnnotationForm annotationForm;
            try {
                annotationForm = new SendAnnotationForm();
            } catch (IOException e) {
                logger.error("Error when creating the annotation form", e);
                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                        e.getLocalizedMessage()
                );
                return;
            }

            boolean confirmed = Dialogs.showConfirmDialog(
                    resources.getString("DataTransporters.AnnotationsSender.dataToSend"),
                    annotationForm
            );

            if (confirmed) {
                Collection<PathObject> annotations = getAnnotations(viewer, annotationForm.sendOnlySelectedAnnotations());

                if (annotations.isEmpty()) {
                    Dialogs.showErrorMessage(
                            resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                            resources.getString("DataTransporters.AnnotationsSender.noAnnotations")
                    );
                } else {
                    // The potential deletion of existing measurements must happen before the other requests
                    CompletableFuture<Boolean> deleteExistingMeasurementsRequest = annotationForm.deleteExistingMeasurements() ?
                            omeroImageServer.getClient().getApisHandler().deleteAttachments(omeroImageServer.getId(), Image.class) :
                            CompletableFuture.completedFuture(false);

                    deleteExistingMeasurementsRequest.thenApplyAsync(measurementsDeleted -> {
                        Map<Request, CompletableFuture<Boolean>> requests = createRequests(
                                omeroImageServer,
                                annotations,
                                annotationForm.deleteExistingAnnotations(),
                                annotationForm.sendAnnotationMeasurements(),
                                annotationForm.sendDetectionMeasurements()
                        );

                        if (annotationForm.deleteExistingMeasurements()) {
                            requests.put(
                                    Request.DELETE_EXISTING_MEASUREMENTS,
                                    CompletableFuture.completedFuture(measurementsDeleted)
                            );
                        }

                        return requests.entrySet().stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().join()
                        ));
                    }).thenAccept(statuses -> Platform.runLater(() -> {
                        String successMessage = createMessageFromResponses(statuses, MessageType.SUCCESS);
                        String errorMessage = createMessageFromResponses(statuses, MessageType.ERROR);

                        if (!errorMessage.isEmpty()) {
                            errorMessage += String.format("\n\n%s", resources.getString("DataTransporters.AnnotationsSender.notAllOperationsSucceeded"));
                            Dialogs.showErrorMessage(
                                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                                    errorMessage
                            );
                        }

                        if (!successMessage.isEmpty()) {
                            Dialogs.showInfoNotification(
                                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                                    successMessage
                            );
                        }
                    }));
                }
            }
        } else {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                    resources.getString("DataTransporters.AnnotationsSender.notFromOMERO")
            );
        }
    }

    private static Collection<PathObject> getAnnotations(QuPathViewer viewer, boolean onlySelected) {
        if (onlySelected) {
            return viewer.getAllSelectedObjects().stream().filter(e -> !e.isDetection()).toList();
        } else {
            return viewer.getHierarchy() == null ? List.of() : viewer.getHierarchy().getAnnotationObjects();
        }
    }

    private static Map<Request, CompletableFuture<Boolean>> createRequests(
            OmeroImageServer omeroImageServer,
            Collection<PathObject> annotations,
            boolean deleteExistingAnnotations,
            boolean sendAnnotationMeasurements,
            boolean sendDetectionMeasurements
    ) {
        Map<Request, CompletableFuture<Boolean>> requests = new HashMap<>();

        requests.put(Request.SEND_ANNOTATIONS, CompletableFuture.supplyAsync(() -> omeroImageServer.sendPathObjects(
                annotations,
                deleteExistingAnnotations
        )));

        if (sendAnnotationMeasurements) {
            requests.put(
                    Request.SEND_ANNOTATION_MEASUREMENTS,
                    getMeasureRequest(PathAnnotationObject.class, omeroImageServer)
            );
        }

        if (sendDetectionMeasurements) {
            requests.put(
                    Request.SEND_DETECTION_MEASUREMENTS,
                    getMeasureRequest(PathDetectionObject.class, omeroImageServer)
            );
        }

        return requests;
    }

    private static CompletableFuture<Boolean> getMeasureRequest(
            Class<? extends qupath.lib.objects.PathObject> exportType,
            OmeroImageServer omeroImageServer
    ) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        if (qupath.getProject() != null) {
            Project<BufferedImage> project = qupath.getProject();

            try (OutputStream outputStream = new ByteArrayOutputStream()) {
                QuPathViewer viewer = qupath.getViewer();
                ProjectImageEntry<BufferedImage> entry = project.getEntry(viewer.getImageData());

                // The image must be saved because non saved measures won't be exported
                entry.saveImageData(viewer.getImageData());

                new MeasurementExporter()
                        .exportType(exportType)
                        .imageList(List.of(entry))
                        .separator(",")
                        .exportMeasurements(outputStream);

                String title = String.format(
                        "%s_%s_%s.csv",
                        exportType.equals(PathAnnotationObject.class) ? "QP annotation table" : "QP detection table",
                        project.getName().split("/")[0],
                        new SimpleDateFormat("yyyyMMdd-HH'h'mm'm'ss").format(new Date())
                );

                return omeroImageServer.getClient().getApisHandler().sendAttachment(
                        omeroImageServer.getId(),
                        Image.class,
                        title,
                        outputStream.toString()
                );
            } catch (IOException e) {
                logger.warn("Error when reading annotation measurements", e);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    private static String createMessageFromResponses(Map<Request, Boolean> statuses, MessageType messageType) {
        return statuses.entrySet().stream()
                .filter(entry -> switch (messageType) {
                    case SUCCESS -> entry.getValue();
                    case ERROR -> !entry.getValue();
                })
                .map(entry -> MessageFormat.format(
                        resources.getString(entry.getValue() ?
                                "DataTransporters.AnnotationsSender.operationSucceeded" :
                                "DataTransporters.AnnotationsSender.operationFailed"
                        ),
                        resources.getString(switch (entry.getKey()) {
                            case SEND_ANNOTATIONS -> "DataTransporters.AnnotationsSender.sendAnnotations";
                            case DELETE_EXISTING_MEASUREMENTS -> "DataTransporters.AnnotationsSender.deleteExistingMeasurements";
                            case SEND_ANNOTATION_MEASUREMENTS -> "DataTransporters.AnnotationsSender.sendAnnotationMeasurements";
                            case SEND_DETECTION_MEASUREMENTS -> "DataTransporters.AnnotationsSender.sendDetectionMeasurements";
                        })
                ))
                .collect(Collectors.joining("\n"));
    }
}
