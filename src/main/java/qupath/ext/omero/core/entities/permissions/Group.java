package qupath.ext.omero.core.entities.permissions;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.Utils;

import java.util.List;
import java.util.ResourceBundle;

/**
 * An OMERO group represents a set of persons that own OMERO entities.
 */
public class Group {

    private static final ResourceBundle resources = Utils.getResources();
    private static final Group ALL_GROUPS = new Group(-1, resources.getString("Entities.Permissions.Group.allGroups"));
    @SerializedName(value = "@id", alternate={"groupId"}) private final int id;
    @SerializedName(value = "Name", alternate={"groupName"}) private final String name;
    @SerializedName(value = "url:experimenters") private String experimentersLink;
    @SerializedName(value = "omero:details") private OmeroDetail omeroDetail;
    private List<Owner> owners;

    /**
     * Creates an empty group only defined by its name and ID.
     *
     * @param id  the ID of the group
     * @param name  the name of the group
     */
    public Group(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("Group %s of ID %d", name, id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Group group))
            return false;
        return group.id == this.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    /**
     * @return a special group that represents all groups
     */
    public static Group getAllGroupsGroup() {
        return ALL_GROUPS;
    }

    /**
     * @return the ID of the group, or 0 if not found
     */
    public int getId() {
        return id;
    }

    /**
     * @return the name of the group, or an empty String if not found
     */
    public String getName() {
        return name == null ? "" : name;
    }

    /**
     * @return the link to get all experimenters of this group, or an empty String if not found
     */
    public String getExperimentersLink() {
        return experimentersLink == null ? "" : experimentersLink;
    }

    /**
     * @return whether this group is private (each member can only see its own data)
     */
    public boolean isPrivate() {
        return omeroDetail == null || !omeroDetail.canReadGroup();
    }

    /**
     * @return the owners belonging to this group
     */
    public synchronized List<Owner> getOwners() {
        return owners == null ? List.of() : owners;
    }

    /**
     * Set the owners belonging to this group
     *
     * @param owners the owners of this group
     * @throws NullPointerException when the provided list is null
     */
    public synchronized void setOwners(List<Owner> owners) {
        this.owners = List.copyOf(owners);
    }
}
