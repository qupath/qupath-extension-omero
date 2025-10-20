package qupath.ext.omero.core.apis.iviewer.imageentities;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestOmeroWindow {

    @Test
    void Check_Created_From_Json() {
        OmeroWindow expectedOmeroWindow = new OmeroWindow(
                234.23423d,
                34d
        );

        OmeroWindow omeroWindow = new Gson().fromJson(
                """
                {
                    "start": 234.23423,
                    "end": 34
                }
                """,
                OmeroWindow.class
        );

        Assertions.assertEquals(expectedOmeroWindow, omeroWindow);
    }
}
