package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.annotations.SerializedName;

/**
 * This class holds information about a physical size (value and unit).
 *
 * @param symbol the unit of this size (can be empty if not found)
 * @param value the value of this size (can be 0 if not found)
 */
record PhysicalSize(@SerializedName(value = "Symbol") String symbol, @SerializedName(value = "Value") double value) {

    @Override
    public String symbol() {
        return symbol == null ? "" : symbol;
    }
}
