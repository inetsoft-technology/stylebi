/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util.stall;

import inetsoft.util.GroupedThread;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78453: {@link GroupedThread#getStackTrace()} of another thread appends the stack that
 * created the thread after its live frames, with no frame marking where they end.
 * {@link StallTestSupport#isIn} must read only the live frames, and match a class and its
 * nested classes only.
 */
@Tag("core")
class StallTestSupportLiveStackTest {
   @Test
   void idleThreadCreatedByClassIsNotInClass() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Thread thread = Creator.create(() -> awaitQuietly(release));

      try {
         thread.start();
         awaitWaiting(thread);
         assertTrue(Arrays.stream(thread.getStackTrace())
                       .anyMatch(e -> e.getClassName().equals(Creator.class.getName())),
                    "the appended creation stack holds the creator");
         assertFalse(isAnyLiveFrameOf(thread, Creator.class));
         assertFalse(StallTestSupport.isIn(thread, Creator.class),
                     "an idle thread is not in the class that created it");
      }
      finally {
         release.countDown();
         thread.join(10_000);
      }
   }

   @Test
   void threadRunningClassIsInClassAndItsNestedClasses() throws Exception {
      assertRunningIn(release -> new GroupedThread(() -> Target.await(release), "target"));
      // lambda body of the class: frame class is the class itself
      assertRunningIn(release -> new GroupedThread(Target.lambda(release), "target-lambda"));
      // anonymous class nested in the class: Target$1
      assertRunningIn(release -> new GroupedThread(Target.anonymous(release), "target-anon"));
      // named nested class: Target$Inner
      assertRunningIn(release -> new GroupedThread(new Target.Inner(release), "target-inner"));
   }

   @Test
   void similarlyNamedClassIsNotMatched() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Thread thread = new GroupedThread(() -> TargetTest.await(release), "target-test");

      try {
         thread.start();
         awaitWaiting(thread);
         assertTrue(StallTestSupport.isIn(thread, TargetTest.class));
         assertFalse(StallTestSupport.isIn(thread, Target.class),
                     "TargetTest starts with the name of Target but is not Target");
      }
      finally {
         release.countDown();
         thread.join(10_000);
      }
   }

   @Test
   void endedThreadHasNoLiveStack() throws Exception {
      Thread thread = new GroupedThread(() -> { }, "ended");
      thread.start();
      thread.join(10_000);
      assertFalse(thread.isAlive());
      assertEquals(0, StallTestSupport.liveStack(thread).length);
      assertFalse(StallTestSupport.isIn(thread, StallTestSupportLiveStackTest.class));
   }

   @Test
   void currentThreadLiveStackHoldsCaller() {
      StackTraceElement[] stack = StallTestSupport.liveStack(Thread.currentThread());
      assertTrue(Arrays.stream(stack).anyMatch(
         e -> e.getMethodName().equals("currentThreadLiveStackHoldsCaller")));
      assertTrue(StallTestSupport.isIn(Thread.currentThread(), StallTestSupportLiveStackTest.class));
   }

   private static void assertRunningIn(java.util.function.Function<CountDownLatch, Thread> factory)
      throws Exception
   {
      CountDownLatch release = new CountDownLatch(1);
      Thread thread = factory.apply(release);

      try {
         thread.start();
         awaitWaiting(thread);
         assertTrue(StallTestSupport.isIn(thread, Target.class), thread.getName() + " " +
                    Arrays.toString(StallTestSupport.liveStack(thread)));
         assertFalse(StallTestSupport.isIn(thread, TargetTest.class), thread.getName());
      }
      finally {
         release.countDown();
         thread.join(10_000);
      }
   }

   private static boolean isAnyLiveFrameOf(Thread thread, Class<?> cls) {
      return Arrays.stream(StallTestSupport.liveStack(thread))
         .anyMatch(e -> e.getClassName().startsWith(cls.getName()));
   }

   private static void awaitWaiting(Thread thread) throws InterruptedException {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

      while(thread.getState() != Thread.State.WAITING) {
         assertTrue(System.nanoTime() < end, "thread waits: " + thread.getState());
         Thread.sleep(10);
      }
   }

   private static void awaitQuietly(CountDownLatch latch) {
      try {
         latch.await();
      }
      catch(InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
   }

   private static final class Creator {
      // the task is not code of the creator: only the appended creation stack holds it
      static Thread create(Runnable task) {
         return new GroupedThread(task, "created");
      }
   }

   private static final class Target {
      static void await(CountDownLatch release) {
         awaitQuietly(release);
      }

      static Runnable lambda(CountDownLatch release) {
         return () -> {
            awaitQuietly(release);
            release.getCount();
         };
      }

      static Runnable anonymous(CountDownLatch release) {
         return new Runnable() {
            @Override
            public void run() {
               awaitQuietly(release);
            }
         };
      }

      static final class Inner implements Runnable {
         Inner(CountDownLatch release) {
            this.release = release;
         }

         @Override
         public void run() {
            awaitQuietly(release);
         }

         private final CountDownLatch release;
      }
   }

   private static final class TargetTest {
      static void await(CountDownLatch release) {
         awaitQuietly(release);
      }
   }
}
