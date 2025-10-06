package qupath.ext.omero.core.entities.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.omeroentities.experimenters.OmeroExperimenterGroup;

import java.util.List;

public class ExperimenterGroup {

    private static final Logger logger = LoggerFactory.getLogger(ExperimenterGroup.class);
    private static final String EXPECTED_TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#ExperimenterGroup";
    private final long id;
    private final String name;
    private final List<Experimenter> experimenters;

    public ExperimenterGroup(OmeroExperimenterGroup omeroExperimenterGroup, List<Experimenter> experimenters) {
        if (!EXPECTED_TYPE.equals(omeroExperimenterGroup.type())) {
            logger.warn(
                    "The provided OMERO experimenter group doesn't have the expected type ({} instead of {}). Some attributes may not be parsed correctly",
                    omeroExperimenterGroup.type(),
                    EXPECTED_TYPE
            );
        }

        this.id = omeroExperimenterGroup.id();
        this.name = omeroExperimenterGroup.name();  //TODO: can be null
        this.experimenters = experimenters;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Experimenter> getExperimenters() {
        return experimenters;
    }
}
