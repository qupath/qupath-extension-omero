package qupath.ext.omero.core.entities.annotations;

import qupath.ext.omero.gui.UiUtilities;

import java.util.ResourceBundle;

/**
 * An annotation containing a rating (from 0 to 5).
 */
public class RatingAnnotation extends Annotation {

    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final short MAX_VALUE = 5;
    private short longValue;

    @Override
    public String toString() {
        return String.format("%s. Value: %d", super.toString(), longValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof RatingAnnotation ratingAnnotation))
            return false;
        return ratingAnnotation.longValue == longValue;
    }

    @Override
    public int hashCode() {
        return Short.hashCode(longValue);
    }

    /**
     * @return a localized title for a map annotation
     */
    public static String getTitle() {
        return resources.getString("Web.Entities.Annotation.Rating.title");
    }

    /**
     * @return the maximum value {@link #getValue()} can return
     */
    public static short getMaxValue() {
        return MAX_VALUE;
    }

    /**
     * Indicates if an annotation type refers to a rating annotation.
     *
     * @param type the annotation type
     * @return whether this annotation type refers to a rating annotation
     */
    public static boolean isOfType(String type) {
        return "LongAnnotationI".equalsIgnoreCase(type) || "rating".equalsIgnoreCase(type);
    }

    /**
     * @return the rating of the annotation (from 0 to {@link #getMaxValue()}),
     * or 0 if the value is missing from the annotation
     */
    public short getValue() {
        return longValue > MAX_VALUE ? MAX_VALUE : longValue;
    }
}
