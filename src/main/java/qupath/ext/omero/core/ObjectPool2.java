package qupath.ext.omero.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A pool of objects of fixed size that can create and destroy objects.
 * <p>
 * This class is thread-safe.
 *
 * @param <T> the type of object to store
 */
public class ObjectPool2<T> {

    private static final Logger logger = LoggerFactory.getLogger(ObjectPool2.class);
    private final Semaphore semaphore;
    private final Supplier<T> objectCreator;
    private final Consumer<T> objectCloser;

    /**
     * Create the pool of objects. This will not create any object yet.
     *
     * @param maxNumberOfElements the capacity of the pool (greater than 0)
     * @param objectCreator a function to create an object. It is allowed to return null
     * @param objectCloser a function that will be called on each object of this pool when it is closed
     * @throws IllegalArgumentException if the provided maximum number of elements is less than 1
     */
    public ObjectPool2(int maxNumberOfElements, Supplier<T> objectCreator, Consumer<T> objectCloser) {
        if (maxNumberOfElements < 1) {
            throw new IllegalArgumentException(String.format("The provided maximum number of elements %d is less than 1", maxNumberOfElements));
        }

        this.semaphore = new Semaphore(maxNumberOfElements);
        this.objectCreator = objectCreator;
        this.objectCloser = objectCloser;
    }

    public Optional<T> createObject() throws InterruptedException {
        semaphore.acquire();

        try {
            T object = objectCreator.get();

            if (object == null) {
                semaphore.release();
                return Optional.empty();
            } else {
                return Optional.of(object);
            }
        } catch (Exception e) {
            logger.error("Error when creating object in pool", e);
            semaphore.release();
            return Optional.empty();
        }
    }

    public void destroyObject(T object) {
        if (object == null) {
            return;
        }

        try {
            objectCloser.accept(object);
        } catch (Exception e) {
            logger.error("Error when closing {}", object, e);
        } finally {
            semaphore.release();
        }
    }
}
