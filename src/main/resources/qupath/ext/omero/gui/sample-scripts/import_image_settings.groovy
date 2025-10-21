import qupath.ext.omero.core.imageserver.OmeroImageServer

/*
 * This script imports the image settings (image name, channel names, channel colors, channel display ranges)
 * of an image stored on an OMERO server and add them to the image in QuPath.
 *
 * An OMERO image must be currently opened in QuPath through the QuPath GUI or through
 * the command line (see the open_image_from_command_line.groovy script).
 *
 * If the image name is imported, a QuPath project must be opened.
 * If one of the channel settings is imported, the image must not have the RGB format.
 */

// Parameters
var importImageName = true
var importChannelNames = true
var importChannelColors = true
var importChannelDisplayRanges = true

// Check that a project is opened (if needed)
if (importImageName && getProject() == null) {
    println "A project needs to be opened in QuPath if the image name is to be imported"
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
if ((importChannelNames || importChannelColors || importChannelDisplayRanges) && omeroServer.getMetadata().isRGB()) {
    println "The image must not have the RGB format if one of the channel settings is to be imported"
    return
}

// Retrieve image settings from OMERO
var imageSettings = omeroServer.getClient().getApisHandler().getImageData(omeroServer.getId()).get()

// Retrieve image name and channel settings from the response
var imageName = imageSettings.getName()
var channelSettings = imageSettings.getChannelSettings()

if (importImageName) {
    getProject().getEntry(imageData).setImageName(imageName)
    println "Image name imported"
}

if (importChannelNames) {
    // Get the channel names from the image settings
    var channelNames = channelSettings.collect { channelSetting ->
        channelSetting.name()
    } as String[]

    setChannelNames(channelNames)
    println "Channel names imported"
}

if (importChannelColors) {
    // Get the channel colors from the image settings
    var channelColors = channelSettings.collect { channelSetting ->
        channelSetting.rgbColor()
    } as int[]

    setChannelColors(channelColors)
    println "Channel colors imported"
}

if (importChannelDisplayRanges) {
    // Get the the display and the channels of the current QuPath viewer
    var display = getCurrentViewer().getImageDisplay()
    var channels = display.availableChannels()

    for (int i=0; i<channels.size(); i++) {
        display.setMinMaxDisplay(
                channels.get(i),
                channelSettings.get(i).minDisplayRange() as float,
                channelSettings.get(i).maxDisplayRange() as float
        )
    }
    println "Channel display ranges imported"
}
