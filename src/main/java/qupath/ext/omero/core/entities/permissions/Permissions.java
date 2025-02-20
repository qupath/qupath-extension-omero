package qupath.ext.omero.core.entities.permissions;

/**
 * A class representing the OME Permissions type.
 */
class Permissions {

    private boolean isGroupRead;

    /**
     * @return whether the permission allow reading from other members of the group
     */
    public boolean isGroupRead() {
        return isGroupRead;
    }
}
