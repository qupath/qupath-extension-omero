package qupath.ext.omero.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Long.max;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * <p>
 *     Utility class that sends web request and can convert HTTP responses
 *     to an understandable format (like JSON for example).
 * </p>
 * <p>Each request is performed asynchronously with CompletableFutures.</p>
 */
public class RequestSender {

    private static final Logger logger = LoggerFactory.getLogger(RequestSender.class);
    private static final int REQUEST_TIMEOUT = 20;
    /**
     * <p>
     *     The redirection policy is specified to allow the HTTP client to automatically
     *     follow HTTP redirections (from http:// to https:// for example).
     *     This is needed for icons requests for example.
     * </p>
     * <p>
     *     The cookie policy is specified because some APIs use a
     *     <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>.
     *     This token is stored in a session cookie, so we need to store this session cookie.
     * </p>
     */
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
            .build();

    private RequestSender() {
        throw new RuntimeException("This class is not instantiable.");
    }

    /**
     * Performs a GET request to the specified URI to determine if it is reachable.
     *
     * @param uri the link of the request
     * @param acceptedHTTPCodes a list of HTTP status codes that make the request successful
     *                          (the 200 code can be omitted)
     * @return a CompletableFuture (that will complete successfully) indicating whether the provided link is reachable
     */
    public static CompletableFuture<Boolean> isLinkReachableWithGet(URI uri, int... acceptedHTTPCodes) {
        return isLinkReachable(getGETRequest(uri), acceptedHTTPCodes);
    }

    /**
     * Performs an OPTIONS request to the specified URI to determine if it is reachable.
     *
     * @param uri the link of the request
     * @param acceptedHTTPCodes a list of HTTP status codes that make the request successful
     *                          (the 200 code can be omitted)
     * @return a CompletableFuture (that will complete successfully) indicating whether the provided link is reachable
     */
    public static CompletableFuture<Boolean> isLinkReachableWithOptions(URI uri, int... acceptedHTTPCodes) {
        return isLinkReachable(getOPTIONSRequest(uri), acceptedHTTPCodes);
    }

