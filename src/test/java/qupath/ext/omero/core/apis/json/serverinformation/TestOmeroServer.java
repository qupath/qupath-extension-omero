package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroServer {

    @Test
    void Check_Host_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroServer(
                        null,
                        234,
                        43
                )
        );
    }

    @Test
    void Check_Id_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroServer(
                        "host",
                        null,
                        43
                )
        );
    }

    @Test
    void Check_Port_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new OmeroServer(
                        "host",
                        234,
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        OmeroServer expectedOmeroServer = new OmeroServer(
                "host",
                234,
                43
        );

        OmeroServer omeroServer = new Gson().fromJson(
                """
                {
                    "host": "host",
                    "id": 234,
                    "port": 43
                }
                """,
                OmeroServer.class
        );

        Assertions.assertEquals(expectedOmeroServer, omeroServer);
    }
}
