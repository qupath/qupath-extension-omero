package qupath.ext.omero.core.apis.webgateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.apis.commonentities.image.ChannelSettings;
import qupath.ext.omero.core.apis.json.JsonApi;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TestWebGatewayApi extends OmeroServer {

    abstract static class GenericUser {

        protected static WebGatewayApi webGatewayApi;
        protected static RequestSender requestSender;
        protected static OmeroServer.UserType userType;

        @AfterAll
        static void closeRequestSender() throws Exception {
            requestSender.close();
        }

        @Test
        void Check_Project_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webGatewayApi.getProjectIcon().get());
        }

        @Test
        void Check_Dataset_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webGatewayApi.getDatasetIcon().get());
        }

        @Test
        void Check_Orphaned_Folder_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webGatewayApi.getOrphanedFolderIcon().get());
        }

        @Test
        void Check_Well_Icon() throws ExecutionException, InterruptedException {
            Assertions.assertNotNull(webGatewayApi.getWellIcon().get());
        }

        @Test
        void Check_Image_Thumbnail_With_Specific_Size() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRgbImage(userType).id();
            int size = 30;

            BufferedImage image = webGatewayApi.getThumbnail(imageId, size).get();

            Assertions.assertEquals(size, Math.max(image.getWidth(), image.getHeight()));
        }

        @Test
        void Check_Image_Thumbnail_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webGatewayApi.getThumbnail(invalidId, 20).get()
            );
        }

        @Test
        void Check_Image_Metadata() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getComplexImage(userType).id();
            ImageServerMetadata expectedMetadata = OmeroServer.getComplexImageMetadata();

            ImageServerMetadata metadata = webGatewayApi.getImageMetadata(imageId).get();

            Assertions.assertEquals(expectedMetadata, metadata);
        }

        @Test
        void Check_Image_Metadata_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webGatewayApi.getImageMetadata(invalidId).get()
            );
        }

        @Test
        void Check_Tile_Size() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getRgbImage(userType).id();
            int expectedWidth = OmeroServer.getRgbImageMetadata().getWidth();
            int expectedHeight = OmeroServer.getRgbImageMetadata().getHeight();

            BufferedImage tile = webGatewayApi.readTile(
                    imageId,
                    TileRequest.createInstance("", 0, 1, ImageRegion.createInstance(0, 0, expectedWidth, expectedHeight, 0, 0)),
                    expectedWidth,
                    expectedHeight,
                    1
            ).get();

            Assertions.assertEquals(expectedWidth, tile.getWidth());
            Assertions.assertEquals(expectedHeight, tile.getHeight());
        }

        @Test
        void Check_Tile_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webGatewayApi.readTile(
                            invalidId,
                            TileRequest.createInstance("", 0, 1, ImageRegion.createInstance(0, 0, 1, 1, 0, 0)),
                            1,
                            1,
                            1
                    ).get()
            );
        }

        @Test
        abstract void Check_Channel_Colors_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        void Check_Channel_Colors_Cannot_Be_Changed_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webGatewayApi.changeChannelColors(invalidId, List.of(), List.of()).get()
            );
        }

        @Test
        abstract void Check_Channel_Display_Ranges_Can_Be_Changed() throws ExecutionException, InterruptedException;

        @Test
        void Check_Channel_Display_Ranges_Cannot_Be_Changed_With_Invalid_Image_Id() {
            long invalidId = -1;

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webGatewayApi.changeChannelDisplayRanges(invalidId, List.of(), List.of()).get()
            );
        }
    }

    @Nested
    class AuthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.AUTHENTICATED;
            requestSender = new RequestSender();
            JsonApi jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()),requestSender, OmeroServer.getCredentials(userType));
            webGatewayApi = new WebGatewayApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }

        @Test
        @Override
        void Check_Channel_Colors_Can_Be_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            ChannelSettings originalSettings = OmeroServer.getModifiableImageChannelSettings();
            int channelColor = Integer.parseInt("00FFFF", 16);

            Assertions.assertDoesNotThrow(() -> webGatewayApi.changeChannelColors(
                    imageId,
                    List.of(channelColor),
                    List.of(originalSettings)
            ).get());

            // Reset channel colors
            webGatewayApi.changeChannelColors(
                    imageId,
                    List.of(originalSettings.rgbColor()),
                    List.of(new ChannelSettings(
                            originalSettings.name(),
                            originalSettings.minDisplayRange(),
                            originalSettings.maxDisplayRange(),
                            channelColor
                    ))
            ).get();
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Can_Be_Changed() throws ExecutionException, InterruptedException {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            ChannelSettings originalSettings = OmeroServer.getModifiableImageChannelSettings();
            ChannelSettings channelSettings = new ChannelSettings(0.45, 100.654);

            Assertions.assertDoesNotThrow(() -> webGatewayApi.changeChannelDisplayRanges(
                    imageId,
                    List.of(channelSettings),
                    List.of(originalSettings)
            ).get());

            // Reset channel display ranges
            webGatewayApi.changeChannelDisplayRanges(
                    imageId,
                    List.of(originalSettings),
                    List.of(new ChannelSettings(
                            originalSettings.name(),
                            channelSettings.minDisplayRange(),
                            channelSettings.maxDisplayRange(),
                            originalSettings.rgbColor()
                    ))
            ).get();
        }
    }

    @Nested
    class UnauthenticatedUser extends GenericUser {

        @BeforeAll
        static void createClient() throws URISyntaxException, ExecutionException, InterruptedException {
            userType = UserType.UNAUTHENTICATED;
            requestSender = new RequestSender();
            JsonApi jsonApi = new JsonApi(URI.create(OmeroServer.getWebServerURI()),requestSender, OmeroServer.getCredentials(userType));
            webGatewayApi = new WebGatewayApi(URI.create(OmeroServer.getWebServerURI()), requestSender, jsonApi.getToken());
        }

        @Test
        @Override
        void Check_Channel_Colors_Can_Be_Changed() {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            ChannelSettings originalSettings = OmeroServer.getModifiableImageChannelSettings();
            int channelColor = Integer.parseInt("00FFFF", 16);

            Assertions.assertThrows(
                    ExecutionException.class,
                    () -> webGatewayApi.changeChannelColors(
                            imageId,
                            List.of(channelColor),
                            List.of(originalSettings)
                    ).get()
            );
        }

        @Test
        @Override
        void Check_Channel_Display_Ranges_Can_Be_Changed() {
            long imageId = OmeroServer.getModifiableImage(userType).id();
            ChannelSettings originalSettings = OmeroServer.getModifiableImageChannelSettings();
            ChannelSettings channelSettings = new ChannelSettings(0.45, 100.654);

            Assertions.assertThrows(ExecutionException.class,
                    () -> webGatewayApi.changeChannelDisplayRanges(
                            imageId,List.of(channelSettings),
                            List.of(originalSettings)
                    ).get()
            );
        }
    }
}
