import qupath.ext.omero.core.apis.commonentities.image.ChannelSettings
import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script send the image settings (image name, channel names, channel colors, channel display ranges)
 * of a QuPath image to the corresponding image on an OMERO server.
 *
 * An OMERO image must be currently opened in QuPath through the QuPath GUI or through
 * the command line (see the open_image_from_command_line.groovy script).
 *
 * If the image name is sent, a QuPath project must be opened.
 * If one of the channel settings is sent, the image must not have the RGB format.
 */

// Parameters
var sendImageName = true
var sendChannelNames = true
var sendChannelColors = true
var sendChannelDisplayRanges = true

// Check that a project is opened (if needed)
if (sendImageName && getProject() == null) {
    println "A project needs to be opened in QuPath if the image name is to be sent"
    return
}

// Get image data
var imageData = getCurrentImageData()
if (imageData == null) {
    println "An image needs to be opened in QuPath before running this script"
    return
}

// Get image server
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Check that the image has not the RGB format (if needed)
if ((sendChannelNames || sendChannelColors || sendChannelDisplayRanges) && omeroServer.getMetadata().isRGB()) {
    println "The image must not have the RGB format if one of the channel settings is to be sent"
    return
}

if (sendImageName) {
    // Retrieve current image name
    var imageName = omeroServer.getMetadata().getName()

    // Send image name
    omeroServer.getClient().getApisHandler().changeImageName(omeroServer.getId(), imageName).get()

    println "Image name sent"
}

if (sendChannelNames) {
    // Retrieve channel names from current image
    var channelNames = omeroServer.getMetadata().getChannels().stream().map(ImageChannel::getName).toList()

    // Send channel names
    omeroServer.getClient().getApisHandler().changeChannelNames(omeroServer.getId(), channelNames).get()

    println "Channel names sent"
}

if (sendChannelColors) {
    // Retrieve channel colors from current image
    var channelColors = omeroServer.getMetadata().getChannels().stream().map(ImageChannel::getColor).toList()

    // Send channel colors
    omeroServer.getClient().getApisHandler().changeChannelColors(omeroServer.getId(), channelColors).get()

    println "Channel colors sent"
}

if (sendChannelDisplayRanges) {
    // Retrieve display ranges from current image
    var displayRanges = getCurrentViewer().getImageDisplay().availableChannels().stream()
            .map(channel -> new ChannelSettings(channel.getMinDisplay(), channel.getMaxDisplay()))
            .toList()

    // Send channel display ranges
    omeroServer.getClient().getApisHandler().changeChannelDisplayRanges(omeroServer.getId(), displayRanges).get()

    println "Channel display ranges sent"
}
