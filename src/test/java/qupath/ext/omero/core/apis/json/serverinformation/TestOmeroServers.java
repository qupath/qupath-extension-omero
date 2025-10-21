package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestOmeroServers {

    @Test
    void Check_Servers_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroServers(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroServers expectedOmeroServers = new OmeroServers(List.of(
                new OmeroServer("host1", 1, 2),
                new OmeroServer("host2", 3, 4)
        ));

        OmeroServers omeroServers = new Gson().fromJson(
                """
                {
                    "data": [
                        {
                            "host": "host1",
                            "id": 1,
                            "port": 2
                        },
                        {
                            "host": "host2",
                            "id": 3,
                            "port": 4
                        }
                    ]
                }
                """,
                OmeroServers.class
        );

        Assertions.assertEquals(expectedOmeroServers, omeroServers);
    }
}
