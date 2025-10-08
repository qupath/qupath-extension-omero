package qupath.ext.omero.core.apis.commonentities.shapes;

/**
 * Represent what type of {@link qupath.lib.objects.PathObject} a {@link Shape} represents.
 */
public enum ShapeType {
    /**
     * An annotation.
     */
    ANNOTATION("Annotation"),
    /**
     * A detection.
     */
    DETECTION("Detection");

    private final String displayName;

    ShapeType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return a displayable name for this shape type
     */
    public String getDisplayName() {
        return displayName;
    }
}
