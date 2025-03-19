package qupath.ext.omero.core.entities.permissions;

import java.util.Optional;

/**
 * A class representing the OME Details type.
 */
public class OmeroDetails {

    private Owner owner;
    private Permissions permissions;

    /**
     * @return the full name of the owner contained in this object, or an empty Optional if not found
     */
    public Optional<String> getOwnerFullName() {
        if (owner == null) {
            return Optional.empty();
        } else {
            if (owner.getFullName().isBlank()) {
                return Optional.empty();
            } else {
                return Optional.of(owner.getFullName());
            }
        }
    }

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
