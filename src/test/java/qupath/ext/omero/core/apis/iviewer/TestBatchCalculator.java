package qupath.ext.omero.core.apis.iviewer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

public class TestBatchCalculator {

    @Test
    void Check_Split_Empty_List() {
        List<Object> objects = List.of();
        long maxBatchSize = 10;
        Function<List<Object>, Long> batchSizeGetter = o -> 0L;

        Assertions.assertThrows(
                NoSuchElementException.class,
                () -> BatchCalculator.splitObjectsIntoBatches(objects, maxBatchSize, batchSizeGetter)
        );
    }

    @Test
    void Check_Split_Single_Element_List() {
        List<String> objects = List.of("a");
        long maxBatchSize = 4;
        Function<List<String>, Long> batchSizeGetter = strings -> (long) strings.stream().mapToInt(String::length).sum();
        List<List<String>> expectedBatches = List.of(
                List.of("a")
        );

        List<List<String>> batches = BatchCalculator.splitObjectsIntoBatches(objects, maxBatchSize, batchSizeGetter);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedBatches, batches);
    }

    @Test
    void Check_Split_Regular_List() {
        List<String> objects = List.of("a", "bb", "ccc", "dddd");
        long maxBatchSize = 4;
        Function<List<String>, Long> batchSizeGetter = strings -> (long) strings.stream().mapToInt(String::length).sum();
        List<List<String>> expectedBatches = List.of(
                List.of("a", "bb"),
                List.of("ccc"),
                List.of("dddd")
        );

        List<List<String>> batches = BatchCalculator.splitObjectsIntoBatches(objects, maxBatchSize, batchSizeGetter);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedBatches, batches);
    }

    @Test
    void Check_Split_With_Big_Elements() {
        List<String> objects = List.of("a", "bbbbbbb", "cc", "dd", "eeeeeeeeee", "f");
        long maxBatchSize = 5;
        Function<List<String>, Long> batchSizeGetter = strings -> (long) strings.stream().mapToInt(String::length).sum();
        List<List<String>> expectedBatches = List.of(
                List.of("a"),
                List.of("bbbbbbb"),
                List.of("cc", "dd"),
                List.of("eeeeeeeeee"),
                List.of("f")
        );

        List<List<String>> batches = BatchCalculator.splitObjectsIntoBatches(objects, maxBatchSize, batchSizeGetter);

        TestUtils.assertCollectionsEqualsWithoutOrder(expectedBatches, batches);
    }
}
