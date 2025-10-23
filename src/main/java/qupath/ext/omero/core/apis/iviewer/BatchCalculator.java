package qupath.ext.omero.core.apis.iviewer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A class to divide lists of objects into batches of certain size.
 */
class BatchCalculator {

    private static final Logger logger = LoggerFactory.getLogger(BatchCalculator.class);

    private BatchCalculator() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Split a list of objects into batches according to a maximal batch size.
     *
     * @param objects the objects to split into batches
     * @param maxBatchSize the exclusive maximal size each batch can have. Note that if the size of a single object is bigger than the batch size,
     *                     then the batch containing the object will be bigger than the maximal size
     * @param batchSizeGetter a function that computes the size of a batch
     * @return batches containing the provided objects
     * @param <T> the type of the objects
     * @throws NullPointerException if one the parameters is null
     * @throws java.util.NoSuchElementException if the provided list is empty
     */
    public static <T> List<List<T>> splitObjectsIntoBatches(List<T> objects, long maxBatchSize, Function<List<T>, Long> batchSizeGetter) {
        List<List<T>> batches = new ArrayList<>();

        createNewBatch(batches, objects.getFirst(), maxBatchSize, batchSizeGetter);

        for (int i=1; i<objects.size(); i++) {
            T object = objects.get(i);
            List<T> currentBatch = batches.getLast();

            currentBatch.add(object);
            long batchSize = batchSizeGetter.apply(currentBatch);

            if (batchSize >= maxBatchSize) {
                currentBatch.remove(object);
                createNewBatch(batches, object, maxBatchSize, batchSizeGetter);
            }
        }

        return batches;
    }

    private static <T> void createNewBatch(List<List<T>> batches, T objectToAdd, long maxBatchSize, Function<List<T>, Long> batchSizeGetter) {
        if (batchSizeGetter.apply(List.of(objectToAdd)) > maxBatchSize) {
            logger.warn(
                    "{} has a bigger size than the batch size {}. The size of the new batch will therefore exceed the maximal size",
                    objectToAdd,
                    maxBatchSize
            );
        }

        List<T> newBatch = new ArrayList<>();
        newBatch.add(objectToAdd);

        batches.add(newBatch);
    }
}
