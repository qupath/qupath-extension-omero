package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestToken {

    @Test
    void Check_Data_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Token(
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        Token expectedToken = new Token(
                "token"
        );

        Token token = new Gson().fromJson(
                """
                {
                    "data": "token"
                }
                """,
                Token.class
        );

        Assertions.assertEquals(expectedToken, token);
    }
}
