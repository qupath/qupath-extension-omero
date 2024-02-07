package qupath.ext.omero.core.entities.annotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class TestMapAnnotation {

    @Test
    void Check_Values() {
        Map<String, String> expectedValues = Map.of(
                "key1", "value1"
        );
        MapAnnotation mapAnnotation = createMapAnnotation(expectedValues);

        Map<String, String> values = mapAnnotation.getValues();

        Assertions.assertEquals(expectedValues, values);
    }

    @Test
    void Check_Values_Missing() {
        MapAnnotation mapAnnotation = new Gson().fromJson("{}", MapAnnotation.class);

        Map<String, String> values = mapAnnotation.getValues();

        Assertions.assertTrue(values.isEmpty());
    }

    @Test
    void Check_Combined_Values() {
        Map<String, String> expectedValues = Map.of(
                "key1", "value1",
                "key2", "value2"
        );
        List<MapAnnotation> annotations = List.of(
                createMapAnnotation(Map.of("key1", "value1")),
                createMapAnnotation(Map.of("key2", "value2"))
        );

        Map<String, String> values = MapAnnotation.getCombinedValues(annotations);

        Assertions.assertEquals(expectedValues, values);
    }

    private MapAnnotation createMapAnnotation(Map<String, String> values) {
        String json = String.format("""
                {
                    "values": %s
                }
                """, new Gson().toJson(values));
        return new Gson().fromJson(json, MapAnnotation.class);
    }
}
