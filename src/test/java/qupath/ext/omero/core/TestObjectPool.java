package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class TestObjectPool {

    @Test
    void Check_Failed_Creation_Returns_Empty() throws InterruptedException {
        ObjectPool<Object1> pool = new ObjectPool<>(10, Object1::new, object -> {});

        Optional<Object1> object = pool.createObject();

        Assertions.assertTrue(object.isEmpty());
    }

    @Test
    void Check_Successful_Object_Creation() throws InterruptedException {
        ObjectPool<Object2> pool = new ObjectPool<>(10, Object2::new, object -> {});

        Optional<Object2> object = pool.createObject();

        Assertions.assertTrue(object.isPresent());
    }

    @Test
    void Check_Failed_Deletion() throws InterruptedException {
        ObjectPool<Object3> pool = new ObjectPool<>(10, Object3::new, Object3::close);
        Object3 object = pool.createObject().orElse(null);

        Assertions.assertDoesNotThrow(() -> pool.destroyObject(object));
    }

    @Test
    void Check_Objects_Closed() throws Exception {
        ObjectPool<Object4> pool = new ObjectPool<>(10, Object4::new, Object4::close);
        List<Object4> objects = new ArrayList<>();
        for (int i=0; i<5; i++) {
            objects.add(pool.createObject().orElse(null));
        }

        for (Object4 object : objects) {
            pool.destroyObject(object);
        }

        Assertions.assertTrue(Object4.allInstancesDeleted());
    }

    @Test
    void Check_Creation_Waits_For_Pool_Capacity() throws InterruptedException {
        int capacity = 10;
        AtomicInteger objectProcessedCounter = new AtomicInteger(0);
        ObjectPool<Object2> pool = new ObjectPool<>(capacity, Object2::new, object -> {});
        List<Object2> objects = IntStream.range(0, capacity)
                .mapToObj(i -> {
                    try {
                        return pool.createObject().orElse(null);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        ExecutorService executor = Executors.newFixedThreadPool(capacity);
        for (int i=0; i<capacity; i++) {
            int finalI = i;
            executor.execute(() -> {
                try {
                    Thread.sleep(500);
                    objectProcessedCounter.incrementAndGet();
                    pool.destroyObject(objects.get(finalI));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        pool.createObject();

        Assertions.assertTrue(objectProcessedCounter.get() > 0);    // at least one object processed

        executor.close();
    }

    private static class Object1 {
        public Object1() {
            throw new RuntimeException();
        }
    }
    private static class Object2 {}
    private static class Object3 {
        public void close() {
            throw new RuntimeException();
        }
    }
    private static class Object4 {
        private static int numberInstancesCreated = 0;
        private static int numberInstancesDeleted = 0;

        public Object4() {
            numberInstancesCreated++;
        }

        public void close() {
            numberInstancesDeleted++;
        }

        public static boolean allInstancesDeleted() {
            return numberInstancesCreated == numberInstancesDeleted;
        }
    }
}
