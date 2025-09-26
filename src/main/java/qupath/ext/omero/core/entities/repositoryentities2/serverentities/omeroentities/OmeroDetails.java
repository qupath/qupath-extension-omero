package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities;

import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

public record OmeroDetails(Owner owner, Group group) {}
