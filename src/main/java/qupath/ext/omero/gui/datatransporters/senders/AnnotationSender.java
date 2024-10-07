package qupath.ext.omero.gui.datatransporters.senders;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.shapes.Shape;
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
    private final QuPathGUI quPath;
    private enum Request {
        SEND_ANNOTATIONS,
        DELETE_EXISTING_MEASUREMENTS,
        SEND_ANNOTATION_MEASUREMENTS,
        SEND_DETECTION_MEASUREMENTS
    }

    public AnnotationSender(QuPathGUI quPath) {
        this.quPath = quPath;
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
        QuPathViewer viewer = quPath.getViewer();

        if (viewer == null || !(viewer.getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                    resources.getString("DataTransporters.AnnotationsSender.notFromOMERO")
            );
            return;
        }

        SendAnnotationForm annotationForm;
        try {
            annotationForm = new SendAnnotationForm(
                    quPath.getProject() != null,
                    !viewer.getImageData().getHierarchy().getAnnotationObjects().isEmpty(),
                    !viewer.getImageData().getHierarchy().getDetectionObjects().isEmpty()
            );
        } catch (IOException e) {
            logger.error("Error when creating the annotation form", e);
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                    e.getLocalizedMessage()
            );
            return;
        }

        if (!Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.AnnotationsSender.dataToSend"),
                annotationForm
        )) {
            return;
        }

        Collection<PathObject> annotations = getAnnotations(viewer, annotationForm.sendOnlySelectedAnnotations());
        if (annotations.isEmpty()) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                    resources.getString("DataTransporters.AnnotationsSender.noAnnotations")
            );
            return;
        }

        // The potential deletion of existing measurements must happen before other requests
        CompletableFuture<Void> deleteExistingMeasurementsRequest = annotationForm.deleteExistingMeasurements() ?
                omeroImageServer.getClient().getApisHandler().deleteAttachments(omeroImageServer.getId(), Image.class) :
                CompletableFuture.completedFuture(null);

        deleteExistingMeasurementsRequest.handle((v, error) -> {
            Map<Request, CompletableFuture<Void>> requests = createRequests(
                    quPath,
                    omeroImageServer,
                    annotations,
                    annotationForm.deleteExistingAnnotations(),
                    annotationForm.sendAnnotationMeasurements(),
                    annotationForm.sendDetectionMeasurements()
            );

            if (annotationForm.deleteExistingMeasurements()) {
                requests.put(
                        Request.DELETE_EXISTING_MEASUREMENTS,
                        error == null ? CompletableFuture.completedFuture(null) : CompletableFuture.failedFuture(error)
                );
            }

            return requests.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().handle((vRequests, errorRequests) -> errorRequests).join()
            ));
        }).thenAccept(errors -> Platform.runLater(() -> {
            logErrors(errors);

            String successMessage = createMessageFromResponses(errors, true);
            String errorMessage = createMessageFromResponses(errors, false);

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

    private static Collection<PathObject> getAnnotations(QuPathViewer viewer, boolean onlySelected) {
        if (onlySelected) {
            return viewer.getAllSelectedObjects().stream().filter(PathObject::isAnnotation).toList();
        } else {
            return viewer.getHierarchy() == null ? List.of() : viewer.getHierarchy().getAnnotationObjects();
        }
    }

    private static Map<Request, CompletableFuture<Void>> createRequests(
            QuPathGUI quPath,
            OmeroImageServer omeroImageServer,
            Collection<PathObject> annotations,
            boolean deleteExistingAnnotations,
            boolean sendAnnotationMeasurements,
            boolean sendDetectionMeasurements
    ) {
        Map<Request, CompletableFuture<Void>> requests = new HashMap<>();

        requests.put(
                Request.SEND_ANNOTATIONS,
                omeroImageServer.getClient().getApisHandler().writeROIs(
                        omeroImageServer.getId(),
                        annotations.stream()
                                .map(Shape::createFromPathObject)
                                .flatMap(List::stream)
                                .toList(),
                        deleteExistingAnnotations
                )
        );

        if (sendAnnotationMeasurements) {
            requests.put(
                    Request.SEND_ANNOTATION_MEASUREMENTS,
                    getSendMeasurementsRequest(quPath, PathAnnotationObject.class, omeroImageServer)
            );
        }

        if (sendDetectionMeasurements) {
            requests.put(
                    Request.SEND_DETECTION_MEASUREMENTS,
                    getSendMeasurementsRequest(quPath, PathDetectionObject.class, omeroImageServer)
            );
        }

        return requests;
    }

    private static CompletableFuture<Void> getSendMeasurementsRequest(
            QuPathGUI quPath,
            Class<? extends qupath.lib.objects.PathObject> exportType,
            OmeroImageServer omeroImageServer
    ) {
        if (quPath.getProject() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("There is no project currently open"));
        }
        Project<BufferedImage> project = quPath.getProject();

        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            QuPathViewer viewer = quPath.getViewer();
            ProjectImageEntry<BufferedImage> entry = project.getEntry(viewer.getImageData());

            if (entry.readHierarchy().getObjects(List.of(entry.readHierarchy().getRootObject()), exportType).isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        String.format("There is no objects of type %s to export", exportType)
                ));
            }

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
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void logErrors(Map<Request, Throwable> errors) {
        for (Map.Entry<Request, Throwable> error: errors.entrySet()) {
            if (error.getValue() != null) {
                logger.error(String.format(
                        "Error while %s",
                        switch (error.getKey()) {
                            case SEND_ANNOTATIONS -> "sending annotations";
                            case DELETE_EXISTING_MEASUREMENTS -> "deleting existing measurements";
                            case SEND_ANNOTATION_MEASUREMENTS -> "sending annotations measurements";
                            case SEND_DETECTION_MEASUREMENTS -> "sending detection measurements";
                        }
                ), error.getValue());
            }
        }
    }

    private static String createMessageFromResponses(Map<Request, Throwable> statuses, boolean isSuccess) {
        return statuses.entrySet().stream()
                .filter(entry -> {
                    if (isSuccess) {
                        return entry.getValue() == null;
                    } else {
                        return entry.getValue() != null;
                    }
                })
                .map(entry -> MessageFormat.format(
                        resources.getString(isSuccess ?
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
