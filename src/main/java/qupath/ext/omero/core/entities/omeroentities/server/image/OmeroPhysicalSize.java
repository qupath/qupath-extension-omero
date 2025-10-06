package qupath.ext.omero.core.entities.omeroentities.server.image;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * Represents the physical size (width or height) of pixels of an OMERO image.
 * <p>
 * A {@link NullPointerException} is thrown if one required parameter is null.
 *
 * @param symbol a text describing the unit of the size
 * @param value the size
 */
public record OmeroPhysicalSize(
        @SerializedName(value = "Symbol") String symbol,
        @SerializedName(value = "Value") Double value
) {
    public OmeroPhysicalSize {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(value);
    }
}
