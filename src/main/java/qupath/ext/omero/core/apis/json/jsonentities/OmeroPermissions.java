package qupath.ext.omero.core.apis.json.jsonentities;

import qupath.ext.omero.core.apis.json.permissions.PermissionLevel;

import java.util.Objects;

/**
 * Represents group permissions defined by OMERO.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param isGroupWrite whether the group owner and regular members can view, perform annotations, and edit and delete member data.
 *                     Required
 * @param isGroupRead whether the group owner and regular members can view member data, and the group owner can perform annotations on
 *                    member data. Required
 * @param isGroupAnnotate whether the group owner and regular members can view and perform annotations on member data, and the group
 *                        owner can edit and delete member data. Required
 */
public record OmeroPermissions(
        Boolean isGroupWrite,
        Boolean isGroupRead,
        Boolean isGroupAnnotate
) {
    public OmeroPermissions {
        Objects.requireNonNull(isGroupWrite);
        Objects.requireNonNull(isGroupRead);
        Objects.requireNonNull(isGroupAnnotate);
    }

    /**
     * Get a {@link PermissionLevel} corresponding to this instance.
     *
     * @return a {@link PermissionLevel} corresponding to this instance
     */
    public PermissionLevel getPermissionLevel() {
        if (!isGroupRead) {
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
