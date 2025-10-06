package qupath.ext.omero.core.entities.annotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.core.apis.webclient.annotations.CommentAnnotation;

import java.util.Optional;

public class TestCommentAnnotation {

    @Test
    void Check_Comment_Value() {
        CommentAnnotation commentAnnotation = createCommentAnnotation("""
                {
                    "textValue": "Some comment"
                }
                """);

        String comment = commentAnnotation.getValue().orElse("");

        Assertions.assertEquals("Some comment", comment);
    }

    @Test
    void Check_Comment_Missing() {
        CommentAnnotation commentAnnotation = createCommentAnnotation("{}");

        Optional<String> comment = commentAnnotation.getValue();

        Assertions.assertTrue(comment.isEmpty());
    }

    private CommentAnnotation createCommentAnnotation(String json) {
        return new Gson().fromJson(json, CommentAnnotation.class);
    }
}
