package qupath.ext.omero.core.entities.annotations.annotationsentities;

import qupath.ext.omero.core.entities.permissions.Owner;

import java.util.Objects;
import java.util.Optional;

/**
 * An OMERO link indicates which owner added an OMERO annotation to an OMERO entity.
 *
 * @param owner the owner who linked the OMERO entity (can be null if not indicated)
 */
public record Link(Owner owner) {}
