package qupath.ext.omero.core.entities.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.entities.omeroentities.experimenters.OmeroExperimenter;

public class Experimenter {

    private static final Logger logger = LoggerFactory.getLogger(Experimenter.class);
    private static final String EXPECTED_TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter";
    private final long id;

    public Experimenter(OmeroExperimenter omeroExperimenter) {
        if (omeroExperimenter.id() == null) {
            throw new IllegalArgumentException("Experimenter ID not found");
        }
        if (!EXPECTED_TYPE.equals(omeroExperimenter.type())) {
            logger.warn(
                    "The provided OMERO experimenter doesn't have the expected type ({} instead of {}). Some attributes may not be parsed correctly",
                    omeroExperimenter.type(),
                    EXPECTED_TYPE
            );
        }

        this.id = omeroExperimenter.id();
    }

    public long getId() {
        return id;
    }
}
