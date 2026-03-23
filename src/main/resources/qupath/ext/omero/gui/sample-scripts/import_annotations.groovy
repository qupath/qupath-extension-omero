import qupath.ext.omero.core.apis.commonentities.shapes.Shape
import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script imports annotations of an image stored on an OMERO server and add them to the image in QuPath.
 *
 * An OMERO image must be currently opened in QuPath through the QuPath GUI or through the command line (see the
 * open_image_from_command_line.groovy script).
 */

// Parameters
var annotationOwner = ""                 // the full name (first middle last name, e.g. "John Smith") of the OMERO user that
                                         // should own the annotations to retrieve. Can be empty to retrieve annotations of all
                                         // users
var deleteExistingAnnotations = false    // whether to delete existing QuPath annotations before importing the OMERO annotations
var deleteExistingDetections = false     // whether to delete existing QuPath detections before importing the OMERO annotations

// Get image data
var imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}

// Get OMERO server
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Delete existing QuPath annotations if necessary
if (deleteExistingAnnotations) {
    removeAnnotations()
}

// Delete existing QuPath detections if necessary
if (deleteExistingDetections) {
    removeDetections()
}

// Get annotations
List<PathObject> annotations
if (annotationOwner.isEmpty()) {
    // If no annotation owner was provided, get all annotations
    annotations = omeroServer.readPathObjects()

    // An alternative script would be:
    // var shapes = omeroServer.getClient().getApisHandler().getShapes(omeroServer.getId(), -1).get()
    // annotations = Shape.createPathObjects(shapes)
} else {
    // If the full name of an annotation owner is provided, get the annotations of this user

    // Get ID of annotation owner
    var userId = omeroServer.getClient().getServer().get().getIdsOfExperimenters(List.of(annotationOwner))[0]

    // Get OMERO shapes and then convert them to QuPath annotations
    var shapes = omeroServer.getClient().getApisHandler().getShapes(omeroServer.getId(), userId).get()
    annotations = Shape.createPathObjects(shapes)
}

// Add annotations to the QuPath image
addObjects(annotations)

println annotations.size() + " annotations found and added to QuPath image"
