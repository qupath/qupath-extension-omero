import qupath.ext.omero.core.apis.commonentities.shapes.ShapeCreator
import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script send all annotations of the current image to the OMERO server.
 *
 * An OMERO image must be currently opened in QuPath through the QuPath GUI or
 * through the command line (see the open_image_from_command_line.groovy script).
 */

// Open server
var imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Get all annotations of the current image
var annotations = getAnnotationObjects()

// Send annotation to OMERO
var fillAnnotationColors = true
omeroServer.getClient().getApisHandler().addShapes(
        omeroServer.getId(),
        annotations.collect { pathObject -> ShapeCreator.createShapes(pathObject, fillAnnotationColors)}.flatten()
).get()

println "Annotations sent"
