package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;

import java.util.List;

public record OmeroPlateAcquisition(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "omero:wellsampleIndex") List<Integer> wellSampleIndices,
        @SerializedName(value = "StartTime") long startTime,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    private static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#PlateAcquisition";

    public OmeroPlateAcquisition {
        if (!TYPE.equalsIgnoreCase(type)) {
            throw new IllegalArgumentException(String.format(
                    "The provided type %s does not correspond to the expected type %s",
                    type,
                    TYPE
            ));
        }
    }

    public Owner owner() {
        return omeroDetails == null ? null : omeroDetails.owner();
    }

    public Group group() {
        return omeroDetails == null ? null : omeroDetails.group();
    }
}
