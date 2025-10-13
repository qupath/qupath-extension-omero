package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Represents a set of links pointing to OMERO entities.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param experimenters a link to get all experimenters of the server. Required
 * @param groups a link to get all groups of the server. Required
 * @param projects a link to get all projects of the server. Required
 * @param datasets a link to get all datasets of the server. Required
 * @param images a link to get all images of the server. Required
 * @param screens a link to get all screens of the server. Required
 * @param plates a link to get all plates of the server. Required
 * @param token a link to get a CSRF token, which is required for any POST, PUT and DELETE requests. Required
 * @param servers a link to all OMERO servers of this instance. Required
 * @param login a link to create an OMERO session. Required
 */
public record Links(
        @SerializedName("url:experimenters") String experimenters,
        @SerializedName("url:experimentergroups") String groups,
        @SerializedName("url:projects") String projects,
        @SerializedName("url:datasets") String datasets,
        @SerializedName("url:images") String images,
        @SerializedName("url:screens") String screens,
        @SerializedName("url:plates") String plates,
        @SerializedName("url:token") String token,
        @SerializedName("url:servers") String servers,
        @SerializedName("url:login") String login
) {
    public Links {
        Objects.requireNonNull(experimenters);
        Objects.requireNonNull(groups);
        Objects.requireNonNull(projects);
        Objects.requireNonNull(datasets);
        Objects.requireNonNull(images);
        Objects.requireNonNull(screens);
        Objects.requireNonNull(plates);
        Objects.requireNonNull(token);
        Objects.requireNonNull(servers);
        Objects.requireNonNull(login);
    }
}
