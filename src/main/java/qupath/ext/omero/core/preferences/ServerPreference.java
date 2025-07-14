package qupath.ext.omero.core.preferences;

import qupath.ext.omero.core.Credentials;

import java.net.URI;

/**
 * Some information about an OMERO server to store.
 *
 * @param webServerUri the URI of the OMERO web server
 * @param credentials the credentials used to log in to the OMERO server
 * @param webJpegQuality the JPEG quality used by the web pixel API of the OMERO server. Can be 0 to indicate that no quality was specified
 * @param iceAddress the address of the OMERO ICE server. Can be null to indicate that no address was specified
 * @param icePort the port of the OMERO ICE server. Can be 0 to indicate that no port was specified
 * @param iceNumberOfReaders the number of readers to use when reading an image with ICE. Can be 0 to indicate that no number of readers was specified
 * @param msPixelBufferPort the saved port used by the pixel buffer microservice of the OMERO server. Can be 0 to indicate that
 *                          no port was specified
 */
public record ServerPreference(
        URI webServerUri,
        Credentials credentials,
        float webJpegQuality,
        String iceAddress,
        int icePort,
        int iceNumberOfReaders,
        int msPixelBufferPort
) {}
