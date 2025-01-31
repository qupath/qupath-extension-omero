package qupath.ext.omero.core;

import qupath.ext.omero.core.apis.ApisHandler;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Utility methods for handling web requests.
 */
public class Utils {

    private static final Pattern webclientImagePattern = Pattern.compile("/webclient/\\?show=image-(\\d+)");
    private static final Pattern webclientImagePatternAlternate = Pattern.compile("/webclient/img_detail/(\\d+)");
    private static final Pattern webgatewayImagePattern = Pattern.compile("/webgateway/img_detail/(\\d+)");
    private static final Pattern iviewerImagePattern = Pattern.compile("/iviewer/\\?images=(\\d+)");
    private static final Pattern datasetPattern = Pattern.compile("/webclient/\\?show=dataset-(\\d+)");
    private static final Pattern projectPattern = Pattern.compile("/webclient/\\?show=project-(\\d+)");
    private static final List<Pattern> allPatterns = List.of(
            webclientImagePattern,
            webclientImagePatternAlternate,
            webgatewayImagePattern,
            iviewerImagePattern,
            datasetPattern,
            projectPattern
    );
    private static final List<Pattern> imagePatterns = List.of(
            webclientImagePattern,
            webclientImagePatternAlternate,
            webgatewayImagePattern,
            iviewerImagePattern
    );


    private Utils() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Parse the OMERO entity ID from a URI.
     *
     * @param uri the URI that is supposed to contain the ID. It can be URL encoded
     * @return the entity ID, or an empty Optional if it was not found
     */
    public static OptionalLong parseEntityId(URI uri) {
        for (Pattern pattern : allPatterns) {
            var matcher = pattern.matcher(decodeURI(uri));

            if (matcher.find()) {
                try {
                    return OptionalLong.of(Long.parseLong(matcher.group(1)));
                } catch (Exception ignored) {}
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Returns the host part of a complex URI. For example,
     * {@code https://www.my-server.com/show=image-462} returns {@code https://www.my-server.com}.
     * This also adds the "https" scheme if none is already present in the provided URI.
     *
     * @return the host part of the URI
     * @throws URISyntaxException when the server URI could not have been created
     */
    public static URI getServerURI(URI uri) throws URISyntaxException {
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        return new URI(String.format("%s://%s", scheme, uri.getAuthority()));
    }

    /**
     * <p>Attempt to retrieve the image URIs indicated by the provided entity URI.</p>
     * <ul>
     *     <li>If the entity is a dataset, the URIs of the children of this dataset (which are images) are returned.</li>
     *     <li>If the entity is a project, the URIs of each children of the datasets of this project are returned.</li>
     *     <li>If the entity is an image, the input URI is returned.</li>
     *     <li>Else, an error is returned.</li>
     * </ul>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request or the conversion failed for example).
     * </p>
     *
     * @param entityURI the URI of the entity whose images should be retrieved. It can be URL encoded
     * @param apisHandler the request handler corresponding to the current server
     * @return a CompletableFuture (that may complete exceptionally) with the list described above
     */
    public static CompletableFuture<List<URI>> getImagesURIFromEntityURI(URI entityURI, ApisHandler apisHandler) {
        String entityURL = decodeURI(entityURI);

        if (projectPattern.matcher(entityURL).find()) {
            var projectID = parseEntityId(entityURI);

            if (projectID.isPresent()) {
                return apisHandler.getImagesURIOfProject(projectID.getAsLong());
            } else {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        String.format("The provided URI %s was detected as a project but no ID was found", entityURL)
                ));
            }
        } else if (datasetPattern.matcher(entityURL).find()) {
            var datasetID = parseEntityId(entityURI);

            if (datasetID.isPresent()) {
                return apisHandler.getImagesURIOfDataset(datasetID.getAsLong());
            } else {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        String.format("The provided URI %s was detected as a dataset but no ID was found", entityURL)
                ));
            }
        } else if (imagePatterns.stream().anyMatch(pattern -> pattern.matcher(entityURL).find())) {
            return CompletableFuture.completedFuture(List.of(entityURI));
        } else {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    String.format("The provided URI %s does not represent a project, dataset, or image", entityURL)
            ));
        }
    }

    /**
     *
     * @param credentialLabel
     * @param args
     * @return
     */
    public static Optional<String> getCredentialFromArgs(
            String credentialLabel,
            String... args
    ) {
        String credential = null;
        int i = 0;
        while (i < args.length-1) {
            String parameter = args[i++];
            if (credentialLabel.equals(parameter)) {
                credential = args[i++];
            }
        }

        return Optional.ofNullable(credential);
    }

    private static String decodeURI(URI uri) {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }
}
