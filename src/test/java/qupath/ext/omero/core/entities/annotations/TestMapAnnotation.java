package qupath.ext.omero.core.entities.annotations;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;

import java.util.List;
import java.util.stream.Collectors;

public class TestMapAnnotation {

    @Test
    void Check_Pairs() {
        List<MapAnnotation.Pair> expectedPairs = List.of(
                new MapAnnotation.Pair("key1", "value1")
        );
        MapAnnotation mapAnnotation = createMapAnnotation(expectedPairs);

        List<MapAnnotation.Pair> pairs = mapAnnotation.getPairs();

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedPairs, pairs);
    }

    @Test
    void Check_Pairs_Missing() {
        MapAnnotation mapAnnotation = new Gson().fromJson("{}", MapAnnotation.class);

        List<MapAnnotation.Pair> pairs = mapAnnotation.getPairs();

        Assertions.assertTrue(pairs.isEmpty());
    }

    private MapAnnotation createMapAnnotation(List<MapAnnotation.Pair> pairs) {
        return new Gson().fromJson(
                String.format(
                    """
                    {
                        "values": [%s]
                    }
                    """,
                    pairs.stream()
                            .map(pair -> String.format("[\"%s\",\"%s\"]", pair.key(), pair.value()))
                            .collect(Collectors.joining(","))
                ),
                MapAnnotation.class
        );
    }
}
