package qupath.ext.omero.core.entities.permissions2.omeroentities;

import qupath.ext.omero.core.entities.permissions2.PermissionLevel;

public record OmeroPermissions(
        Boolean isGroupWrite,
        Boolean isGroupRead,
        Boolean isGroupAnnotate
) {
    public PermissionLevel getPermissionLevel() {
        if (isGroupWrite == null || isGroupRead == null || isGroupAnnotate == null) {
            return PermissionLevel.UNKNOWN;
        } else if (!isGroupRead) {
            return PermissionLevel.PRIVATE;
        } else if (!isGroupAnnotate) {
            return PermissionLevel.READ_ONLY;
        } else if (!isGroupWrite) {
            return PermissionLevel.READ_ANNOTATE;
        } else {
            return PermissionLevel.READ_WRITE;
        }
    }
}
