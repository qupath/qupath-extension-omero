import qupath.ext.omero.core.imageserver.*
import qupath.ext.omero.core.apis.commonentities.shapes.Shape

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
def fillAnnotationColors = true
omeroServer.getClient().getApisHandler().addShapes(
        omeroServer.getId(),
        annotations.collect { pathObject -> Shape.createFromPathObject(pathObject, fillAnnotationColors)}.flatten()
).get()

println "Annotations sent"
