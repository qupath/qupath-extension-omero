package qupath.ext.omero.core.apis.webclient.annotations;

import java.util.Objects;

/**
 * Represents basic information on an OMERO experimenter.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param id the ID of the experimenter. Required
 * @param firstName the first name of the experimenter. Optional
 * @param lastName the last name of the experimenter. Optional
 */
record OmeroSimpleExperimenter(
        Long id,
        String firstName,
        String lastName
) {
    public OmeroSimpleExperimenter {
        Objects.requireNonNull(id);
    }
}
