package qupath.ext.omero.core.apis.json.jsonentities.experimenters;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An OMERO experimenter as described by the <a href="http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter">OME specifications</a>.
 * <p>
 * A {@link RuntimeException} is thrown if one required parameter is null.
 *
 * @param type a link to the specifications of this object ({@link #TYPE} is expected). Optional
 * @param id the ID of the experimenter. Required
 * @param firstName the first name of the experimenter. Optional
 * @param middleName the middle name of the experimenter. Optional
 * @param lastName the last name of the experimenter. Optional
 */
public record OmeroExperimenter(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "FirstName") String firstName,
        @SerializedName(value = "MiddleName") String middleName,
        @SerializedName(value = "LastName") String lastName
) {
    public static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Experimenter";
    private static final Logger logger = LoggerFactory.getLogger(OmeroExperimenter.class);

    public OmeroExperimenter {
        Objects.requireNonNull(id);

        if (!TYPE.equals(type)) {
            logger.warn(
                    "The provided type {} does not correspond to the expected type {}. The created object might not represent an experimenter",
                    type,
                    TYPE
            );
        }
    }

    /**
     * @return the full name (first, middle and last name) of the experimenter, or an empty String if not found
     */
    public String fullName() {
        return Stream.of(firstName, middleName, lastName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
