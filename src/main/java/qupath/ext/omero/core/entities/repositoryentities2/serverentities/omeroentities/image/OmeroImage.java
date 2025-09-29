package qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.image;

import com.google.gson.annotations.SerializedName;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities2.serverentities.omeroentities.OmeroDetails;
import qupath.lib.images.servers.PixelType;

public record OmeroImage(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") long id,
        @SerializedName(value = "Name") String name,
        @SerializedName(value = "AcquisitionDate") long acquisitionDate,
        @SerializedName(value = "Pixels") OmeroPixels pixels,
        @SerializedName(value = "omero:details:") OmeroDetails omeroDetails
) {
    private static final String TYPE = "http://www.openmicroscopy.org/Schemas/OME/2016-06#Image";

    public OmeroImage {
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

    public int sizeX() {
        return pixels == null ? -1 : pixels.sizeX();
    }

    public int sizeY() {
        return pixels == null ? -1 : pixels.sizeY();
    }

    public int sizeZ() {
        return pixels == null ? -1 : pixels.sizeZ();
    }

    public int sizeC() {
        return pixels == null ? -1 : pixels.sizeC();
    }

    public int sizeT() {
        return pixels == null ? -1 : pixels.sizeT();
    }

    public PixelType pixelType() {
        return pixels == null ? null : pixels.pixelType();
    }

    public double sizeMebibyte() {
        return pixels == null ? 0 : pixels().sizeMebibyte();
    }

    public OmeroPhysicalSize physicalSizeX() {
        if (pixels == null || pixels.physicalSizeX() == null || pixels.physicalSizeX().value() < 0 || pixels.physicalSizeX().symbol() == null) {
            return null;
        } else {
            return pixels.physicalSizeX();
        }
    }

    public OmeroPhysicalSize physicalSizeY() {
        if (pixels == null || pixels.physicalSizeY() == null || pixels.physicalSizeY().value() < 0 || pixels.physicalSizeY().symbol() == null) {
            return null;
        } else {
            return pixels.physicalSizeY();
        }
    }

    public OmeroPhysicalSize physicalSizeZ() {
        if (pixels == null || pixels.physicalSizeZ() == null || pixels.physicalSizeZ().value() < 0 || pixels.physicalSizeZ().symbol() == null) {
            return null;
        } else {
            return pixels.physicalSizeZ();
        }
    }

    public String imageType() {
        if (pixels == null || pixels.imageType() == null) {
            return null;
        } else {
            return pixels.imageType().value();
        }
    }
}
