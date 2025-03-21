import qupath.ext.omero.core.imageserver.*
import qupath.ext.omero.core.entities.annotations.*
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.*

/*
 * This script imports all key value pairs of an image stored on an OMERO server
 * and add them to the image in QuPath.
 *
 * A QuPath project and an OMERO image must be currently opened in QuPath through
 * the QuPath GUI or through the command line (see the open_image_from_command_line.groovy script).
 */

// Parameters
def deleteExistingKeyValuePairs = false
def replaceExistingKeyValuesPairs = true

// Get project
def project = getProject()
if (project == null) {
    println "A project needs to be opened in QuPath before running this script"
    return
}

// Get image and project entry
def imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}
def projectEntry = project.getEntry(imageData)

// Get image server
def server = imageData.getServer()
def omeroServer = (OmeroImageServer) server

// Get all annotations from OMERO
def annotationGroup = omeroServer.getClient().getApisHandler().getAnnotations(omeroServer.getId(), Image.class).get()

// Filter the retrieved list of annotations by only keeping the map annotations
def mapAnnotations = annotationGroup.getAnnotationsOfClass(MapAnnotation.class)

// Get the key value pairs of the list of map annotations
def keyValues = mapAnnotations.collect { mapAnnotation -> mapAnnotation.getPairs()}.flatten()

// Delete all existing key value pairs if necessary
if (deleteExistingKeyValuePairs) {
    projectEntry.clearMetadata()
}

// Set and replace (if necessary) key value pairs of the QuPath image by the ones from OMERO
for (MapAnnotation.Pair pair : keyValues) {
    if (replaceExistingKeyValuesPairs || !projectEntry.containsMetadata(pair.key())) {
        projectEntry.putMetadataValue(pair.key(), pair.value())
    }
}

println "Key value pairs imported"
