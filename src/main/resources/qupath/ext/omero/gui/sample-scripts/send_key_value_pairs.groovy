import qupath.ext.omero.core.apis.webclient.Namespace
import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script sends all key value pairs of a QuPath image to the
 * corresponding image on an OMERO server with the defined namespace.
 *
 * A QuPath project and an OMERO image must be currently opened in QuPath through
 * the QuPath GUI or through the command line (see the open_image_from_command_line.groovy script).
 */

// Parameters
var replaceExistingKeyValuesPairs = true
var namespace = Namespace.getDefaultNamespace()      // could be a custom namespace with: new Namespace("custom namespace")

// Get project
var project = getProject()
if (project == null) {
    println "A project needs to be opened in QuPath before running this script"
    return
}

// Get image and project entry
var imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}

// Get key value pairs of the QuPath image
var keyValues = project.getEntry(imageData).getMetadataMap()

// Get image server
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Attempt to send key value pairs to OMERO
omeroServer.getClient().getApisHandler().sendKeyValuePairs(
        omeroServer.getId(),
        namespace,
        keyValues,
        replaceExistingKeyValuesPairs
).get()

println "Key value pairs sent"
