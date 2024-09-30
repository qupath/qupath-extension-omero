package qupath.ext.omero.core.entities.permissions;

import com.google.gson.annotations.SerializedName;

/**
 * A class representing the OME Permissions type.
 */
class Permissions {

    @SerializedName(value = "isGroupRead") private boolean groupRead;

    /**
     * @return whether the permission allow reading from other members of the group
     */
    public boolean isGroupRead() {
        return groupRead;
    }
}
