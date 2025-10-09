package qupath.ext.omero.core.apis.webclient;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroDataset;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroPlate;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroPlateAcquisition;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroProject;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroScreen;
import qupath.ext.omero.core.apis.json.jsonentities.server.OmeroWell;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImage;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroImageType;
import qupath.ext.omero.core.apis.json.jsonentities.server.image.OmeroPixels;
import qupath.ext.omero.core.apis.json.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Attribute;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Image;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Plate;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Well;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TestSimpleServerEntity {

    @Test
    void Check_Creation_From_Screen() {
        long id = 0;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.SCREEN, id);
        Screen screen = new Screen(
                new OmeroScreen(
                        null,
                        id,
                        null,
                        null,
                        8,
                        null
                ),
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(screen);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_Plate() {
        long id = 53323;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.PLATE, id);
        Plate plate = new Plate(
                new OmeroPlate(
                        null,
                        id,
                        null,
                        0,
                        0,
                        null
                ),
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(plate);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_Plate_Acquisition() {
        long id = 533;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.PLATE_ACQUISITION, id);
        PlateAcquisition plateAcquisition = new PlateAcquisition(
                new OmeroPlateAcquisition(
                        null,
                        id,
                        null,
                        null,
                        null,
                        null
                ),
                3,
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(plateAcquisition);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_Well() {
        long id = 33;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.WELL, id);
        Well well = new Well(
                new OmeroWell(
                        null,
                        id,
                        null,
                        null,
                        8,
                        null,
                        null
                ),
                4,
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(well);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_Project() {
        long id = 353;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.PROJECT, id);
        Project project = new Project(
                new OmeroProject(
                        null,
                        id,
                        null,
                        null,
                        8,
                        null
                ),
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(project);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_Dataset() {
        long id = 53;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.DATASET, id);
        Dataset dataset = new Dataset(
                new OmeroDataset(
                        null,
                        id,
                        null,
                        null,
                        8,
                        null
                ),
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(dataset);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_Image() {
        long id = 5;
        SimpleServerEntity expectedEntity = new SimpleServerEntity(EntityType.IMAGE, id);
        Image image = new Image(
                new OmeroImage(
                        null,
                        id,
                        null,
                        null,
                        new OmeroPixels(
                                null,
                                1,
                                2,
                                3,
                                4,
                                5,
                                null,
                                null,
                                null,
                                new OmeroImageType("image type")
                        ),
                        null
                ),
                URI.create("http://someUri.com")
        );

        SimpleServerEntity entity = new SimpleServerEntity(image);

        Assertions.assertEquals(expectedEntity, entity);
    }

    @Test
    void Check_Creation_From_New_Server_Entity() {
        NewServerEntity newServerEntity = new NewServerEntity();

        Assertions.assertThrows(IllegalArgumentException.class, () -> new SimpleServerEntity(newServerEntity));
    }

    @Test
    void Check_Creation_From_Json() {
        String json = """
                {
                  "paths": [
                    [
                      {
                        "type": "experimenter",
                        "id": 208
                      },
                      {
                        "type": "dataset",
                        "id": 266,
                        "childCount": 12
                      },
                      {
                        "type": "image",
                        "id": 5628
                      }
                    ]
                  ]
                }
                """;
        List<SimpleServerEntity> expectedEntities = List.of(
                new SimpleServerEntity(EntityType.DATASET, 266),
                new SimpleServerEntity(EntityType.IMAGE, 5628)
        );

        List<SimpleServerEntity> entities = SimpleServerEntity.createFromJson(new Gson().fromJson(json, JsonElement.class));

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedEntities, entities);
    }

    private static class NewServerEntity extends ServerEntity {

        protected NewServerEntity() {
            super(0, null, null, null, URI.create("http://some_uri.com"));
        }

        @Override
        public List<Attribute> getAttributes() {
            return List.of();
        }

        @Override
        public boolean hasChildren() {
            return false;
        }

        @Override
        public CompletableFuture<? extends List<? extends RepositoryEntity>> getChildren(long ownerId, long groupId) {
            return null;
        }

        @Override
        public String getLabel() {
            return "";
        }
    }
}
