package qupath.ext.omero.core.apis.json.repositoryentities.serverentities;

/**
 * A key-value pair.
 *
 * @param label the label of the pair
 * @param value the value of the pair
 */
public record Attribute(String label, String value) {}
