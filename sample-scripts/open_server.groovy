import qupath.ext.omero.core.*
import qupath.lib.images.servers.*

/*
 * This script browses an OMERO server and prints its projects, datasets, and images.
 * An image is also opened and its metadata is printed.
 */

// Create a connection to an OMERO server
def serverURL = "https://idr.openmicroscopy.org/"
def authentication = WebClient.Authentication.TRY_TO_SKIP       // try to skip authentication if the server allows it. Can be
                                                                // WebClient.Authentication.ENFORCE to enforce authentication or
                                                                // WebClient.Authentication.SKIP to skip authentication (even if the server doesn't allow it)
def client = WebClients.createClient(serverURL, authentication).get()

// List projects of the OMERO server
def projects = client.getApisHandler().getProjects().get()
projects.forEach(it -> {
    println it
})

// List datasets belonging to one project
def projectID = 101
def datasets = client.getApisHandler().getDatasets(projectID).get()
datasets.forEach(it -> {
    println it
})

// List images belonging to one dataset
def datasetID = 369
def images = client.getApisHandler().getImages(datasetID).get()
images.forEach(it -> {
    println it
})

// Open an image and print its metadata
def imageID = 1920093
def image = client.getApisHandler().getImage(imageID).get().get()
def imageURI = client.getApisHandler().getItemURI(image)
ImageServer<BufferedImage> server = ImageServerProvider.buildServer(imageURI, BufferedImage.class)
println "Image metadata: " + server.getMetadata()

// Close image
server.close()