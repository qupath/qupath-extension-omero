package qupath.ext.omero.core.apis.json.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestLinks {

    @Test
    void Check_Experimenters_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        null,
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        "plates_url",
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Groups_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        null,
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        "plates_url",
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Projects_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        null,
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        "plates_url",
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Datasets_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        null,
                        "images_url",
                        "screens_url",
                        "plates_url",
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Images_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        null,
                        "screens_url",
                        "plates_url",
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Screens_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        null,
                        "plates_url",
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Plates_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        null,
                        "token_url",
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Token_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        "plates_url",
                        null,
                        "servers_url",
                        "login_url"
                )
        );
    }

    @Test
    void Check_Servers_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        "plates_url",
                        "token_url",
                        null,
                        "login_url"
                )
        );
    }

    @Test
    void Check_Login_Required() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> new Links(
                        "experimenters_url",
                        "groups_url",
                        "projects_url",
                        "datasets_url",
                        "images_url",
                        "screens_url",
                        "plates_url",
                        "token_url",
                        "servers_url",
                        null
                )
        );
    }

    @Test
    void Check_Created_From_Json() {
        Links expectedLinks = new Links(
                "experimenters_url",
                "groups_url",
                "projects_url",
                "datasets_url",
                "images_url",
                "screens_url",
                "plates_url",
                "token_url",
                "servers_url",
                "login_url"
        );

        Links links = new Gson().fromJson(
                """
                {
                    "url:experimenters": "experimenters_url",
                    "url:experimentergroups": "groups_url",
                    "url:projects": "projects_url",
                    "url:datasets": "datasets_url",
                    "url:images": "images_url",
                    "url:screens": "screens_url",
                    "url:plates": "plates_url",
                    "url:token": "token_url",
                    "url:servers": "servers_url",
                    "url:login": "login_url"
                }
                """,
                Links.class
        );

        Assertions.assertEquals(expectedLinks, links);
    }
}
