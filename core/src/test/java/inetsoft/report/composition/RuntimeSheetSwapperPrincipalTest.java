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
package inetsoft.report.composition;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.GroupedThread;
import inetsoft.util.ThreadContext;
import inetsoft.util.swap.XSwappable;
import inetsoft.util.swap.XSwapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77649: the swapper threads have no principal. A sheet swapped on one of them must still
 * be written as the sheet's own user and organization (Bug #72493), and must leave the swapper
 * thread without a principal afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RuntimeSheetSwapperPrincipalTest {
   @Test
   void sheetIsWrittenAsItsOwnPrincipal() throws Exception {
      XPrincipal owner = new XPrincipal(new IdentityID("admin", "orgb"));
      RecordingWorksheet sheet = swapOnSwapperThread(owner);

      assertSame(owner, sheet.principal.get(), "sheet was not written as its own principal");
      assertEquals("orgb", sheet.orgID.get());
   }

   @Test
   void sheetWithoutPrincipalIsWrittenInDefaultOrganization() throws Exception {
      RecordingWorksheet sheet = swapOnSwapperThread(null);

      assertNull(sheet.principal.get(), "sheet was written as the swapper creator");
      assertEquals(Organization.getDefaultOrganizationID(), sheet.orgID.get());
   }

   private static RecordingWorksheet swapOnSwapperThread(XPrincipal owner) throws Exception {
      XSwapper swapper = createSwapper(new XPrincipal(new IdentityID("alice", "orga")));

      try {
         RecordingWorksheet sheet = new RecordingWorksheet();
         RuntimeSheet.XSwappableSheet swappable = new RuntimeSheet.XSwappableSheet(sheet, owner);
         setSwapper(swappable, swapper);
         setCritical(swapper);
         swappable.complete();

         assertTrue(awaitSwap(swapper, sheet.written), "sheet was not swapped");

         // swap() is synchronized, so it has restored the thread once the lock is free
         synchronized(swappable) {
            assertNull(sheet.error.get(), "writing the sheet failed: " + sheet.error.get());
            Thread thread = sheet.thread.get();
            assertEquals(XSwapper.class.getName() + "$XSwapperThread", thread.getClass().getName());
            assertNull(((GroupedThread) thread).getPrincipal(),
                       "swapper thread has a principal after the swap");
            assertFalse(swappable.isValid(), "sheet is still in memory");

            // the sheet is read back from the file the swapper thread wrote
            swappable.access();
            assertInstanceOf(RecordingWorksheet.class, swappable.get());
         }

         swappable.dispose();
         return sheet;
      }
      finally {
         swapper.stop();
      }
   }

   private static XSwapper createSwapper(Principal principal) throws Exception {
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

   // notify the swapper threads so they sweep before their timed wait ends
   private static boolean awaitSwap(XSwapper swapper, CountDownLatch swapped) throws Exception {
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

   // make this swapper, and only this one, see the memory as critical
   private static void setCritical(XSwapper swapper) throws Exception {
      Field state = XSwapper.class.getDeclaredField("cachedState");
      state.setAccessible(true);
      state.setInt(swapper, XSwapper.CRITICAL_MEM);
      Field ts = XSwapper.class.getDeclaredField("stateTS");
      ts.setAccessible(true);
      ts.setLong(swapper, Long.MAX_VALUE);
   }

   private static void setSwapper(XSwappable swappable, XSwapper swapper) throws Exception {
      Field field = XSwappable.class.getDeclaredField("swapper");
      field.setAccessible(true);
      field.set(swappable, swapper);
   }

   public static class RecordingWorksheet extends Worksheet {
      public RecordingWorksheet() {
         super();
      }

      @Override
      public void writeXML(PrintWriter writer) {
         if(thread.get() == null) {
            thread.set(Thread.currentThread());
            principal.set(ThreadContext.getContextPrincipal());

            try {
               orgID.set(OrganizationManager.getInstance().getCurrentOrgID());
            }
            catch(Throwable ex) {
               error.set(ex);
            }

            written.countDown();
         }

         super.writeXML(writer);
      }

      private final transient AtomicReference<Thread> thread = new AtomicReference<>();
      private final transient AtomicReference<Principal> principal = new AtomicReference<>();
      private final transient AtomicReference<String> orgID = new AtomicReference<>();
      private final transient AtomicReference<Throwable> error = new AtomicReference<>();
      private final transient CountDownLatch written = new CountDownLatch(1);
   }
}
