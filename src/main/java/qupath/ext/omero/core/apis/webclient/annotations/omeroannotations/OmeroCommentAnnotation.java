package qupath.ext.omero.core.apis.webclient.annotations.omeroannotations;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Represents an OMERO annotation containing a text comment.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param id the ID of the annotation. Required
 * @param namespace the namespace of the annotation. Optional
 * @param type a text describing this object ({@link #TYPE} is expected). Optional
 * @param owner the owner of this annotation. Optional
 * @param link a link containing the adder of this annotation. Optional
 * @param comment a text comment. Required
 */
public record OmeroCommentAnnotation(
        Long id,
        @SerializedName("ns") String namespace,
        @SerializedName("class") String type,
        OmeroAnnotationExperimenter owner,
        OmeroLink link,
        @SerializedName("textValue") String comment
) {
    public static final String TYPE = "CommentAnnotationI";
    private static final Logger logger = LoggerFactory.getLogger(OmeroCommentAnnotation.class);

    public OmeroCommentAnnotation {
        Objects.requireNonNull(id, "id not provided");
        Objects.requireNonNull(comment, "textValue not provided");

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent a comment annotation",
                    type,
                    TYPE
            );
        }
    }
}
