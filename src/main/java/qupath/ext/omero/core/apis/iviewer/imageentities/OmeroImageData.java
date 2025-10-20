package qupath.ext.omero.core.apis.iviewer.imageentities;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Represent information on an OMERO image.
 *
 * @param metadata some metadata of the image. Optional
 * @param channels information on each channel of the image. Optional
 */
public record OmeroImageData(
        @SerializedName("meta") OmeroImageMetadata metadata,
        List<OmeroImageChannel> channels
) {}
