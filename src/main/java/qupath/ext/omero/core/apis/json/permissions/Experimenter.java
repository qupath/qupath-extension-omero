package qupath.ext.omero.core.apis.json.permissions;

import qupath.ext.omero.Utils;
import qupath.ext.omero.core.apis.json.jsonentities.experimenters.OmeroExperimenter;

import java.util.Objects;
import java.util.ResourceBundle;

/**
 * An OMERO experimenter represents a person that own OMERO entities.
 */
public class Experimenter {

    private static final ResourceBundle resources = Utils.getResources();
    private static final Experimenter ALL_EXPERIMENTERS = new Experimenter(
            -1,
            resources.getString("Entities.Permissions.Owner.allMembers")
    );
    private final long id;
    private final String fullName;

    /**
     * Create an experimenter from a {@link OmeroExperimenter}.
     *
     * @param omeroExperimenter the OMERO experimenter containing information about the experimenter to create
     * @throws NullPointerException if the provided OMERO experimenter is null
     */
    public Experimenter(OmeroExperimenter omeroExperimenter) {
        this(omeroExperimenter.id(), omeroExperimenter.fullName());
    }

    private Experimenter(long id, String fullName) {
        this.id = id;
        this.fullName = fullName;
    }

    /**
     * @return a special experimenter that represents all experimenters. Its ID is -1 and has a localized name
     */
    public static Experimenter getAllExperimenters() {
        return ALL_EXPERIMENTERS;
    }

    @Override
    public String toString() {
        return String.format("Experimenter %s with ID %d", getFullName(), id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Experimenter experimenter))
            return false;
        return experimenter.id == this.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * @return the unique ID of this experimenter
     */
    public long getId() {
        return id;
    }

    /**
     * @return the full name (first, middle and last name) of the experimenter, or an empty String if not found
     */
    public String getFullName() {
        return fullName;
    }
}
