package qupath.ext.omero.core.apis.json.permissions;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenterGroup;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * An experimenter group represents a set of {@link Experimenter} that own OMERO entities.
 */
public class ExperimenterGroup {

    private static final ResourceBundle resources = Utils.getResources();
    private static final ExperimenterGroup ALL_GROUPS = new ExperimenterGroup(
            -1,
            resources.getString("Entities.Permissions.Group.allGroups"),
            PermissionLevel.UNKNOWN,
            List.of()
    );
    private final long id;
    private final String name;
    private final PermissionLevel permissionLevel;
    private final List<Experimenter> experimenters;

    /**
     * Create a group from a {@link OmeroExperimenterGroup} and a list of {@link Experimenter}.
     *
     * @param omeroExperimenterGroup the OMERO experimenter group containing information about the experimenter group to create
     * @param experimenters the experimenters that should belong to this group
     * @throws NullPointerException if one of the provided parameters is null
     */
    public ExperimenterGroup(OmeroExperimenterGroup omeroExperimenterGroup, List<Experimenter> experimenters) {
        this(omeroExperimenterGroup.id(), omeroExperimenterGroup.name(), omeroExperimenterGroup.getPermissionLevel(), Objects.requireNonNull(experimenters));
    }

    private ExperimenterGroup(long id, String name, PermissionLevel permissionLevel, List<Experimenter> experimenters) {
        this.id = id;
        this.name = name;
        this.permissionLevel = permissionLevel;
        this.experimenters = experimenters;
    }

    /**
     * @return a special group that represents all groups. Its ID is -1, it contains no experimenters, and has a localized name
     */
    public static ExperimenterGroup getAllGroups() {
        return ALL_GROUPS;
    }

    @Override
    public String toString() {
        return String.format("Group with ID %d", id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof ExperimenterGroup experimenterGroup))
            return false;
        return experimenterGroup.id == this.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * @return the unique ID of this group
     */
    public long getId() {
        return id;
    }

    /**
     * @return the name of the group, or an empty Optional if not defined
     */
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * @return the {@link PermissionLevel} of this group
     */
    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    /**
     * @return the experimenters that belong to this group
     */
    public List<Experimenter> getExperimenters() {
        return experimenters;
    }
}
