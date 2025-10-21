package qupath.ext.omero.core.apis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.EntityType;
import qupath.ext.omero.core.apis.webclient.SimpleServerEntity;

import java.net.URI;

public class TestEntityParser {

    @Test
    void Check_Project_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.PROJECT, 201);
        URI uri = URI.create("http://localhost:4080/webclient/?show=project-201");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Dataset_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.DATASET, 1157);
        URI uri = URI.create("http://localhost:4080/webclient/?show=dataset-1157");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Webclient_Image_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.IMAGE, 123);
        URI uri = URI.create("http://localhost:4080/webclient/?show=image-123");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Webclient_Image_Alternate_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.IMAGE, 45);
        URI uri = URI.create("http://localhost:4080/webclient/img_detail/45/?dataset=1157");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_WebGateway_Image_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.IMAGE, 876);
        URI uri = URI.create("http://localhost:4080/webgateway/img_detail/876/?dataset=1157");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_IViewer_Image_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.IMAGE, 90);
        URI uri = URI.create("http://localhost:4080/iviewer/?images=90&dataset=1157");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Well_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.WELL, 43);
        URI uri = URI.create("http://localhost:4080/webclient/?show=well-43");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Plate_Acquisition_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.PLATE_ACQUISITION, 333);
        URI uri = URI.create("http://localhost:4080/webclient/?show=run-333");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Plate_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.PLATE, 332);
        URI uri = URI.create("http://localhost:4080/webclient/?show=plate-332");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Screen_Uri() {
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.SCREEN, 4443);
        URI uri = URI.create("http://localhost:4080/webclient/?show=screen-4443");

        SimpleServerEntity entity = EntityParser.parseUri(uri).orElseThrow();

        Assertions.assertEquals(expectedEntity, entity);
    }
}
