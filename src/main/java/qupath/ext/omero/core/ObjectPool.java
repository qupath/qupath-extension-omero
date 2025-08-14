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
 * This class should be used when only a fixed amount of instances of a class are allowed to exist at the
 * same time.
 * <p>
 * This class is thread-safe.
 *
 * @param <T> the type of object to store
 */
public class ObjectPool<T> {

    private static final Logger logger = LoggerFactory.getLogger(ObjectPool.class);
    private final Semaphore semaphore;
    private final Supplier<T> objectCreator;
    private final Consumer<T> objectCloser;

    /**
     * Create the pool of objects. This will not create any object yet.
     *
     * @param maxNumberOfElements the capacity of the pool (greater than 0). There will never be more existing objects than this capacity
     * @param objectCreator a function to create an object. It is allowed to return null
     * @param objectCloser a function to delete an object
     * @throws IllegalArgumentException if the provided maximum number of elements is less than 1
     */
    public ObjectPool(int maxNumberOfElements, Supplier<T> objectCreator, Consumer<T> objectCloser) {
        if (maxNumberOfElements < 1) {
            throw new IllegalArgumentException(String.format("The provided maximum number of elements %d is less than 1", maxNumberOfElements));
        }

        this.semaphore = new Semaphore(maxNumberOfElements);
        this.objectCreator = objectCreator;
        this.objectCloser = objectCloser;
    }

    /**
     * Attempt to create an object of this pool.
     * <p>
     * If the pool capacity doesn't allow an object to be created, this function blocks until the pool capacity changes. Then, a new object
     * is created and returned. If for some reason the object creation fails (or return null), an empty Optional is returned (and the possible
     * exception is logged).
     * <p>
     * If this function returns a non-null object, this pool's capacity will be reduced. Use the {@link #destroyObject(Object)} function
     * to increase back this pool's capacity.
     *
     * @return an object from this pool, or an empty Optional if the creation failed
     * @throws InterruptedException  if waiting for the pool capacity to increase is interrupted
     */
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

    /**
     * Delete an object and increase this pool's capacity. This function must be used once an object
     * returned by {@link #createObject()} is not used anymore.
     * <p>
     * If an exception is thrown while closing the object, it will be logged but not propagated.
     *
     * @param object the object to give back. Nothing will happen if the object is null
     */
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
