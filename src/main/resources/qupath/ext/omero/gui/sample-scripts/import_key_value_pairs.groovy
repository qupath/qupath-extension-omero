import qupath.ext.omero.core.apis.webclient.EntityType
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity
import qupath.ext.omero.core.apis.webclient.annotations.MapAnnotation
import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script imports all key value pairs of an image stored on an OMERO server
 * and add them to the image in QuPath.
 *
 * A QuPath project and an OMERO image must be currently opened in QuPath through
 * the QuPath GUI or through the command line (see the open_image_from_command_line.groovy script).
 */

// Parameters
var deleteExistingKeyValuePairs = false
var replaceExistingKeyValuesPairs = true

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
var projectEntry = project.getEntry(imageData)

// Get image server
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Get all annotations from OMERO
var annotations = omeroServer.getClient().getApisHandler().getAnnotations(new SimpleServerEntity(EntityType.IMAGE, omeroServer.getId())).get()

// Filter the retrieved list of annotations by only keeping the map annotations
var mapAnnotations = annotations.findAll {
    it instanceof MapAnnotation
}

// Get the key value pairs of the list of map annotations
var keyValues = mapAnnotations.collect { mapAnnotation -> mapAnnotation.getPairs()}.flatten()

// Delete all existing key value pairs if necessary
if (deleteExistingKeyValuePairs) {
    projectEntry.clearMetadata()
}

// Set and replace (if necessary) key value pairs of the QuPath image by the ones from OMERO
for (var pair : keyValues) {
    if (replaceExistingKeyValuesPairs || !projectEntry.containsMetadata(pair.key())) {
        projectEntry.putMetadataValue(pair.key(), pair.value())
    }
}

println "Key value pairs imported"
