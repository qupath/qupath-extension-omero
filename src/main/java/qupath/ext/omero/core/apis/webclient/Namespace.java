package qupath.ext.omero.core.apis.webclient;

/**
 * A namespace to group key-value pairs on OMERO.
 *
 * @param name the name of the namespace
 */
public record Namespace(String name) {

    private static final Namespace DEFAULT_NAMESPACE = new Namespace("openmicroscopy.org/omero/client/mapAnnotation");

    /**
     * @return the default namespace of key-value pairs on OMERO
     */
    public static Namespace getDefaultNamespace() {
        return DEFAULT_NAMESPACE;
    }
}
