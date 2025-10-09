package qupath.ext.omero.core.apis.json.permissions;

/**
 * A level of permission as described in the
 * <a href="https://omero.readthedocs.io/en/stable/sysadmins/server-permissions.html">OMERO groups and permission system documentation page</a>.
 *
 */
public enum PermissionLevel {
    /**
     * The group owner can view member data while regular members can only view their own data.
     */
    PRIVATE,
    /**
     * The group owner and regular members can view member data, and the group owner can perform annotations on member data.
     */
    READ_ONLY,
    /**
     * The group owner and regular members can view and perform annotations on member data, and the group owner can edit and delete member data.
     */
    READ_ANNOTATE,
    /**
     * The group owner and regular members can view, perform annotations, and edit and delete member data.
     */
    READ_WRITE
}
