package qupath.ext.omero.core.entities.permissions;

/**
 * A class representing the OME Details type.
 */
class OmeroDetail {

    private Permissions permissions;

    /**
     * @return whether the permission allow reading from other members of the group.
     * This will be false if the "permissions" object is null
     */
    public boolean canReadGroup() {
        return permissions != null && permissions.isGroupRead();
    }
}
