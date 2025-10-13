package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSupportedVersion {

    @Test
    void Check_Version_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new SupportedVersion(
                        null,
                        "version_url"
                )
        );
    }

    @Test
    void Check_Version_Url_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new SupportedVersion(
                        "version",
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        SupportedVersion expectedSupportedVersion = new SupportedVersion(
                "version",
                "version_url"
        );

        SupportedVersion supportedVersion = new Gson().fromJson(
                """
                {
                    "version": "version",
                    "url:base": "version_url"
                }
                """,
                SupportedVersion.class
        );

        Assertions.assertEquals(expectedSupportedVersion, supportedVersion);
    }
}
