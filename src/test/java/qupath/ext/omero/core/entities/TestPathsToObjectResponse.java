package qupath.ext.omero.core.entities;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.PlateAcquisition;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.Screen;
import qupath.ext.omero.core.apis.json.repositoryentities.serverentities.ServerEntity;

import java.util.List;

public class TestPathsToObjectResponse {

    @Test
    void Check_Null_Response() {
        Assertions.assertThrows(NullPointerException.class, () -> PathsToObjectResponse.getServerEntitiesFromResponse(null));
    }

    @Test
    void Check_Empty_Response() {
        Assertions.assertThrows(NullPointerException.class, () -> PathsToObjectResponse.getServerEntitiesFromResponse(""));
    }

    @Test
    void Check_Empty_Json_Object() {
        Assertions.assertThrows(NullPointerException.class, () -> PathsToObjectResponse.getServerEntitiesFromResponse("{}"));
    }

    @Test
    void Check_Invalid_Field_Name_Json() {
        Assertions.assertThrows(NullPointerException.class, () -> PathsToObjectResponse.getServerEntitiesFromResponse("""
                {
                    "not_paths": {}
                }
                """));
    }

    @Test
    void Check_Invalid_Json() {
        Assertions.assertThrows(JsonSyntaxException.class, () -> PathsToObjectResponse.getServerEntitiesFromResponse("""
                {
                    "paths": {}
                }
                """));
    }

    @Test
    void Check_No_Entities() {
        String response = """
                {
                    "paths": [[]]
                }
                """;
        List<ServerEntity> expectedServerEntities = List.of();

        List<ServerEntity> serverEntities = PathsToObjectResponse.getServerEntitiesFromResponse(response);

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedServerEntities, serverEntities);
    }

    @Test
    void Check_Single_Paths() {
        String response = """
                {
                    "paths": [
                        [
                            {
                                "type": "project",
                                "id": 863
                            },
                            {
                                "type": "dataset",
                                "id": 567856
                            }
                        ]
                    ]
                }
                """;
        List<ServerEntity> expectedServerEntities = List.of(
                new Project(863),
                new Dataset(567856)
        );

        List<ServerEntity> serverEntities = PathsToObjectResponse.getServerEntitiesFromResponse(response);

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedServerEntities, serverEntities);
    }

    @Test
    void Check_Single_Paths_With_Unknown_Entity() {
        String response = """
                {
                    "paths": [
                        [
                            {
                                "type": "project",
                                "id": 863
                            },
                            {
                                "type": "unknown",
                                "id": 567856
                            }
                        ]
                    ]
                }
                """;
        List<ServerEntity> expectedServerEntities = List.of(
                new Project(863)
        );

        List<ServerEntity> serverEntities = PathsToObjectResponse.getServerEntitiesFromResponse(response);

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedServerEntities, serverEntities);
    }

    @Test
    void Check_Single_Paths_With_Invalid_Entity() {
        String response = """
                {
                    "paths": [
                        [
                            {
                                "invalid": "invalid"
                            },
                            {
                                "type": "project",
                                "id": 863
                            }
                        ]
                    ]
                }
                """;
        List<ServerEntity> expectedServerEntities = List.of(
                new Project(863)
        );

        List<ServerEntity> serverEntities = PathsToObjectResponse.getServerEntitiesFromResponse(response);

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedServerEntities, serverEntities);
    }

    @Test
    void Check_Multiple_Paths() {
        String response = """
                {
                    "paths": [
                        [
                            {
                                "type": "project",
                                "id": 863
                            },
                            {
                                "type": "dataset",
                                "id": 567856
                            }
                        ],
                        [
                            {
                                "type": "screen",
                                "id": 324
                            },
                            {
                                "type": "acquisition",
                                "id": 33333
                            }
                        ]
                    ]
                }
                """;
        List<ServerEntity> expectedServerEntities = List.of(
                new Project(863),
                new Dataset(567856),
                new Screen(324),
                new PlateAcquisition(33333)
        );

        List<ServerEntity> serverEntities = PathsToObjectResponse.getServerEntitiesFromResponse(response);

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedServerEntities, serverEntities);
    }
}
