package qupath.ext.omero.core.apis.webclient.annotations;

import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroRatingAnnotation;

import java.util.List;

/**
 * An annotation containing a rating (from 0 to 5).
 */
public class RatingAnnotation extends Annotation {

    private static final short MAX_VALUE = 5;
    private final short rating;

    /**
     * Create the annotation.
     *
     * @param omeroRatingAnnotation the OMERO annotation to create this annotation from
     * @param experimenters a list of experimenters that may contain the adder and owner of this annotation
     * @throws NullPointerException if one of the provided parameters is null
     */
    public RatingAnnotation(OmeroRatingAnnotation omeroRatingAnnotation, List<OmeroSimpleExperimenter> experimenters) {
        super(
                omeroRatingAnnotation.id(),
                omeroRatingAnnotation.namespace(),
                omeroRatingAnnotation.link() == null ? null : omeroRatingAnnotation.link().owner(),
                omeroRatingAnnotation.owner(),
                experimenters
        );

        this.rating = (short) Math.min(omeroRatingAnnotation.value(), MAX_VALUE);
    }

    @Override
    public String toString() {
        return String.format("Rating annotation %d with value %d", getId(), rating);
    }

    /**
     * @return the maximum value {@link #getRating()} can return
     */
    public static short getMaxValue() {
        return MAX_VALUE;
    }

    /**
     * @return the rating of the annotation (from 0 to {@link #getMaxValue()})
     */
    public short getRating() {
        return rating;
    }
}
