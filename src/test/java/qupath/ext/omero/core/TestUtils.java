package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class TestUtils {

    @Test
    void Check_OMERO_ID_On_Project() {
        long expectedID = 201;
        URI uri = URI.create(String.format(
                "http://localhost:4080/webclient/?show=project-%d",
                expectedID
        ));

        long id = Utils.parseEntityId(uri).orElse(-1);

        Assertions.assertEquals(expectedID, id);
    }

    @Test
    void Check_OMERO_ID_On_Dataset() {
        long expectedID = 1157;
        URI uri = URI.create(String.format("http://localhost:4080/webclient/?show=dataset-%d", expectedID));

        long id = Utils.parseEntityId(uri).orElse(-1);

        Assertions.assertEquals(expectedID, id);
    }

    @Test
    void Check_OMERO_ID_On_Webclient_Image() {
        long expectedID = 12546;
        URI uri = URI.create(String.format("http://localhost:4080/webclient/?show=image-%d", expectedID));

        long id = Utils.parseEntityId(uri).orElse(-1);

        Assertions.assertEquals(expectedID, id);
    }

    @Test
    void Check_OMERO_ID_On_Webclient_Image_Alternate() {
        long expectedID = 12546;
        URI uri = URI.create(String.format("http://localhost:4080/webclient/img_detail/%d/?dataset=1157", expectedID));

        long id = Utils.parseEntityId(uri).orElse(-1);

        Assertions.assertEquals(expectedID, id);
    }

    @Test
    void Check_OMERO_ID_On_WebGateway_Image() {
        long expectedID = 12546;
        URI uri = URI.create(String.format("http://localhost:4080/webgateway/img_detail/%d/?dataset=1157", expectedID));

        long id = Utils.parseEntityId(uri).orElse(-1);

        Assertions.assertEquals(expectedID, id);
    }

    @Test
    void Check_OMERO_ID_On_IViewer_Image() {
        long expectedID = 12546;
        URI uri = URI.create(String.format("http://localhost:4080/iviewer/?images=%d&dataset=1157", expectedID));

        long id = Utils.parseEntityId(uri).orElse(-1);

        Assertions.assertEquals(expectedID, id);
    }

    @Test
    void Check_Host_Part_Of_URI() throws URISyntaxException {
        URI expectedServerURI = URI.create("http://localhost:4080");
        URI uri = URI.create(expectedServerURI + "/iviewer/?images=12546&dataset=1157");

        URI serverURI = Utils.getServerURI(uri);

        Assertions.assertEquals(expectedServerURI, serverURI);
    }
}
