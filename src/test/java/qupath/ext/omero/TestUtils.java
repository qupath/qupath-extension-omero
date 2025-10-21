package qupath.ext.omero;

import org.junit.jupiter.api.Assertions;
import org.opentest4j.AssertionFailedError;

import java.awt.image.BufferedImage;
import java.util.Collection;

/**
 * Adds some utility functions for testing.
 */
public class TestUtils {

    private TestUtils() {
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

    /**
     * Assert that two images are equal. This means same width, height, number of bands, and pixel values.
     *
     * @param expectedImage the expected image
     * @param actualImage the actual image
     */
    public static void assertDoubleBufferedImagesEqual(BufferedImage expectedImage, BufferedImage actualImage) {
        Assertions.assertEquals(expectedImage.getWidth(), actualImage.getWidth());
        Assertions.assertEquals(expectedImage.getHeight(), actualImage.getHeight());

        double[] expectedPixels = new double[expectedImage.getSampleModel().getNumBands()];
        double[] actualPixels = new double[actualImage.getSampleModel().getNumBands()];
        for (int x = 0; x < expectedImage.getWidth(); x++) {
            for (int y = 0; y < expectedImage.getHeight(); y++) {
                Assertions.assertArrayEquals(
                        expectedImage.getRaster().getPixel(x, y, expectedPixels),
                        expectedImage.getRaster().getPixel(x, y, actualPixels)
                );
            }
        }
    }
}
