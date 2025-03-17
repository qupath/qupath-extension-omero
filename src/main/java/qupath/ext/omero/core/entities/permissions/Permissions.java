package qupath.ext.omero.core.entities.permissions;

/**
 * A class representing the OME Permissions type.
 */
class Permissions {

    private Boolean isGroupWrite;
    private Boolean isGroupRead;
    private Boolean isGroupAnnotate;

    /**
     * @return the permission level contained in this object
     */
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
