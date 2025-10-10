package qupath.ext.omero;

import org.opentest4j.AssertionFailedError;

import java.util.Collection;

/**
 * Adds some utility functions for testing.
 */
public class TestUtilities {

    private TestUtilities() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Assert that two lists are equal without taking the order
     * of elements into account.
     * This function doesn't work if some duplicates are present in one
     * of the list.
     *
     * @param expectedCollection the expected values
     * @param actualCollection the actual values
     * @param <T> the type of the elements of the collection
     */
    public static <T> void assertCollectionsEqualsWithoutOrder(Collection<T> expectedCollection, Collection<T> actualCollection) {
        if (expectedCollection.size() != actualCollection.size()) {
            throw new AssertionFailedError(String.format(
                    "Expected collection size: %d but was: %d",
                    expectedCollection.size(),
                    actualCollection.size())
            );
        }

        if (!expectedCollection.containsAll(actualCollection) || !actualCollection.containsAll(expectedCollection)) {
            throw new AssertionFailedError(String.format(
                    "Expected collection: %s but was: %s",
                    expectedCollection,
                    actualCollection
            ));
        }
    }
}
