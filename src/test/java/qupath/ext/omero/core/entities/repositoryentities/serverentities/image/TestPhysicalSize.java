package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class TestPhysicalSize {

    @Test
    void Check_Empty() {
        PhysicalSize physicalSize = new Gson().fromJson("{}", PhysicalSize.class);

        String symbol = physicalSize.symbol();

        Assertions.assertTrue(symbol.isEmpty());
    }

    @Test
    void Check_Symbol() {
        PhysicalSize physicalSize = createPhysicalSize();

        String symbol = physicalSize.symbol();

        Assertions.assertEquals("μm", symbol);
    }

    @Test
    void Check_Value() {
        PhysicalSize physicalSize = createPhysicalSize();

        double value = physicalSize.value();

        Assertions.assertEquals(45.63, value);
    }

    private PhysicalSize createPhysicalSize() {
        String json = """
                {
                    "Symbol": "μm",
                    "Value": 45.63
                }
                """;
        return new Gson().fromJson(json, PhysicalSize.class);
    }
}
