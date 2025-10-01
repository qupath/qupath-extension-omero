package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.annotations.SerializedName;

public record Token(@SerializedName("date") String token) {}
