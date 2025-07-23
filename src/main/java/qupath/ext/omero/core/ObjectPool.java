package qupath.ext.omero.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A pool of objects of fixed size. Objects are created on demand.
 * <p>
 * Once no longer used, this pool must be {@link #close() closed}.
 * <p>
 * This class is thread-safe.
 *
 * @param <T> the type of object to store
 */
public class ObjectPool<T> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ObjectPool.class);
    private final ExecutorService objectCreationService = Executors.newCachedThreadPool();
    private final ArrayBlockingQueue<T> queue;
    private final int queueSize;
    private final Supplier<T> objectCreator;
    private final Consumer<T> objectCloser;
    private int numberOfObjectsCreated = 0;
    private record ObjectWrapper<V>(Optional<V> object, boolean useThisObject) {}

    /**
     * Create the pool of objects. This will not create any object yet.
     *
     * @param size the capacity of the pool (greater than 0)
     * @param objectCreator a function to create an object. It is allowed to return null
     * @throws IllegalArgumentException when size is less than 1
     */
    public ObjectPool(int size, Supplier<T> objectCreator) {
        this(size, objectCreator, null);
    }

    /**
     * Create the pool of objects. This will not create any object yet.
     *
     * @param size the capacity of the pool (greater than 0)
     * @param objectCreator a function to create an object. It is allowed to return null
     * @param objectCloser a function that will be called on each object of this pool when it is closed
     * @throws IllegalArgumentException when size is less than 1
     */
    public ObjectPool(int size, Supplier<T> objectCreator, Consumer<T> objectCloser) {
        logger.debug("Creating object pool of size {}", size);

        this.queue = new ArrayBlockingQueue<>(size);
        this.queueSize = size;
        this.objectCreator = objectCreator;
        this.objectCloser = objectCloser;
    }

    /**
     * Close this pool. If some objects are being created, this function will wait
     * for their creation to end.
     * <p>
     * If defined, the objectCloser parameter of {@link #ObjectPool(int,Supplier,Consumer)} will be
     * called on each object currently present in the pool, but not on objects taken from the pool
     * and not given back yet.
     * <p>
     * This function can be called multiple times, but only the first call does something.
     *
     * @throws Exception when an error occurs while waiting for the object creation to end
     */
    @Override
    public void close() throws Exception {
        if (objectCreationService.isShutdown()) {
            logger.debug("Object pool already closed before. Not doing anything");
            return;
        }

        logger.debug("Closing object pool");

        objectCreationService.shutdown();
        objectCreationService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        if (objectCloser != null) {
            queue.forEach(objectCloser);
        }
    }

    /**
     * Attempt to retrieve an object from this pool.
     * <ul>
     *     <li>
     *         If an object is available in the pool, it will be directly returned.
     *     </li>
     *     <li>
     *         If no object is available in the pool and the pool capacity allows to create a new
     *         object, a new object is created and returned. If for some reason the object creation
     *         fails (or return null), an empty Optional is returned.
     *     </li>
     *     <li>
     *         If no object is available in the pool and the pool capacity doesn't allow creating
     *         a new object, this function blocks until an object becomes available in the pool.
     *     </li>
     * </ul>
     * <p>
     * The caller of this function will have a full control on the returned object. As soon as the
     * object is not used anymore, it must be given back to this pool using the {@link #giveBack(Object)}
     * function.
     *
     * @return an object from this pool, or an empty Optional if the creation failed
     * @throws InterruptedException  when creating an object or waiting for an object to become available is interrupted
     * @throws ExecutionException  when an error occurs while creating an object
     */
    public Optional<T> get() throws InterruptedException, ExecutionException {
        logger.trace("Getting object from pool");
        T object = queue.poll();

        if (object == null) {
            ObjectWrapper<T> objectWrapper = computeObjectIfPossible().get();

            if (objectWrapper.useThisObject()) {
                logger.trace("Object {} created. Returning it", objectWrapper.object());
                return objectWrapper.object();
            } else {
                logger.trace("Pool empty and the maximum number of objects have been created. Waiting for an object to become available");
                return Optional.of(queue.take());
            }
        } else {
            logger.trace("The pool was not empty, so the object {} was retrieved from the pool", object);
            return Optional.of(object);
        }
    }

    /**
     * Give an object back to this pool. This function must be used once an object
     * returned {@link #get()} is not used anymore.
     *
     * @param object the object to give back. Nothing will happen if the object is null
     */
    public void giveBack(T object) {
        if (object != null) {
            logger.trace("Object {} gave back to pool", object);
            queue.offer(object);
        }
    }

    private synchronized CompletableFuture<ObjectWrapper<T>> computeObjectIfPossible() {
        if (numberOfObjectsCreated < queueSize) {
            logger.trace("Pool empty but another object can be created. Doing that");
            numberOfObjectsCreated++;

            return CompletableFuture.supplyAsync(
                    () -> {
                        T object = null;

                        try {
                            object = objectCreator.get();
                        } catch (Exception e) {
                            logger.error("Error when creating object in pool", e);
                        }

                        if (object == null) {
                            synchronized (this) {
                                numberOfObjectsCreated--;
                            }
                        }
                        return new ObjectWrapper<>(Optional.ofNullable(object), true);
                    },
                    objectCreationService
            );
        } else {
            return CompletableFuture.completedFuture(new ObjectWrapper<>(Optional.empty(), false));
        }
    }
}
