import qupath.ext.omero.core.apis.webclient.EntityType
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity
import qupath.ext.omero.core.imageserver.OmeroImageServer
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathDetectionObject

/*
 * This script sends the annotation and detections measurements of the current image to the OMERO server as attachments.
 * Existing measurements of specific users can be deleted.
 *
 * A QuPath project and an OMERO image must be currently opened in QuPath through the QuPath GUI or through the command
 * line (see the open_image_from_command_line.groovy script).
 */

// Parameters
var deleteExistingMeasurements = false      // whether to delete existing measurements on the OMERO server
var measurementOwners = []                  // A list of full names of OMERO users whose measurements should be deleted
                                            // (e.g. ["John Smith", "Jane Smith"]).
                                            // If deleteExistingMeasurements is false, this parameter is not considered
var sendAnnotationMeasurements = true       // whether to send annotation measurements to OMERO
var sendDetectionMeasurements = true        // whether to send detection measurements to OMERO

// Get image data
var imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}

// Get project and project entry
var project = getProject()
if (project == null) {
    println "A project needs to be opened in QuPath before running this script"
    return
}
var projectEntry = project.getEntry(imageData)

// Get image server
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Delete existing measurements if necessary
if (deleteExistingMeasurements) {
    // Get IDs of provided owners
    var userIds = omeroServer.getClient().getServer().get().getIdsOfExperimenterFromFullNames(measurementOwners)
    // Alternatively, if measurementOwners contains usernames or full names, you can use:
    // var userIds = omeroServer.getClient().getServer().get().getIdsOfExperimentersFromUsernames(measurementOwners)

    if (userIds.isEmpty()) {
        println "Warning: no owner was provided, so no measurement will be deleted"
    } else {
        // Delete measurements on OMERO server
        omeroServer.getClient().getApisHandler().deleteAttachments(
                new SimpleServerEntity(EntityType.IMAGE, omeroServer.getId()),
                userIds
        ).get()

        println "Existing measurements deleted on the OMERO server"
    }
}

// Send annotation measurements if necessary
if (sendAnnotationMeasurements) {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
        // The image must be saved first because non saved measures won't be exported
        projectEntry.saveImageData(imageData)

        // Get annotation measurements as a text
        new MeasurementExporter()
                .exportType(PathAnnotationObject.class)
                .imageList(List.of(projectEntry))
                .separator(",")
                .exportMeasurements(outputStream)
        def annotationMeasurements = outputStream.toString()

        // Send annotation measurements
        omeroServer.getClient().getApisHandler().sendAttachment(
                new SimpleServerEntity(EntityType.IMAGE, omeroServer.getId()),
                "annotation_measurements.csv",
                annotationMeasurements
        ).get()

        println "Annotation measurements sent"
    }
}

// Send detection measurements if necessary
if (sendDetectionMeasurements) {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
        // The image must be saved first because non saved measures won't be exported
        projectEntry.saveImageData(imageData)

        // Get detection measurements as a text
        new MeasurementExporter()
                .exportType(PathDetectionObject.class)
                .imageList(List.of(projectEntry))
                .separator(",")
                .exportMeasurements(outputStream)
        def detectionMeasurements = outputStream.toString()

        // Send detection measurements
        omeroServer.getClient().getApisHandler().sendAttachment(
                new SimpleServerEntity(EntityType.IMAGE, omeroServer.getId()),
                "detection_measurements.csv",
                detectionMeasurements
        ).get()

        println "Detection measurements sent"
    }
}
