package qupath.ext.omero.core.entities.shapes2;

public enum ShapeType {
    ANNOTATION("Annotation"),
    DETECTION("Detection");

    private final String displayName;

    ShapeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
