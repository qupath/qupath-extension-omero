import qupath.ext.omero.core.imageserver.*
import qupath.ext.omero.core.entities.shapes.Shape

/*
 * This script send all annotations of the current image to the OMERO server.
 *
 * An OMERO image must be currently opened in QuPath through the QuPath GUI or
 * through the command line (see the open_image_from_command_line.groovy script).
 */

// Open server
def imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}
def server = imageData.getServer()
def omeroServer = (OmeroImageServer) server

// Get all annotations of the current image
def annotations = getAnnotationObjects()

// Send annotation to OMERO
def removeExistingAnnotations = true
omeroServer.getClient().getApisHandler().writeROIs(
        omeroServer.getId(),
        annotations.stream()
                .map(Shape::createFromPathObject)
                .flatMap(List::stream)
                .toList(),
        removeExistingAnnotations
).get()

println "Annotations sent"
