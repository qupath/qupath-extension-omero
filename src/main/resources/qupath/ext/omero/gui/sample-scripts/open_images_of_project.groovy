/*
 * This script iterates over the images of the currently opened project.
 * Each image is opened, their metadata is printed, and a part of the images is read.
 * This script it not specific to OMERO but is a good way to see if all images of a project
 * are supported.
 */
var project = getProject()
if (project == null) {
    println "A project needs to be opened in QuPath before running this script"
    return
}

for (entry in project.getImageList()) {
    println "Opening " + entry.getImageName()
    
    // Accessing image metadata
    var server = entry.getServerBuilder().build()
    println "Metadata: " + server.getMetadata()

    // Accessing pixels
    var downsample = 4.0
    var x = 100
    var y = 200
    var width = 100
    var height = 200
    var request = RegionRequest.createInstance(server.getPath(), downsample, x, y, width, height)
    var img = server.readRegion(request)
    println "Image: " + img
    
    // Closing server. This is needed to free resources on the OMERO server
    server.close()
    println "Closing " + entry.getImageName()
    println ""
}
