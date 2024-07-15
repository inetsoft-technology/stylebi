/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultDebouncerTest {
   @BeforeEach
   void setUp() {
      debouncer = new DefaultDebouncer<>();
   }

   @AfterEach
   void tearDown() throws Exception {
      if(debouncer != null) {
         debouncer.close();
      }
   }

   @Test
   @Timeout(10)
   void testDebounceReplacedRunnable() throws InterruptedException {
      String key = "key";
      AtomicInteger counter = new AtomicInteger(0);
      AtomicReference<String> ref = new AtomicReference<>(null);
      CountDownLatch latch = new CountDownLatch(1);
      Runnable r1 = new TestRunnable("A", counter, ref, latch);
      Runnable r2 = new TestRunnable("B", counter, ref, latch);
      debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, r1);
      debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, r2);
      latch.await();
      assertEquals(1, counter.get());
      assertEquals("B", ref.get());
   }

   @Test
   @Timeout(10)
   void testDebounceReducedRunnable() throws InterruptedException {
      String key = "key";
      AtomicInteger counter = new AtomicInteger(0);
      AtomicReference<String> ref = new AtomicReference<>(null);
      CountDownLatch latch = new CountDownLatch(1);
      Runnable r1 = new TestRunnable("A", counter, ref, latch);
      Runnable r2 = new TestRunnable("B", counter, ref, latch);
      debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, r1, this::reduce);
      debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, r2, this::reduce);
      latch.await();
      assertEquals(1, counter.get());
      assertEquals("AB", ref.get());
   }

   @Test
   @Timeout(10)
   void testDebounceReplacedCallable() throws ExecutionException, InterruptedException {
      String key = "key";
      Callable<String> c1 = new TestCallable("A");
      Callable<String> c2 = new TestCallable("B");
      Future<String> future1 = debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, c1);
      Future<String> future2 = debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, c2);
      assertSame(future1, future2);
      assertEquals("B", future1.get());
   }

   @Test
   @Timeout(10)
   void testDebounceReducedCallable() throws ExecutionException, InterruptedException {
      String key = "key";
      Callable<String> c1 = new TestCallable("A");
      Callable<String> c2 = new TestCallable("B");
      Future<String> future1 =
         debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, c1, this::reduce);
      Future<String> future2 =
         debouncer.debounce(key, 500L, TimeUnit.MILLISECONDS, c2, this::reduce);
      assertSame(future1, future2);
      assertEquals("AB", future1.get());
   }

   @Test
   @Timeout(10)
   void testDebounceDifferentKey() throws InterruptedException {
      String key1 = "key1";
      String key2 = "key2";
      AtomicInteger counter1 = new AtomicInteger(0);
      AtomicInteger counter2 = new AtomicInteger(0);
      AtomicReference<String> ref1 = new AtomicReference<>(null);
      AtomicReference<String> ref2 = new AtomicReference<>(null);
      CountDownLatch latch = new CountDownLatch(2);
      Runnable r1 = new TestRunnable("A", counter1, ref1, latch);
      Runnable r2 = new TestRunnable("B", counter2, ref2, latch);
      debouncer.debounce(key1, 500L, TimeUnit.MILLISECONDS, r1);
      debouncer.debounce(key2, 500L, TimeUnit.MILLISECONDS, r2);
      latch.await();
      assertEquals(1, counter1.get());
      assertEquals("A", ref1.get());
      assertEquals(1, counter2.get());
      assertEquals("B", ref2.get());
   }

   private Runnable reduce(Runnable r1, Runnable r2) {
      TestRunnable t1 = (TestRunnable) r1;
      TestRunnable t2 = (TestRunnable) r2;
      StringBuilder id = new StringBuilder();
      AtomicInteger counter = null;
      AtomicReference<String> ref = null;
      CountDownLatch latch = null;

      if(t1 != null) {
         id.append(t1.id);
         counter = t1.counter;
         ref = t1.ref;
         latch = t1.latch;
      }

      if(t2 != null) {
         id.append(t2.id);

         if(t1 == null) {
            counter = t2.counter;
            ref = t2.ref;
            latch = t2.latch;
         }
      }

      return new TestRunnable(id.toString(), counter, ref, latch);
   }

   private Callable<String> reduce(Callable<String> c1, Callable<String> c2) {
      try {
         StringBuilder id = new StringBuilder();

         if(c1 != null) {
            id.append(c1.call());
         }

         if(c2 != null) {
            id.append(c2.call());
         }

         return new TestCallable(id.toString());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to reduce callable", e);
      }
   }

   private Debouncer<String> debouncer;

   private static final class TestRunnable implements Runnable {
      private TestRunnable(String id, AtomicInteger counter, AtomicReference<String> ref,
                           CountDownLatch latch)
      {
         this.id = id;
         this.counter = counter;
         this.ref = ref;
         this.latch = latch;
      }

      @Override
      public void run() {
         counter.getAndIncrement();
         ref.set(id);
         latch.countDown();
      }

      private final String id;
      private final AtomicInteger counter;
      private final AtomicReference<String> ref;
      private final CountDownLatch latch;
   }

   private static final class TestCallable implements Callable<String> {
      private TestCallable(String id) {
         this.id = id;
      }

      @Override
      public String call() {
         return id;
      }

      private final String id;
   }
}