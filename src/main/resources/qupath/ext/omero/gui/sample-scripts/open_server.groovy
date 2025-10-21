import qupath.ext.omero.core.Client
import qupath.ext.omero.core.Credentials
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity
import qupath.lib.images.servers.ImageServers

/*
 * This script browses an OMERO server and prints its projects, datasets, and images.
 * An image is also opened and its metadata is printed.
 */

// Create a connection to an OMERO server
var serverURL = "https://idr.openmicroscopy.org/"
var credentials = new Credentials()                                                     // to skip authentication and use the public account
//var credentials = new Credentials("some_username", "some_password".toCharArray())     // to authenticate with a regular account
var client = Client.createOrGet(serverURL, credentials, null)

// List all projects of the OMERO server
var projects = client.getApisHandler().getProjects(-1, -1).get()
projects.forEach(it -> {
    println it
})

// List all datasets belonging to one project
var projectID = 101
var datasets = client.getApisHandler().getDatasets(projectID, -1, -1).get()
datasets.forEach(it -> {
    println it
})

// List all images belonging to one dataset
var datasetID = 369
var images = client.getApisHandler().getImages(datasetID, -1, -1).get()
images.forEach(it -> {
    println it
})

// Open an image and print its metadata
var imageID = 1920093
var image = client.getApisHandler().getImage(imageID).get()
var imageURI = client.getApisHandler().getEntityUri(new SimpleServerEntity(image))
var server = ImageServers.buildServer(imageURI)
println "Image metadata: " + server.getMetadata()

// Close image
server.close()

// Close client connection. This is needed to release resources on the OMERO server
client.close()