package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestSupportedVersions {

    @Test
    void Check_Supported_Versions_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new SupportedVersions(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        SupportedVersions expectedSupportedVersions = new SupportedVersions(List.of(
                new SupportedVersion("version1", "version_url1"),
                new SupportedVersion("version2", "version_url2")
        ));

        SupportedVersions supportedVersions = new Gson().fromJson(
                """
                {
                    "data": [
                        {
                            "version": "version1",
                            "url:base": "version_url1"
                        },
                        {
                            "version": "version2",
                            "url:base": "version_url2"
                        }
                    ]
                }
                """,
                SupportedVersions.class
        );

        Assertions.assertEquals(expectedSupportedVersions, supportedVersions);
    }
}
