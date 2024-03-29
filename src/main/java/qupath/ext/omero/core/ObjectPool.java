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
import java.util.function.Supplier;

/**
 * <p>
 *     A pool of object of fixed size. Objects are created on demand.
 * </p>
 * <p>
 *     Once no longer used, this pool must be {@link #close() closed}.
 * </p>
 * <p>
 *     This class is thread-safe.
 * </p>
 *
 * @param <E>  the type of the object to store
 */
public class ObjectPool<E> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ObjectPool.class);
    private final ExecutorService objectCreationService = Executors.newCachedThreadPool();
    private final ArrayBlockingQueue<E> queue;
    private final int queueSize;
    private final Supplier<E> objectCreator;
    private int numberOfObjectsCreated = 0;
    private record ObjectWrapper<V>(Optional<V> object, boolean useThisObject) {}

    /**
     * Create the pool of objects. This will not create any object yet.
     *
     * @param size  the capacity of the pool
     * @param objectCreator  a function to create an object
     */
    public ObjectPool(int size, Supplier<E> objectCreator) {
        queue = new ArrayBlockingQueue<>(size);
        this.queueSize = size;
        this.objectCreator = objectCreator;
    }

    /**
     * Close this pool. If some objects are being created, this function will wait
     * for their creation to end.
     *
     * @throws Exception when an error occurs while waiting for the object creation to end
     */
    @Override
    public void close() throws Exception {
        objectCreationService.shutdown();
        objectCreationService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        //TODO: handle when objects are instances of AutoCloseable
    }

    /**
     * <p>
     *     Attempt to retrieve an object from this pool.
     *     <ul>
     *         <li>
     *             If an object is available in the pool, it will be directly returned.
     *         </li>
     *         <li>
     *             If no object is available in the pool and the pool capacity allows to create a new
     *             object, a new object is created and returned. If for some reason the object creation
     *             fails (or return null), an empty Optional is returned.
     *         </li>
     *         <li>
     *             If no object is available in the pool and the pool capacity doesn't allow creating
     *             a new object, this function blocks until an object becomes available in the pool.
     *         </li>
     *     </ul>
     * </p>
     * <p>
     *     The caller of this function will have a full control on the returned object. As soon as the
     *     object is not used anymore, it must be given back to this pool using the {@link #giveBack(Object)}
     *     function.
     * </p>
     *
     * @return an object from this pool, or an empty Optional if the creation failed
     * @throws InterruptedException  when creating an object or waiting for an object to become available is interrupted
     * @throws ExecutionException  when an error occurs while creating an object
     */
    public Optional<E> get() throws InterruptedException, ExecutionException {
        E object = queue.poll();

        if (object == null) {
            ObjectWrapper<E> objectWrapper = computeObjectIfPossible().get();

            if (objectWrapper.useThisObject()) {
                return objectWrapper.object();
            } else {
                return Optional.of(queue.take());
            }
        } else {
            return Optional.of(object);
        }
    }

    /**
     * Give an object back to this pool. This function must be used once an object
     * returned {@link #get()} is not used anymore.
     *
     * @param object  the object to give back. If it's null, nothing will happen
     */
    public void giveBack(E object) {
        if (object != null) {
            queue.offer(object);
        }
    }

    private synchronized CompletableFuture<ObjectWrapper<E>> computeObjectIfPossible() {
        if (numberOfObjectsCreated < queueSize) {
            numberOfObjectsCreated++;

            return CompletableFuture.supplyAsync(
                    () -> {
                        E object = null;

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
                        return new ObjectWrapper<>(Optional.ofNullable(objectCreator.get()), true);
                    },
                    objectCreationService
            );
        } else {
            return CompletableFuture.completedFuture(new ObjectWrapper<>(Optional.empty(), false));
        }
    }
}
