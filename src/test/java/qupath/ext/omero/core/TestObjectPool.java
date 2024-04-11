package qupath.ext.omero.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

public class TestObjectPool {

    @Test
    void Check_Only_One_Object_Created_When_Given_Back_Between_Get_Calls() throws Exception {
        ObjectPool<Object1> objectPool = new ObjectPool<>(10, Object1::new);
        Object1 object1;

        for (int i=0; i<5; i++) {
            object1 = objectPool.get().orElse(null);
            objectPool.giveBack(object1);
        }

        Assertions.assertEquals(1, Object1.creationCounter);

        objectPool.close();
    }

    @Test
    void Check_Failed_Creation_Returns_Empty() throws Exception {
        ObjectPool<Object2> objectPool = new ObjectPool<>(10, () -> {
            throw new IllegalArgumentException("Expected exception");
        });

        Optional<Object2> object2 = objectPool.get();

        Assertions.assertTrue(object2.isEmpty());

        objectPool.close();
    }

    @Test
    void Check_Objects_Closed() throws Exception {
        ObjectPool<Object3> objectPool = new ObjectPool<>(10, Object3::new, Object3::close);
        List<Object3> objects3 = new ArrayList<>();
        for (int i=0; i<5; i++) {
            objects3.add(objectPool.get().orElse(null));
        }
        for (Object3 object3: objects3) {
            objectPool.giveBack(object3);
        }

        objectPool.close();

        Assertions.assertTrue(Object3.allInstancesDeleted());
    }

    @Test
    void Check_Objects_Non_Given_Back_Not_Closed() throws Exception {
        ObjectPool<Object4> objectPool = new ObjectPool<>(10, Object4::new, Object4::close);
        for (int i=0; i<5; i++) {
            objectPool.get();
        }

        objectPool.close();

        Assertions.assertEquals(0, Object4.numberInstancesDeleted);
    }

    @Test
    void Check_Three_Objects_Created() throws Exception {
        int expectedNumberOfObjectsCreated = 3;
        ObjectPool<Object5> objectPool = new ObjectPool<>(10, Object5::new);
        List<Object5> objects5 = new ArrayList<>();
        for (int i=0; i<expectedNumberOfObjectsCreated; i++) {
            objects5.add(objectPool.get().orElse(null));
        }
        for (Object5 object5: objects5) {
            objectPool.giveBack(object5);
        }
        for (int i=0; i<10; i++) {
            Object5 object5 = objectPool.get().orElse(null);
            objectPool.giveBack(object5);
        }

        Assertions.assertEquals(expectedNumberOfObjectsCreated, Object5.creationCounter);

        objectPool.close();
    }

    @Test
    void Check_Wait_For_Object_Available() throws Exception {
        int size = 2;
        ObjectPool<Object6> objectPool = new ObjectPool<>(size, Object6::new);

        IntStream.range(0, 10)
                .parallel()
                .forEach(i -> {
                    try {
                        Object6 object6 = objectPool.get().orElse(null);
                        Thread.sleep(50);
                        objectPool.giveBack(object6);
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                });

        Assertions.assertTrue(Object6.creationCounter <= size);        // with some unlucky timing, fewer objects than size
                                                                                // could be created

        objectPool.close();
    }


    private static class Object1 {
        private static int creationCounter = 0;

        public Object1() {
            creationCounter++;
        }
    }
    private static class Object2 {}
    private static class Object3 {
        private static int numberInstancesCreated = 0;
        private static int numberInstancesDeleted = 0;

        public Object3() {
            numberInstancesCreated++;
        }

        public void close() {
            numberInstancesDeleted++;
        }

        public static boolean allInstancesDeleted() {
            return numberInstancesCreated == numberInstancesDeleted;
        }
    }
    private static class Object4 {
        private static int numberInstancesDeleted = 0;

        public void close() {
            numberInstancesDeleted++;
        }
    }
    private static class Object5 {
        private static int creationCounter = 0;

        public Object5() {
            creationCounter++;
        }
    }
    private static class Object6 {
        private static int creationCounter = 0;

        public Object6() {
            creationCounter++;
        }
    }
}
