package qupath.ext.omero.gui.datatransporters.senders;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.Utils;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.gui.datatransporters.DataTransporter;
import qupath.ext.omero.gui.datatransporters.forms.SendAnnotationForm;
import qupath.ext.omero.core.imageserver.OmeroImageServer;
import qupath.ext.omero.gui.login.WaitingWindow;
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
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Send QuPath annotations from the currently opened image to an OMERO server.
 * <p>
 * Here, an annotation refers to a QuPath annotation (a path object) and <b>not</b>
 * an OMERO annotation (some metadata attached to images for example).
 * <p>
 * This class uses an {@link SendAnnotationForm} to prompt the user for parameters.
 */
public class AnnotationSender implements DataTransporter {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationSender.class);
    private static final ResourceBundle resources = Utils.getResources();
    private final QuPathGUI quPath;
    private enum Request {
        SEND_ANNOTATIONS,
        DELETE_EXISTING_ANNOTATIONS,
        DELETE_EXISTING_MEASUREMENTS,
        SEND_ANNOTATION_MEASUREMENTS,
        SEND_DETECTION_MEASUREMENTS
    }

    /**
     * Create the annotation sender.
     *
     * @param quPath the quPath window
     */
    public AnnotationSender(QuPathGUI quPath) {
        logger.debug("Creating annotation sender for {}", quPath);
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
        logger.debug("Attempting to send annotations to OMERO");

        QuPathViewer viewer = quPath.getViewer();
        if (viewer == null || !(viewer.getServer() instanceof OmeroImageServer omeroImageServer)) {
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                    resources.getString("DataTransporters.AnnotationsSender.notFromOMERO")
            );
            return;
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.AnnotationsSender.retrievingOwners")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        logger.debug("Getting server of {}", omeroImageServer.getClient());
        omeroImageServer.getClient().getServer().thenApplyAsync(server -> {
            logger.debug("Got server {} of {}", server, omeroImageServer.getClient());

            logger.debug("Getting image with ID {} to get ID of group owning the image", omeroImageServer.getId());
            long groupId = omeroImageServer.getClient().getApisHandler().getImage(omeroImageServer.getId()).join().getGroupId();

            Group group = server.getGroups().stream()
                    .filter(g -> g.getId() == groupId)
                    .findAny()
                    .orElseThrow(() -> new NoSuchElementException(String.format("Cannot find group with ID %d", groupId)));
            logger.debug("Got group {} owning image with ID {}", group, omeroImageServer.getId());

            return switch (group.getPermissionLevel()) {
                case UNKNOWN, PRIVATE, READ_ONLY -> {
                    logger.debug("Group {} has {} permission level, which means current user can only delete its own annotations", group, group.getPermissionLevel());

                    yield List.of(server.getConnectedExperimenter());
                }
                case READ_ANNOTATE -> {
                    if (omeroImageServer.getClient().getApisHandler().isConnectedUserOwnerOfGroup(groupId)) {
                        logger.debug(
                                "Group {} has {} permission level and current user is owner of this group, which means current user can delete annotations of all members of group",
                                group,
                                group.getPermissionLevel()
                        );

                        yield group.getOwners();
                    } else {
                        logger.debug(
                                "Group {} has {} permission level and current user is not owner of this group, which means current user can only delete its own annotations",
                                group,
                                group.getPermissionLevel()
                        );

                        yield List.of(server.getConnectedExperimenter());
                    }
                }
                case READ_WRITE -> {
                    logger.debug(
                            "Group {} has {} permission level, which means current user can delete annotations of all members of group",
                            group,
                            group.getPermissionLevel()
                    );

                    yield group.getOwners();
                }
            };
        }).whenComplete((owners, error) -> Platform.runLater(() -> {
            waitingWindow.close();

            if (owners == null) {
                logger.error("Cannot get owners having access to the image {}. Cannot send annotations", omeroImageServer.getURIs(), error);

                Dialogs.showErrorMessage(
                        resources.getString("DataTransporters.AnnotationsSender.serverError"),
                        MessageFormat.format(
                                resources.getString("DataTransporters.AnnotationsSender.cannotRetrieveOwners"),
                                omeroImageServer.getURIs()
                        )
                );
                return;
            }

            logger.debug("Got owners {} that potentially have annotations that can be deleted by current user", owners);
            sendAnnotations(omeroImageServer, owners, viewer);
        }));
    }

    @Override
    public String toString() {
        return String.format("Annotation sender for %s", quPath);
    }

    private void sendAnnotations(OmeroImageServer omeroImageServer, List<Owner> owners, QuPathViewer viewer) {
        SendAnnotationForm annotationForm;
        try {
            annotationForm = new SendAnnotationForm(
                    owners,
                    quPath.getProject() != null,
                    !viewer.getImageData().getHierarchy().getAnnotationObjects().isEmpty(),
                    !viewer.getImageData().getHierarchy().getDetectionObjects().isEmpty()
            );
        } catch (IOException e) {
            logger.error("Error when creating the annotation form", e);
            return;
        }

        if (!Dialogs.showConfirmDialog(
                resources.getString("DataTransporters.AnnotationsSender.dataToSend"),
                annotationForm
        )) {
            logger.debug("Sending annotations dialog not confirmed. Not sending annotations");
            return;
        }

        Collection<PathObject> annotations = getAnnotations(viewer, annotationForm.sendOnlySelectedAnnotations());
        if (annotations.isEmpty()) {
            logger.debug("No annotations to send. Not sending annotations");
            Dialogs.showErrorMessage(
                    resources.getString("DataTransporters.AnnotationsSender.sendAnnotations"),
                    resources.getString("DataTransporters.AnnotationsSender.noAnnotations")
            );
            return;
        }

        // The potential deletion of existing measurements and annotations must happen before other requests
        Map<Request, CompletableFuture<Void>> deletionRequests = new HashMap<>();
        if (annotationForm.deleteExistingAnnotations()) {
            logger.debug("Deleting shapes of image with ID {} belonging to users {}", omeroImageServer.getId(), annotationForm.getSelectedOwnersOfDeletedAnnotations());
            deletionRequests.put(
                    Request.DELETE_EXISTING_ANNOTATIONS,
                    omeroImageServer.getClient().getApisHandler().deleteShapes(
                            omeroImageServer.getId(),
                            annotationForm.getSelectedOwnersOfDeletedAnnotations().stream().map(Owner::id).toList()
                    )
            );
        }
        if (annotationForm.deleteExistingMeasurements()) {
            logger.debug("Deleting measurements of image with ID {} belonging to users {}", omeroImageServer.getId(), annotationForm.getSelectedOwnersOfDeletedMeasurements());
            deletionRequests.put(
                    Request.DELETE_EXISTING_MEASUREMENTS,
                    omeroImageServer.getClient().getApisHandler().deleteAttachments(
                            omeroImageServer.getId(),
                            Image.class,
                            annotationForm.getSelectedOwnersOfDeletedMeasurements().stream().map(Owner::getFullName).toList()   //tODO: first name + last name, not full name
                                                                                                                                //TODO: actually, can it be ID?
                    )
            );
        }

        WaitingWindow waitingWindow;
        try {
            waitingWindow = new WaitingWindow(
                    quPath.getStage(),
                    resources.getString("DataTransporters.AnnotationsSender.sendingAnnotations")
            );
        } catch (IOException e) {
            logger.error("Error while creating the waiting window", e);
            return;
        }
        waitingWindow.show();

        // Get form info in JavaFX Application Thread
        boolean sendAnnotationMeasurements = annotationForm.sendAnnotationMeasurements();
        boolean sendDetectionMeasurements = annotationForm.sendDetectionMeasurements();

        CompletableFuture.supplyAsync(() -> {
            Map<Request, Throwable> requestToErrors = new HashMap<>();      // a HashMap is manually created because using
                                                                            // streams doesn't allow for null values
            for (var entry: deletionRequests.entrySet()) {
                try {
                    requestToErrors.put(
                            entry.getKey(),
                            entry.getValue().handle((v, error) -> error).get()
                    );
                } catch (InterruptedException | ExecutionException e) {
                    requestToErrors.put(entry.getKey(), e);
                }
            }
            return requestToErrors;
        }).thenApply(deletionRequestToErrors -> {
            Map<Request, CompletableFuture<Void>> requests = new LinkedHashMap<>();  // LinkedHashMap to keep inserting order
            for (var entry: deletionRequestToErrors.entrySet()) {
                requests.put(
                        entry.getKey(),
                        entry.getValue() == null ?
                                CompletableFuture.completedFuture(null) :
                                CompletableFuture.failedFuture(entry.getValue())
                );
            }
            requests.putAll(createRequests(
                    quPath,
                    omeroImageServer,
                    annotations,
                    sendAnnotationMeasurements,
                    sendDetectionMeasurements
            ));

            Map<Request, Throwable> requestToErrors = new LinkedHashMap<>();
            for (var entry: requests.entrySet()) {
                try {
                    requestToErrors.put(
                            entry.getKey(),
                            entry.getValue().handle((v, error) -> error).get()
                    );
                } catch (InterruptedException | ExecutionException e) {
                    requestToErrors.put(entry.getKey(), e);
                }
            }
            return requestToErrors;
        }).exceptionally(error -> {
            logger.error("Unexpected error while sending annotations", error);
            return Map.of();
        }).thenAccept(errors -> Platform.runLater(() -> {
            waitingWindow.close();

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
            boolean sendAnnotationMeasurements,
            boolean sendDetectionMeasurements
    ) {
        Map<Request, CompletableFuture<Void>> requests = new HashMap<>();

        logger.debug("Adding {} to image with ID {}", annotations, omeroImageServer.getId());
        requests.put(
                Request.SEND_ANNOTATIONS,
                omeroImageServer.getClient().getApisHandler().addShapes(
                        omeroImageServer.getId(),
                        annotations.stream()
                                .map(pathObject -> Shape.createFromPathObject(pathObject, quPath.getOverlayOptions().getFillAnnotations()))
                                .flatMap(List::stream)
                                .toList()
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
            Class<? extends PathObject> exportType,
            OmeroImageServer omeroImageServer
    ) {
        if (quPath.getProject() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("There is no project currently open"));
        }
        Project<BufferedImage> project = quPath.getProject();

        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            QuPathViewer viewer = quPath.getViewer();
            ProjectImageEntry<BufferedImage> entry = project.getEntry(viewer.getImageData());

            List<PathObject> pathObjects = new ArrayList<>();
            pathObjects.add(entry.readHierarchy().getRootObject());     // List.of() cannot be used because getObjects()
                                                                        // below expects a mutable list
            if (entry.readHierarchy().getObjects(pathObjects, exportType).isEmpty()) {
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

            logger.debug("Sending {} measurements to image with ID {}", exportType, omeroImageServer.getId());
            return omeroImageServer.getClient().getApisHandler().sendAttachment(
                    omeroImageServer.getId(),
                    Image.class,
                    title,
                    outputStream.toString()
            );
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void logErrors(Map<Request, Throwable> errors) {
        for (Map.Entry<Request, Throwable> error: errors.entrySet()) {
            if (error.getValue() == null) {
                logger.debug(
                        "The operation {} succeeded",
                        switch (error.getKey()) {
                            case SEND_ANNOTATIONS -> "sending annotations";
                            case DELETE_EXISTING_ANNOTATIONS -> "deleting existing annotations";
                            case DELETE_EXISTING_MEASUREMENTS -> "deleting existing measurements";
                            case SEND_ANNOTATION_MEASUREMENTS -> "sending annotations measurements";
                            case SEND_DETECTION_MEASUREMENTS -> "sending detection measurements";
                        }
                );
            } else {
                logger.error(
                        "Error while {}",
                        switch (error.getKey()) {
                            case SEND_ANNOTATIONS -> "sending annotations";
                            case DELETE_EXISTING_ANNOTATIONS -> "deleting existing annotations";
                            case DELETE_EXISTING_MEASUREMENTS -> "deleting existing measurements";
                            case SEND_ANNOTATION_MEASUREMENTS -> "sending annotations measurements";
                            case SEND_DETECTION_MEASUREMENTS -> "sending detection measurements";
                        },
                        error.getValue()
                );
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
                            case DELETE_EXISTING_ANNOTATIONS -> "DataTransporters.AnnotationsSender.deleteExistingAnnotations";
                            case DELETE_EXISTING_MEASUREMENTS -> "DataTransporters.AnnotationsSender.deleteExistingMeasurements";
                            case SEND_ANNOTATION_MEASUREMENTS -> "DataTransporters.AnnotationsSender.sendAnnotationMeasurements";
                            case SEND_DETECTION_MEASUREMENTS -> "DataTransporters.AnnotationsSender.sendDetectionMeasurements";
                        })
                ))
                .collect(Collectors.joining("\n"));
    }
}
