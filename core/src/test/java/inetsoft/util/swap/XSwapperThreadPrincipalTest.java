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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77649: the swapper threads are JVM-wide, so they must not run as the principal of the
 * thread that happened to create the swapper.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XSwapperThreadPrincipalTest {
   @BeforeEach
   void setUp() {
      SreeEnv.setProperty(KEY, "global");
      SreeEnv.setProperty("inetsoft.org.orga." + KEY, "orgvalue");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.remove(KEY);
      SreeEnv.remove("inetsoft.org.orga." + KEY);
   }

   @Test
   void swapperThreadHasNoPrincipalOfCreator() throws Exception {
      XSwapper swapper = createSwapper(new XPrincipal(new IdentityID("alice", "orga")));

      try {
         RecordingSwappable swappable = new RecordingSwappable();
         setCritical(swapper);
         swapper.register(swappable);

         assertTrue(awaitSwap(swapper, swappable.swapped), "swappable was not swapped");
         assertEquals(XSwapper.class.getName() + "$XSwapperThread",
                      swappable.thread.get().getClass().getName(), "not swapped by a swapper thread");
         assertNull(swappable.principal.get(), "swapper thread runs as the creator");
         assertNull(swappable.user.get(), "swapper thread logs as the creator");
         assertEquals("global", swappable.property.get(),
                      "swapper thread reads the creator organization's property");
      }
      finally {
         swapper.stop();
      }
   }

   /**
    * Create a swapper on a thread with the principal, as when the bean is created lazily on a
    * request or task thread.
    */
   static XSwapper createSwapper(Principal principal) throws Exception {
      AtomicReference<XSwapper> result = new AtomicReference<>();
      Thread creator = new Thread(() -> {
         ThreadContext.setContextPrincipal(principal);

         try {
            result.set(new XSwapper());
         }
         finally {
            ThreadContext.setContextPrincipal(null);
            OrganizationContextHolder.clear();
         }
      });

      creator.start();
      creator.join(30000L);
      assertNotNull(result.get(), "swapper was not created");
      return result.get();
   }

   /**
    * Wait for the swap. Notify the swapper threads so they sweep before their timed wait ends.
    */
   static boolean awaitSwap(XSwapper swapper, CountDownLatch swapped) throws Exception {
      Field field = XSwapper.class.getDeclaredField("swapLock");
      field.setAccessible(true);
      Object swapLock = field.get(swapper);

      for(int i = 0; i < 60; i++) {
         synchronized(swapLock) {
            swapLock.notifyAll();
         }

         if(swapped.await(500, TimeUnit.MILLISECONDS)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Make this swapper, and only this one, see the memory as critical.
    */
   static void setCritical(XSwapper swapper) throws Exception {
      Field state = XSwapper.class.getDeclaredField("cachedState");
      state.setAccessible(true);
      state.setInt(swapper, XSwapper.CRITICAL_MEM);
      Field ts = XSwapper.class.getDeclaredField("stateTS");
      ts.setAccessible(true);
      ts.setLong(swapper, Long.MAX_VALUE);
   }

   private static final class RecordingSwappable extends XSwappable {
      @Override
      public double getSwapPriority() {
         return valid ? 100 : 0;
      }

      @Override
      public boolean isCompleted() {
         return true;
      }

      @Override
      public boolean isSwappable() {
         return valid;
      }

      @Override
      public boolean isValid() {
         return valid;
      }

      @Override
      public synchronized boolean swap() {
         if(!valid) {
            return false;
         }

         thread.set(Thread.currentThread());
         principal.set(ThreadContext.getContextPrincipal());
         user.set(MDC.get("USER"));
         property.set(SreeEnv.getProperty(KEY));
         valid = false;
         swapped.countDown();
         return true;
      }

      @Override
      public void dispose() {
         valid = false;
      }

      private volatile boolean valid = true;
      private final AtomicReference<Thread> thread = new AtomicReference<>();
      private final AtomicReference<Principal> principal = new AtomicReference<>();
      private final AtomicReference<String> user = new AtomicReference<>();
      private final AtomicReference<String> property = new AtomicReference<>();
      private final CountDownLatch swapped = new CountDownLatch(1);
   }

   private static final String KEY = "test77649.key";
}
