package qupath.ext.omero.core.imageserver;

import org.junit.jupiter.api.*;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.ROIs;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestOmeroImageServer extends OmeroServer {

    private static final UserType userType = UserType.USER;
    private static final Image image = OmeroServer.getRGBImage(userType);
    private static WebClient client;
    private static OmeroImageServer imageServer;

    @BeforeAll
    static void createImageServer() throws ExecutionException, InterruptedException {
        client = OmeroServer.createClient(userType);
        imageServer = (OmeroImageServer) new OmeroImageServerBuilder().buildServer(
                OmeroServer.getImageURI(image),
                "--pixelAPI",
                "Web"
        );
    }

    @AfterAll
    static void removeImageServer() throws Exception {
        if (imageServer != null) {
            imageServer.close();
        }
        WebClients.removeClient(client);
    }

    @Test
    void Check_Image_Can_Be_Read() throws IOException {
        TileRequest tileRequest = imageServer.getTileRequestManager().getTileRequest(0, 0, 0, 0, 0);

        BufferedImage image = imageServer.readTile(tileRequest);

        Assertions.assertNotNull(image);
    }

    @Test
    void Check_Image_Thumbnail() throws IOException {
        BufferedImage thumbnail = imageServer.getDefaultThumbnail(0, 0);

        Assertions.assertNotNull(thumbnail);
    }

    @Test
    void Check_Image_Metadata() {
        ImageServerMetadata expectedMetadata = OmeroServer.getImageMetadata(image);

        ImageServerMetadata metadata = imageServer.getMetadata();

        Assertions.assertEquals(expectedMetadata, metadata);
    }

    @Test
    void Check_Path_Objects_Read() throws ExecutionException, InterruptedException {
        List<PathObject> expectedPathObject = List.of(
                PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 10, 100, 100, null)),
                PathObjects.createAnnotationObject(ROIs.createLineROI(20, 20, 50, 50, null))
        );
        imageServer.getClient().getApisHandler().writeROIs(
                imageServer.getId(),
                expectedPathObject.stream()
                        .map(Shape::createFromPathObject)
                        .flatMap(List::stream)
                        .toList(),
                true
        ).get();

        Collection<PathObject> pathObjects = imageServer.readPathObjects();

        TestUtilities.assertCollectionsEqualsWithoutOrder(
                expectedPathObject.stream().map(PathObject::getID).toList(),
                pathObjects.stream().map(PathObject::getID).toList()
        );
    }

    @Test
    void Check_Id() {
        long expectedId = image.getId();

        long id = imageServer.getId();

        Assertions.assertEquals(expectedId, id);
    }
}
