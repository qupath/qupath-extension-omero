package qupath.ext.omero.core.entities.permissions;

/**
 * A class representing the OME Details type.
 */
class OmeroDetail {

    private Permissions permissions;

    /**
     * @return the permission level contained in this object
     */
    public PermissionLevel getPermissionLevel() {
        if (permissions == null) {
            return PermissionLevel.UNKNOWN;
        } else {
            return permissions.getPermissionLevel();
        }
    }
}
