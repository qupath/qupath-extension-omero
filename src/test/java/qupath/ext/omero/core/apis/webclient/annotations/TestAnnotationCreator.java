package qupath.ext.omero.core.apis.webclient.annotations;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroAnnotationExperimenter;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroCommentAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFile;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroFileAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroLink;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroMapAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroRatingAnnotation;
import qupath.ext.omero.core.apis.webclient.annotations.omeroannotations.OmeroTagAnnotation;

import java.util.List;

public class TestAnnotationCreator {

    @Test
    void Check_Comment_Annotation() {
        List<Annotation> expectedAnnotations = List.of(new CommentAnnotation(
                new OmeroCommentAnnotation(
                        398L,
                        "some namespace",
                        OmeroCommentAnnotation.TYPE,
                        new OmeroAnnotationExperimenter(43L),
                        new OmeroLink(new OmeroAnnotationExperimenter(763L)),
                        "some comment"
                ),
                List.of(
                        new OmeroSimpleExperimenter(43L, "first adder", "last adder"),
                        new OmeroSimpleExperimenter(763L, "first owner", "last owner")
                )
        ));
        String json = """
                {
                    "annotations": [
                        {
                            "id": 398,
                            "ns": "some namespace",
                            "class": "CommentAnnotationI",
                            "owner": {
                                "id": 43
                            },
                            "link": {
                                "owner": {
                                    "id": 763
                                }
                            },
                            "textValue": "some comment"
                        }
                    ],
                    "experimenters": [
                        {
                            "id": 43,
                            "firstName": "first adder",
                            "lastName": "last adder"
                        },
                        {
                            "id": 763,
                            "firstName": "first owner",
                            "lastName": "last owner"
                        }
                    ]
                }
                """;

        List<Annotation> annotations = AnnotationCreator.createAnnotations(new Gson().fromJson(json, JsonElement.class));

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedAnnotations, annotations);
    }

    @Test
    void Check_File_Annotation() {
        List<Annotation> expectedAnnotations = List.of(new FileAnnotation(
                new OmeroFileAnnotation(
                        398L,
                        "some namespace",
                        OmeroFileAnnotation.TYPE,
                        new OmeroAnnotationExperimenter(43L),
                        new OmeroLink(new OmeroAnnotationExperimenter(763L)),
                        new OmeroFile("name", "mimetype", 345L)
                ),
                List.of(
                        new OmeroSimpleExperimenter(43L, "first adder", "last adder"),
                        new OmeroSimpleExperimenter(763L, "first owner", "last owner")
                )
        ));
        String json = """
                {
                    "annotations": [
                        {
                            "id": 398,
                            "ns": "some namespace",
                            "class": "FileAnnotationI",
                            "owner": {
                                "id": 43
                            },
                            "link": {
                                "owner": {
                                    "id": 763
                                }
                            },
                            "file": {
                                "name": "name",
                                "mimetype": "mimetype",
                                "size": 345
                            }
                        }
                    ],
                    "experimenters": [
                        {
                            "id": 43,
                            "firstName": "first adder",
                            "lastName": "last adder"
                        },
                        {
                            "id": 763,
                            "firstName": "first owner",
                            "lastName": "last owner"
                        }
                    ]
                }
                """;

        List<Annotation> annotations = AnnotationCreator.createAnnotations(new Gson().fromJson(json, JsonElement.class));

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedAnnotations, annotations);
    }

    @Test
    void Check_Map_Annotation() {
        List<Annotation> expectedAnnotations = List.of(new MapAnnotation(
                new OmeroMapAnnotation(
                        398L,
                        "some namespace",
                        OmeroMapAnnotation.TYPE,
                        new OmeroAnnotationExperimenter(43L),
                        new OmeroLink(new OmeroAnnotationExperimenter(763L)),
                        List.of(
                                List.of("a", "b"),
                                List.of("c"),
                                List.of("d", "e")
                        )
                ),
                List.of(
                        new OmeroSimpleExperimenter(43L, "first adder", "last adder"),
                        new OmeroSimpleExperimenter(763L, "first owner", "last owner")
                )
        ));
        String json = """
                {
                    "annotations": [
                        {
                            "id": 398,
                            "ns": "some namespace",
                            "class": "MapAnnotationI",
                            "owner": {
                                "id": 43
                            },
                            "link": {
                                "owner": {
                                    "id": 763
                                }
                            },
                            "values": [
                                ["a", "b"],
                                ["c"],
                                ["d", "e"]
                            ]
                        }
                    ],
                    "experimenters": [
                        {
                            "id": 43,
                            "firstName": "first adder",
                            "lastName": "last adder"
                        },
                        {
                            "id": 763,
                            "firstName": "first owner",
                            "lastName": "last owner"
                        }
                    ]
                }
                """;

        List<Annotation> annotations = AnnotationCreator.createAnnotations(new Gson().fromJson(json, JsonElement.class));

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedAnnotations, annotations);
    }

    @Test
    void Check_Rating_Annotation() {
        List<Annotation> expectedAnnotations = List.of(new RatingAnnotation(
                new OmeroRatingAnnotation(
                        398L,
                        "some namespace",
                        OmeroRatingAnnotation.TYPE,
                        new OmeroAnnotationExperimenter(43L),
                        new OmeroLink(new OmeroAnnotationExperimenter(763L)),
                        (short) 45
                ),
                List.of(
                        new OmeroSimpleExperimenter(43L, "first adder", "last adder"),
                        new OmeroSimpleExperimenter(763L, "first owner", "last owner")
                )
        ));
        String json = """
                {
                    "annotations": [
                        {
                            "id": 398,
                            "ns": "some namespace",
                            "class": "LongAnnotationI",
                            "owner": {
                                "id": 43
                            },
                            "link": {
                                "owner": {
                                    "id": 763
                                }
                            },
                            "longValue": 45
                        }
                    ],
                    "experimenters": [
                        {
                            "id": 43,
                            "firstName": "first adder",
                            "lastName": "last adder"
                        },
                        {
                            "id": 763,
                            "firstName": "first owner",
                            "lastName": "last owner"
                        }
                    ]
                }
                """;

        List<Annotation> annotations = AnnotationCreator.createAnnotations(new Gson().fromJson(json, JsonElement.class));

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedAnnotations, annotations);
    }

    @Test
    void Check_Tag_Annotation() {
        List<Annotation> expectedAnnotations = List.of(new TagAnnotation(
                new OmeroTagAnnotation(
                        398L,
                        "some namespace",
                        OmeroTagAnnotation.TYPE,
                        new OmeroAnnotationExperimenter(43L),
                        new OmeroLink(new OmeroAnnotationExperimenter(763L)),
                        "tag"
                ),
                List.of(
                        new OmeroSimpleExperimenter(43L, "first adder", "last adder"),
                        new OmeroSimpleExperimenter(763L, "first owner", "last owner")
                )
        ));
        String json = """
                {
                    "annotations": [
                        {
                            "id": 398,
                            "ns": "some namespace",
                            "class": "TagAnnotationI",
                            "owner": {
                                "id": 43
                            },
                            "link": {
                                "owner": {
                                    "id": 763
                                }
                            },
                            "textValue": "tag"
                        }
                    ],
                    "experimenters": [
                        {
                            "id": 43,
                            "firstName": "first adder",
                            "lastName": "last adder"
                        },
                        {
                            "id": 763,
                            "firstName": "first owner",
                            "lastName": "last owner"
                        }
                    ]
                }
                """;

        List<Annotation> annotations = AnnotationCreator.createAnnotations(new Gson().fromJson(json, JsonElement.class));

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedAnnotations, annotations);
    }
}
