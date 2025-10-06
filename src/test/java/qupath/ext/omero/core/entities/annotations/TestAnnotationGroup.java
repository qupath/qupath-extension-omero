package qupath.ext.omero.core.entities.annotations;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.annotations.Annotation;
import qupath.ext.omero.core.apis.webclient.annotations.AnnotationGroup;
import qupath.ext.omero.core.apis.webclient.annotations.CommentAnnotation;

import java.util.List;
import java.util.Map;

public class TestAnnotationGroup {

    @Test
    void Check_Annotations_Not_Created_Because_Class_Missing() {
        Assertions.assertThrows(JsonParseException.class, () -> createAnnotationGroup("{}, {}"));
    }

    @Test
    void Check_Two_Annotations_Created() {
        AnnotationGroup annotationGroup = createAnnotationGroup("""
                {
                    "class": "comment"
                },
                {
                    "class": "file"
                }
                """);

        Map<Class<? extends Annotation>, List<Annotation>> annotations = annotationGroup.annotations();

        Assertions.assertEquals(2, annotations.values().stream().mapToInt(List::size).sum());
    }

    @Test
    void Check_Two_Comment_Annotations_Created() {
        AnnotationGroup annotationGroup = createAnnotationGroup("""
                {
                    "class": "comment"
                },
                {
                    "class": "comment"
                }
                """);

        Map<Class<? extends Annotation>, List<Annotation>> annotations = annotationGroup.annotations();

        Assertions.assertEquals(2, annotations.get(CommentAnnotation.class).size());
    }

    @Test
    void Check_Two_Comment_Annotations_Can_Be_Retrieved() {
        AnnotationGroup annotationGroup = createAnnotationGroup("""
                {
                    "class": "comment"
                },
                {
                    "class": "comment"
                }
                """);

        List<CommentAnnotation> annotations = annotationGroup.getAnnotationsOfClass(CommentAnnotation.class);

        Assertions.assertEquals(2, annotations.size());
    }

    private AnnotationGroup createAnnotationGroup(String annotations) {
        String json = String.format("""
                {
                    "annotations": [%s],
                    "experimenters": []
                }
                """, annotations);
        return new AnnotationGroup(JsonParser.parseString(json).getAsJsonObject());
    }
}
