package qupath.ext.omero.core.entities.permissions2.omeroentities;

import com.google.gson.annotations.SerializedName;

public record OmeroExperimenter(
        @SerializedName(value = "@type") String type,
        @SerializedName(value = "@id") Long id,
        @SerializedName(value = "FirstName") String firstName,
        @SerializedName(value = "MiddleName") String middleName,
        @SerializedName(value = "LastName") String lastName,
        @SerializedName(value = "Email") String emailAddress,
        @SerializedName(value = "Institution") String institution,
        @SerializedName(value = "UserName") String username
) {}
