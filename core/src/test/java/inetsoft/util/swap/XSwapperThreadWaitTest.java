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
package inetsoft.util.swap;

import inetsoft.test.*;
import inetsoft.util.GroupedThread;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77682, a swapper thread that had a swappable on its first loop started with an untimed
 * wait(0) and didn't sweep until swapLock was notified, and swapLock was an interned string
 * literal shared by every swapper in the JVM.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XSwapperThreadWaitTest {
   @Test
   void threadRegisteredBeforeFirstLoopSweepsWithoutNotify() throws Exception {
      XSwapper swapper = new XSwapper();
      setState(swapper, XSwapper.BAD_MEM);
      CountingSwappable swappable = new CountingSwappable();
      GroupedThread thread = createSweepThread(swapper, swappable);
      thread.start();

      try {
         long end = System.currentTimeMillis() + 10000;

         // swapLock is never notified, the first wait must time out on its own
         while(swappable.scans.get() == 0 && System.currentTimeMillis() < end) {
            Thread.sleep(100);
         }

         assertTrue(swappable.scans.get() > 0,
                    "swapper thread didn't sweep, state " + thread.getState());
      }
      finally {
         thread.cancel();
         thread.join(15000L);
         swapper.stop();
      }
   }

   @Test
   void goodStateWakeupIsFollowedByTimedWait() throws Exception {
      XSwapper swapper = new XSwapper();
      setState(swapper, XSwapper.GOOD_MEM);
      CountingSwappable swappable = new CountingSwappable();
      GroupedThread thread = createSweepThread(swapper, swappable);
      // make the first wakeup at good run the weak reference cleanup, which scans the swappable
      Field lcheck = thread.getClass().getDeclaredField("lcheck");
      lcheck.setAccessible(true);
      lcheck.setLong(thread, 0L);
      thread.start();
      Object swapLock = getSwapLock(swapper);

      try {
         long end = System.currentTimeMillis() + 10000;

         while(swappable.scans.get() == 0 && System.currentTimeMillis() < end) {
            synchronized(swapLock) {
               swapLock.notifyAll();
            }

            Thread.sleep(100);
         }

         assertTrue(swappable.scans.get() > 0, "swapper thread didn't wake up at good");
         Thread.State state = awaitWaiting(thread);
         assertEquals(Thread.State.TIMED_WAITING, state,
                      "swapper thread went back to an untimed wait at good");
      }
      finally {
         thread.cancel();
         thread.join(15000L);
         swapper.stop();
      }
   }

   @Test
   void swappersHaveTheirOwnLock() throws Exception {
      XSwapper swapper1 = new XSwapper();
      XSwapper swapper2 = new XSwapper();

      try {
         assertNotSame(getSwapLock(swapper1), getSwapLock(swapper2),
                       "swappers share one swapLock");
      }
      finally {
         swapper1.stop();
         swapper2.stop();
      }
   }

   private static Thread.State awaitWaiting(Thread thread) throws Exception {
      long end = System.currentTimeMillis() + 5000;
      Thread.State state = thread.getState();

      while(state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING &&
            System.currentTimeMillis() < end)
      {
         Thread.sleep(10);
         state = thread.getState();
      }

      return state;
   }

   /**
    * Make this swapper, and only this one, see the memory state.
    */
   private static void setState(XSwapper swapper, int memState) throws Exception {
      Field state = XSwapper.class.getDeclaredField("cachedState");
      state.setAccessible(true);
      state.setInt(swapper, memState);
      Field ts = XSwapper.class.getDeclaredField("stateTS");
      ts.setAccessible(true);
      ts.setLong(swapper, Long.MAX_VALUE);
   }

   private static Object getSwapLock(XSwapper swapper) throws Exception {
      Field field = XSwapper.class.getDeclaredField("swapLock");
      field.setAccessible(true);
      return field.get(swapper);
   }

   /**
    * Create a swapper thread with the swappable registered before its first loop.
    */
   private static GroupedThread createSweepThread(XSwapper swapper, XSwappable swappable)
      throws Exception
   {
      Class<?> threadClass = Class.forName(XSwapper.class.getName() + "$XSwapperThread");
      List<Constructor<?>> constructors = Arrays.stream(threadClass.getDeclaredConstructors())
         .filter(c -> c.getParameterCount() > 0 && c.getParameterTypes()[0] == XSwapper.class)
         .toList();
      assertEquals(1, constructors.size(),
                   "expected one XSwapperThread constructor taking the swapper, found " + constructors);
      Constructor<?> constructor = constructors.get(0);
      constructor.setAccessible(true);
      Object[] args = new Object[constructor.getParameterCount()];
      args[0] = swapper;
      GroupedThread thread = (GroupedThread) constructor.newInstance(args);
      Method register = threadClass.getDeclaredMethod("register", XSwappable.class);
      register.setAccessible(true);
      register.invoke(thread, swappable);
      return thread;
   }

   /**
    * A swappable that counts the sweeps that look at it and is never swapped.
    */
   private static final class CountingSwappable extends XSwappable {
      @Override
      public double getSwapPriority() {
         return 0;
      }

      @Override
      public boolean isCompleted() {
         return true;
      }

      @Override
      public boolean isSwappable() {
         scans.incrementAndGet();
         return true;
      }

      @Override
      public boolean isValid() {
         return true;
      }

      @Override
      public boolean swap() {
         return false;
      }

      @Override
      public void dispose() {
      }

      private final AtomicInteger scans = new AtomicInteger();
   }
}
