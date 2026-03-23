import qupath.ext.omero.core.apis.commonentities.shapes.ShapeCreator
import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script send all annotations of the current image to the OMERO server. Existing OMERO annotations can optionally be
 * deleted.
 *
 * An OMERO image must be currently opened in QuPath through the QuPath GUI or through the command line (see the
 * open_image_from_command_line.groovy script).
 */

// Parameters
var deleteExistingAnnotations = false      // whether to delete existing annotations on the OMERO server before
                                                    // sending the new ones
var annotationOwners = []                           // A list of full names of OMERO users whose annotations should be deleted
                                                    // (e.g. ["John Smith", "Jane Smith"]).
                                                    // If deleteExistingAnnotations is false, this parameter is not considered
var fillAnnotationColors = true            // whether the created annotations on OMERO should have a fill color

// Get image data
var imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}

// Get OMERO server
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Delete existing annotations if necessary
if (deleteExistingAnnotations) {
    // Get IDs of provided owners
    var userIds = omeroServer.getClient().getServer().get().getIdsOfExperimenters(annotationOwners)

    if (userIds.isEmpty()) {
        println "Warning: no owner was provided, so no annotation will be deleted"
    } else {
        // Delete annotations on OMERO server
        omeroServer.getClient().getApisHandler().deleteShapes(omeroServer.getId(), userIds).get()
        println "Existing annotations deleted on the OMERO server"
    }
}

// Get all annotations of the current image. Could be getSelectedObjects() instead to get selected objects, or something else
var annotations = getAnnotationObjects()

// Convert QuPath annotations to OMERO shapes
var shapes = annotations.collect { pathObject -> ShapeCreator.createShapes(pathObject, fillAnnotationColors)}.flatten()

// Send annotation to OMERO
omeroServer.getClient().getApisHandler().addShapes(omeroServer.getId(), shapes).get()

println annotations.size() + " annotations sent to OMERO"