    /**
     * Performs a GET request to the specified URI. Note that exception handling is left to the caller
     * (the returned CompletableFuture may complete exceptionally if the request failed).
     *
     * @param uri the link of the request
     * @return a CompletableFuture (that may complete exceptionally) with the raw HTTP response in text format
     */
    public static CompletableFuture<String> get(URI uri) {
        return httpClient
                .sendAsync(
                        getGETRequest(uri),
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenApply(HttpResponse::body);
    }

    /**
     * Performs a GET request to the specified URI and convert the response to the provided type.
     * Note that exception handling is left to the caller (the returned CompletableFuture may
     * complete exceptionally if the request or the conversion failed for example).
     *
     * @param uri the link of the request
     * @param conversionClass the class the response should be converted to
     * @return a CompletableFuture (that may complete exceptionally) with the HTTP response converted to the desired format
     */
    public static <T> CompletableFuture<T> getAndConvert(URI uri, Class<T> conversionClass) {
        return getAndConvert(uri, TypeToken.get(conversionClass));
    }

    /**
     * See {@link #getAndConvert(URI, Class)}. This method is suited for generic types.
     */
    public static <T> CompletableFuture<T> getAndConvert(URI uri, TypeToken<T> conversionClass) {
        return get(uri).thenApply(response -> GsonTools.getInstance().fromJson(response, conversionClass));
    }

    /**
     * <p>
     *     Performs a GET request to the specified URI when the response is expected to be paginated
     *     and convert the response to JSON objects.
     * </p>
     * <p>
     *     If there are more results than the size of each page, subsequent requests are carried to obtain all results.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request or the conversion failed for example).
     * </p>
     *
     * @param uri the link of the request
     * @return a CompletableFuture (that may complete exceptionally) with a list of JSON elements
     */
    public static CompletableFuture<List<JsonElement>> getPaginated(URI uri) {
        String delimiter = uri.getQuery() == null || uri.getQuery().isEmpty() ? "?" : "&";

        return getAndConvert(uri, JsonObject.class).thenApply(response -> {
            if (!response.has("meta") || !response.get("meta").isJsonObject()) {
                throw new IllegalArgumentException(String.format("'meta' JSON object not found in %s", response));
            }
            JsonObject meta = response.getAsJsonObject("meta");

            if (!response.has("data") || !response.get("data").isJsonArray()) {
                throw new IllegalArgumentException(String.format("'data' JSON array not found in %s", response));
            }
            List<JsonElement> elements = response.getAsJsonArray("data").asList();

            if (!meta.has("limit") || !meta.get("limit").isJsonPrimitive() || !meta.get("limit").getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(String.format("'limit' number not found in %s", meta));
            }
            if (!meta.has("totalCount") || !meta.get("totalCount").isJsonPrimitive() || !meta.get("totalCount").getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(String.format("'totalCount' number not found in %s", meta));
            }

            elements.addAll(readFollowingPages(
                    uri + delimiter,
                    meta.get("limit").getAsNumber().intValue(),
                    meta.get("totalCount").getAsNumber().intValue()
            ));
            return elements;
        });
    }

    /**
     * <p>
     *     Performs a GET request to the specified URI and convert the response to an image.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request or the conversion failed for example).
     * </p>
     *
     * @param uri the link of the request
     * @return a CompletableFuture (that may complete exceptionally) with the HTTP response converted to an image
     */
    public static CompletableFuture<BufferedImage> getImage(URI uri) {
        return httpClient
                .sendAsync(getGETRequest(uri), HttpResponse.BodyHandlers.ofByteArray())
                .thenApplyAsync(response -> {
                    try (InputStream targetStream = new ByteArrayInputStream(response.body())) {
                        return ImageIO.read(targetStream);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * <p>
     *     Performs a GET request to the specified URI and convert the response to a list of JSON elements
     *     using the provided member of the response.
     * </p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request or the conversion failed for example).
     * </p>
     *
     * @param uri the link of the request
     * @param memberName the member of the response that should contain the list to convert
     * @return a CompletableFuture (that may complete exceptionally) with a list of JSON elements
     */
    public static CompletableFuture<List<JsonElement>> getAndConvertToJsonList(URI uri, String memberName) {
        return getAndConvert(uri, JsonObject.class).thenApply(response -> {
            if (response.has(memberName) && response.get(memberName).isJsonArray()) {
                return response.getAsJsonArray(memberName).asList();
            } else {
                throw new IllegalArgumentException(String.format("'%s' not found in %s", memberName, response));
            }
        });
    }

    /**
     * <p>Performs a POST request to the specified URI.</p>
     * <p>The body of the request uses the application/x-www-form-urlencoded content type.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param uri the link of the request
     * @param body the keys and values of the request body, encoded to a byte array with the UTF 8 format.
     * @param referer <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referer">
     *                the absolute or partial address from which a resource has been requested.</a>
     *                It is needed for some requests
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *              of the session
     * @return a CompletableFuture (that may complete exceptionally) with the raw HTTP response with the text format
     */
    public static CompletableFuture<String> post(URI uri, byte[] body, String referer, String token) {
        return post(
                getPOSTRequest(
                        uri,
                        HttpRequest.BodyPublishers.ofByteArray(body),
                        "application/x-www-form-urlencoded",
                        referer,
                        token
                )
        );
    }

    /**
     * <p>Performs a POST request to the specified URI.</p>
     * <p>The body of the request uses the application/json content type.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param uri the link of the request
     * @param body the keys and values of the request body with the JSON format.
     * @param referer <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referer">
     *                the absolute or partial address from which a resource has been requested.</a>
     *                It is needed for some requests
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *               of the session
     * @return the raw HTTP response (that may complete exceptionally) with the text format
     */
    public static CompletableFuture<String> post(URI uri, String body, String referer, String token) {
        return post(
                getPOSTRequest(
                        uri,
                        HttpRequest.BodyPublishers.ofString(body),
                        "application/json",
                        referer,
                        token
                )
        );
    }

    /**
     * <p>Send a file through a POST request to the specified URI.</p>
     * <p>The body of the request uses the multipart/form-data content type.</p>
     * <p>
     *     Note that exception handling is left to the caller (the returned CompletableFuture may complete exceptionally
     *     if the request failed for example).
     * </p>
     *
     * @param uri the link of the request
     * @param fileName the name of the file to send
     * @param fileContent the content of the file to send
     * @param token the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a>
     *              of the session
     * @param referer <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referer">
     *                the absolute or partial address from which a resource has been requested.</a>
     *                It is needed for some requests
     * @param parameters additional parameters to be included in the body of the request
     * @return the raw HTTP response (that may complete exceptionally) with the text format
     */
    public static CompletableFuture<String> post(
            URI uri,
            String fileName,
            String fileContent,
            String referer,
            String token,
            Map<String, String> parameters
    ) {
        String boundary = generateRandomAlphabeticText();

        String body =
                String.format("--%s\r\n", boundary) +
                String.format("Content-Disposition: form-data; name=\"annotation_file\"; filename=\"%s\"\r\n", fileName) +
                String.format("Content-Type: text/csv\r\n\r\n%s\r\n", fileContent) +
                String.format("--%s", boundary) +
                parameters.entrySet().stream()
                        .map(entry -> String.format(
                                "\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n--%s",
                                entry.getKey(),
                                entry.getValue(),
                                boundary
                        ))
                        .collect(Collectors.joining()) +
                "--\r\n";

        return post(getPOSTRequest(
                uri,
                HttpRequest.BodyPublishers.ofString(body),
                "multipart/form-data; boundary=" + boundary,
                referer,
                token
        ));
    }

    private static CompletableFuture<Boolean> isLinkReachable(HttpRequest httpRequest, int... acceptedHTTPCodes) {
        return httpClient
                .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        logger.debug(String.format("GET request to %s failed", httpRequest.uri()), error);
                        return false;
                    }

                    if (response.statusCode() != 200 || Arrays.stream(acceptedHTTPCodes).anyMatch(code -> code == response.statusCode())) {
                        logger.debug(String.format("GET request to %s failed with status code %d", httpRequest.uri(), response.statusCode()));
                        return false;
                    }

                    return true;
                });
    }

    private static HttpRequest getGETRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.of(REQUEST_TIMEOUT, SECONDS))
                .build();
    }

    private static HttpRequest getOPTIONSRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.of(REQUEST_TIMEOUT, SECONDS))
                .build();
    }

    private static List<JsonElement> readFollowingPages(String uri, int limit, int totalCount) {
        return IntStream.iterate(limit, i -> i + limit)
                .limit(max(0, (totalCount - limit) / limit))
                .mapToObj(offset -> URI.create(uri + "offset=" + offset))
                .map(currentURI -> getAndConvertToJsonList(currentURI, "data"))
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    private static HttpRequest getPOSTRequest(
            URI uri,
            HttpRequest.BodyPublisher bodyPublisher,
            String contentType,
            String referer,
            String token
    ) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .headers(
                        "Content-Type", contentType,
                        "X-CSRFToken", token,
                        "Referer", referer
                )
                .POST(bodyPublisher)
                .timeout(Duration.of(REQUEST_TIMEOUT, SECONDS))
                .build();
    }

    private static CompletableFuture<String> post(HttpRequest request) {
        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    private static String generateRandomAlphabeticText() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
