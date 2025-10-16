import qupath.ext.omero.core.imageserver.OmeroImageServer

/**
 * This script opens an image through the command line.
 */

/*
The script must be launched with the following command:
/path/to/QuPath/bin/QuPath script \
    --image="the_web_link_of_your_image" \
    --server "[]" \
    path/to/this/script/open_image_from_command_line.groovy


--server "[]" is optional but can be:

--server "[--usertype, PUBLIC_USER]" to attempt to connect to the OMERO server with the public user. The public user
must have sufficient rights to open the image.
--server "[--username, your_username, --password, your_password]" to set the credentials to use when connecting to
the OMERO server. Note that it is not recommended to set the password this way as it may be shown in the logs. A safer
approach is to only provide the username, you will then be prompted for the password.
If those parameters are not indicated, you will be prompted for the credentials.

--server "[--pixelAPI, Web]" to use the web pixel API (see the README file of the extension, "Reading images" section). If this API
is selected, you can add the [--jpegQuality, 0.6] optional argument to set the quality level of the JPEG images returned by the web
pixel API (number between 0 and 1).
--server "[--pixelAPI, Ice]" to use the Ice pixel API (see the README file of the extension, "Reading images" section). If this API
is selected, you can add the [--serverAddress, "some_address"] optional argument to set the address of the OMERO server, the
[--serverPort, 4064] optional argument to set the port used by the OMERO server, and the [--numberOfReaders, 16] optional argument to
set the maximum number of parallel readers to use when reading the image.
--server "[--pixelAPI, Pixel Buffer Microservice]" to use the Pixel Buffer Microservice pixel API (see the README file of the extension,
"Reading images" section). If this API is selected, you can add the [--msPixelBufferPort, 8082] optional argument to set the port used
by this microservice on the OMERO server.
If you omit this parameter, the most accurate available pixel API will be selected.

For example, to authenticate with the public user and use the web pixel API with a JPEG quality of 1.0:
--server "[--usertype, PUBLIC_USER, --pixelAPI, Web, --jpegQuality, 1.0]"
 */

// Open server
var imageData = getCurrentImageData()
if (imageData == null) {
    println "Image not found"
    return
}
var server = imageData.getServer()
var omeroServer = (OmeroImageServer) server

// Print server type
println omeroServer.getServerType()

// Perform operations with image
// ...

// Close server
omeroServer.close()