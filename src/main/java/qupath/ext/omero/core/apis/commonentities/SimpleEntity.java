package qupath.ext.omero.core.apis.commonentities;

/**
 * A simple class to describe an entity with an ID and a name.
 *
 * @param id the ID of the entity
 * @param name the name of the entity. Can be null
 */
public record SimpleEntity(long id, String name) {}
