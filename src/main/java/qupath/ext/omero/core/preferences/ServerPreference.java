package qupath.ext.omero.core.preferences;

import qupath.ext.omero.core.Credentials;

import java.net.URI;

/**
 * Some information about an OMERO server to store. Every field of this class can be null.
 *
 * @param webServerUri the URI of the OMERO web server
 * @param credentials the credentials used to log in to the OMERO server
 * @param maxBodySizeBytes the maximal size in bytes that the body of a request to one the APIs can have
 * @param webJpegQuality the JPEG quality used by the web pixel API of the OMERO server
 * @param iceAddress the address of the OMERO ICE server
 * @param icePort the port of the OMERO ICE server
 * @param iceNumberOfReaders the number of readers to use when reading an image with ICE
 * @param msPixelBufferPort the saved port used by the pixel buffer microservice of the OMERO server
 */
public record ServerPreference(
        URI webServerUri,
        Credentials credentials,
        Long maxBodySizeBytes,
        Float webJpegQuality,
        String iceAddress,
        Integer icePort,
        Integer iceNumberOfReaders,
        Integer msPixelBufferPort
) {}
