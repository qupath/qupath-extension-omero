import qupath.ext.omero.core.apis.webclient.EntityType
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity
import qupath.ext.omero.core.imageserver.OmeroImageServer
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathDetectionObject

/*
 * This script send the annotation and detections measurements of the current image to the OMERO server as attachments.
 * Existing attachments of specific users can be deleted.
 *
 * A QuPath project and an OMERO image must be currently opened in QuPath through the QuPath GUI or
 * through the command line (see the open_image_from_command_line.groovy script).
 */

// Parameters
var deleteExistingAttachments = true
var idsOfUsersWhoseAttachmentsShouldBeDeleted = [1]     // the OMERO ID of the user whose attachments should be deleted
var sendAnnotationMeasurements = true
var sendDetectionMeasurements = true

// Open server
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

// Delete existing attachment
if (deleteExistingAttachments) {
    omeroServer.getClient().getApisHandler().deleteAttachments(
            new SimpleServerEntity(EntityType.IMAGE, omeroServer.getId()),
            idsOfUsersWhoseAttachmentsShouldBeDeleted
    ).get()

    println "Existing attachments deleted"
}

// Send annotation measurements
if (sendAnnotationMeasurements) {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
        // The image must be saved first because non saved measures won't be exported
        projectEntry.saveImageData(imageData)

        // Get annotation measurements
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
    } catch (IOException e) {
        println "Error when reading annotation measurements: " + e
    }
}

// Send detection measurements
if (sendDetectionMeasurements) {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
        // The image must be saved first because non saved measures won't be exported
        projectEntry.saveImageData(imageData)

        // Get detection measurements
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
    } catch (IOException e) {
        println "Error when reading detection measurements: " + e
    }
}
