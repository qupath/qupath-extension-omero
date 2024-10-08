package qupath.ext.omero.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.TestUtilities;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class TestRequestSender extends OmeroServer {

    @Test
    void Check_Link_Reachable() {
        URI reachableLink = URI.create(OmeroServer.getWebServerURI());

        Assertions.assertDoesNotThrow(() -> RequestSender.isLinkReachableWithGet(reachableLink));
    }

    @Test
    void Check_Link_Unreachable() {
        URI unreachableLink = URI.create("http://invalid.invalid");

        Assertions.assertThrows(CompletionException.class, () -> RequestSender.isLinkReachableWithGet(unreachableLink).get());
    }

    @Test
    void Check_Get_Request() {
        URI reachableLink = URI.create(OmeroServer.getWebServerURI());

        Assertions.assertDoesNotThrow(() -> RequestSender.get(reachableLink).get());
    }

    @Test
    void Check_Get_Request_On_Invalid_Link() {
        URI unreachableLink = URI.create("http://invalid.invalid");

        Assertions.assertThrows(ExecutionException.class, () -> RequestSender.get(unreachableLink).get());
    }

    @Test
    void Check_Get_Request_And_Convert() throws ExecutionException, InterruptedException {
        URI apiLink = URI.create(OmeroServer.getWebServerURI() + "/api/");
        ApiResponse expectedResponse = new ApiResponse(OmeroServer.getWebServerURI());

        ApiResponse response = RequestSender.getAndConvert(apiLink, ApiResponse.class).get();

        Assertions.assertEquals(expectedResponse, response);
    }

    @Test
    void Check_Get_Request_And_Convert_On_Invalid_Link() {
        URI invalidApiLink = URI.create(OmeroServer.getWebServerURI());

        Assertions.assertThrows(CompletionException.class, () ->
                RequestSender.getAndConvert(invalidApiLink, ApiResponse.class).get()
        );
    }

    @Test
    void Check_Get_Image() throws ExecutionException, InterruptedException {
        URI imageLink = URI.create(OmeroServer.getWebServerURI() + "/static/webgateway/img/folder16.png");

        BufferedImage image = RequestSender.getImage(imageLink).get();

        Assertions.assertNotNull(image);
    }

    @Test
    void Check_Get_Image_On_Invalid_Link() {
        URI invalidImageLink = URI.create(OmeroServer.getWebServerURI());

        Assertions.assertThrows(CompletionException.class, () -> RequestSender.getImage(invalidImageLink).get());
    }

    @Test
    void Check_Get_Request_And_Convert_To_JSON_List() throws ExecutionException, InterruptedException {
        URI jsonListLink = URI.create(OmeroServer.getWebServerURI() + "/api/");
        String memberName = "data";
        List<JsonElement> expectedResponse = List.of(new Gson().toJsonTree(new ApiResponseVersion(OmeroServer.getWebServerURI())));

        List<JsonElement> response = RequestSender.getAndConvertToJsonList(jsonListLink, memberName).get();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedResponse, response);
    }

    @Test
    void Check_Get_Request_And_Convert_To_JSON_List_On_Invalid_Request() {
        URI invalidJsonListLink = URI.create(OmeroServer.getWebServerURI());
        String memberName = "data";

        Assertions.assertThrows(CompletionException.class, () -> RequestSender.getAndConvertToJsonList(invalidJsonListLink, memberName));
    }

    @Test
    void Check_Get_Request_And_Convert_To_JSON_List_With_Invalid_Member() {
        URI jsonListLink = URI.create(OmeroServer.getWebServerURI() + "/api/");
        String memberName = "invalid";

        Assertions.assertThrows(CompletionException.class, () -> RequestSender.getAndConvertToJsonList(jsonListLink, memberName));
    }

    private static class ApiResponse {
        @SerializedName("data") private List<ApiResponseVersion> versions;

        public ApiResponse(String baseAddress) {
            versions = List.of(new ApiResponseVersion(baseAddress));
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof ApiResponse other))
                return false;
            return other.versions.equals(versions);
        }

        @Override
        public String toString() {
            return versions.toString();
        }
    }

    private static class ApiResponseVersion {
        @SerializedName("version") private String version;
        @SerializedName("url:base") private String url;

        public ApiResponseVersion(String baseAddress) {
            version = "0";
            url = baseAddress + "/api/v0/";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof ApiResponseVersion other))
                return false;
            return other.version.equals(version) && other.url.equals(url);
        }

        @Override
        public String toString() {
            return "version: " + version + "; url: " + url;
        }
    }
}




