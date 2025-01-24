package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class TestOmeroApi {

    @Test
    void Check_Empty() {
        OmeroApi omeroAPI = new Gson().fromJson("{}", OmeroApi.class);

        Optional<String> latestVersionURL = omeroAPI.getLatestVersionURL();

        Assertions.assertTrue(latestVersionURL.isEmpty());
    }

    @Test
    void Check_Latest_Version_URL() {
        OmeroApi omeroAPI = createOmeroAPI();

        String latestVersionURL = omeroAPI.getLatestVersionURL().orElse("");

        Assertions.assertEquals("https://idr.openmicroscopy.org/api/v1/", latestVersionURL);
    }

    private OmeroApi createOmeroAPI() {
        String json = """
                {
                    "data": [
                        {
                            "url:base": "https://idr.openmicroscopy.org/api/v0/"
                        },
                        {
                            "url:base": "https://idr.openmicroscopy.org/api/v1/"
                        }
                    ]
                }
                """;
        return new Gson().fromJson(json, OmeroApi.class);
    }
}
